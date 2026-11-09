package dev.preptrack.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
public class IdentityController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authenticationManager;
    private final AuthAttemptRateLimiter rateLimiter;
    private static final SecureRandom RANDOM = new SecureRandom();

    public IdentityController(JdbcTemplate jdbc, PasswordEncoder passwords, AuthenticationManager authenticationManager,
                              AuthAttemptRateLimiter rateLimiter) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(HttpServletRequest request) {
        var token = (org.springframework.security.web.csrf.CsrfToken) request.getAttribute("_csrf");
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/bootstrap")
    ResponseEntity<?> bootstrap(@Valid @RequestBody BootstrapRequest body,
                                @RequestHeader("X-PrepTrack-Bootstrap") String secret,
                                HttpServletRequest request) {
        requireAttempt("bootstrap", 5);
        String expected = System.getenv("PREPTRACK_INVITE_SECRET");
        if (expected == null || expected.length() < 24 || !constantTimeEquals(expected, secret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Integer count = jdbc.queryForObject("SELECT count(*) FROM user_account", Integer.class);
        if (count != null && count > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Owner account already initialized");
        String email = normalize(body.email());
        UUID id = jdbc.queryForObject("INSERT INTO user_account(email, password_hash, role) VALUES (?, ?, 'ADMIN') RETURNING id",
            UUID.class, email, passwords.encode(body.password()));
        jdbc.update("INSERT INTO user_preference(user_id) VALUES (?)", id);
        jdbc.update("INSERT INTO audit_event(actor_user_id, event_type) VALUES (?, 'OWNER_BOOTSTRAP')", id);
        login(email, body.password(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "email", email, "role", "ADMIN"));
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        String email = normalize(body.email());
        requireAttempt("login:" + email, 10);
        login(email, body.password(), request);
        return ResponseEntity.ok(profile(email));
    }

    @GetMapping("/me")
    Map<String, Object> me(Authentication authentication) { return profile(authentication.getName()); }

    @PostMapping("/preferences")
    Map<String, Object> preferences(Authentication authentication, @Valid @RequestBody PreferencesRequest body) {
        jdbc.update("UPDATE user_preference SET show_difficulty=?, timezone=?, theme=?, updated_at=now() " +
            "WHERE user_id=(SELECT id FROM user_account WHERE email=?)", body.showDifficulty(), body.timezone(), body.theme(), authentication.getName());
        return profile(authentication.getName());
    }

    @PostMapping("/invites/accept")
    ResponseEntity<?> acceptInvite(@Valid @RequestBody AcceptInviteRequest body) {
        String email = normalize(body.email());
        requireAttempt("invite:" + email, 10);
        // Argon2 hashes are salted, so verify each active invite hash with the password encoder.
        var invite = jdbc.query("SELECT id, role, token_hash FROM invite WHERE lower(email)=? AND accepted_at IS NULL AND expires_at>now()",
            (rs, n) -> new InviteRow(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("token_hash")), email);
        var match = invite.stream().filter(i -> passwords.matches(body.token(), i.tokenHash())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite is invalid or expired"));
        if (jdbc.queryForObject("SELECT count(*) FROM user_account WHERE email=?", Integer.class, email) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already exists");
        jdbc.update("INSERT INTO user_account(email,password_hash,role) VALUES (?,?,?)", email, passwords.encode(body.password()), match.role());
        jdbc.update("INSERT INTO user_preference(user_id) SELECT id FROM user_account WHERE email=?", email);
        jdbc.update("UPDATE invite SET accepted_at=now() WHERE id=?", match.id());
        jdbc.update("INSERT INTO audit_event(event_type,safe_metadata) VALUES ('INVITE_ACCEPTED', jsonb_build_object('email', ?))", email);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("accepted", true));
    }

    @PostMapping("/admin/invites")
    ResponseEntity<?> createInvite(Authentication authentication, @Valid @RequestBody CreateInviteRequest body) {
        String email = normalize(body.email());
        if (jdbc.queryForObject("SELECT count(*) FROM user_account WHERE email=?", Integer.class, email) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already exists");
        String token = newToken();
        UUID id = jdbc.queryForObject("INSERT INTO invite(email,role,token_hash,expires_at,created_by) " +
            "SELECT ?, 'USER', ?, ?, id FROM user_account WHERE email=? RETURNING id", UUID.class,
            email, passwords.encode(token), Instant.now().plus(7, ChronoUnit.DAYS), authentication.getName());
        jdbc.update("INSERT INTO audit_event(actor_user_id,event_type,safe_metadata) " +
            "SELECT id,'INVITE_CREATED',jsonb_build_object('email',?) FROM user_account WHERE email=?", email, authentication.getName());
        // The raw token is returned once so the owner can share it through a channel they control.
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("inviteId", id, "email", email, "token", token,
            "expiresAt", Instant.now().plus(7, ChronoUnit.DAYS)));
    }

    private void login(String email, String rawPassword, HttpServletRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        jdbc.update("INSERT INTO audit_event(actor_user_id, event_type) SELECT id, 'LOGIN' FROM user_account WHERE email = ?", email);
    }

    private Map<String, Object> profile(String email) {
        return jdbc.queryForMap("SELECT u.id, u.email, u.role, p.show_difficulty AS \"showDifficulty\", p.timezone, p.theme " +
            "FROM user_account u JOIN user_preference p ON p.user_id=u.id WHERE u.email=? AND u.status='ACTIVE'", email);
    }

    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private void requireAttempt(String key, int limit) {
        if (!rateLimiter.allow(key, limit)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again in 15 minutes.");
        }
    }
    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8), b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String newToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }

    record BootstrapRequest(@Email @NotBlank @Size(max=254) String email, @NotBlank @Size(min=12, max=128) String password) {}
    record LoginRequest(@Email @NotBlank @Size(max=254) String email, @NotBlank @Size(max=128) String password) {}
    record PreferencesRequest(boolean showDifficulty, @NotBlank @Size(max=64) String timezone,
                              @NotBlank @jakarta.validation.constraints.Pattern(regexp="dark|light|system") String theme) {}
    record AcceptInviteRequest(@Email @NotBlank @Size(max=254) String email, @NotBlank String token,
                              @NotBlank @Size(min=12, max=128) String password) {}
    record CreateInviteRequest(@Email @NotBlank @Size(max=254) String email) {}
    record InviteRow(UUID id, String role, String tokenHash) {
        InviteRow(UUID id, String role) { this(id, role, ""); }
    }
}

package dev.preptrack.learning;

import tools.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/design")
public class DesignController {
    private static final Set<String> SYSTEM_DESIGN_FIELDS = Set.of(
        "requirements", "estimates", "api", "components", "flows", "bottlenecks", "tradeoffs", "retrospective");
    private static final Set<String> LLD_FIELDS = Set.of(
        "requirements", "domain", "relationships", "patterns", "edgeCases", "diagramUrl", "tradeoffs", "retrospective");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public DesignController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping("/library")
    Object library(Authentication auth, @RequestParam String track) {
        String normalizedTrack = normalizeTrack(track);
        return jdbc.queryForList("""
            SELECT ci.id, ci.track, ci.kind, ci.display_title AS title, ci.canonical_url AS url,
                   si.raw_metadata_json->>'category' AS category,
                   si.raw_metadata_json->>'summary' AS summary,
                   si.raw_metadata_json->>'prompt' AS prompt,
                   (si.raw_metadata_json->>'estimatedMinutes')::integer AS "estimatedMinutes",
                   src.slug AS "sourceSlug", src.canonical_url AS "sourceUrl",
                   coalesce((SELECT json_agg(t.name ORDER BY t.name)::text
                             FROM content_topic ct JOIN topic t ON t.id=ct.topic_id
                             WHERE ct.content_item_id=ci.id), '[]') AS topics,
                   coalesce(ls.status, 'NOT_STARTED') AS status,
                   (b.user_id IS NOT NULL) AS bookmarked,
                   coalesce(dw.response_json::text, '{}') AS "workspaceData",
                   dw.completed_at AS "workspaceCompletedAt"
            FROM catalogue_item ci
            JOIN LATERAL (SELECT source_id, raw_metadata_json FROM source_item
                          WHERE content_item_id=ci.id AND active=true ORDER BY external_key LIMIT 1) si ON true
            JOIN source src ON src.id=si.source_id
            JOIN user_account u ON u.email=?
            LEFT JOIN learning_state ls ON ls.content_item_id=ci.id AND ls.user_id=u.id
            LEFT JOIN bookmark b ON b.content_item_id=ci.id AND b.user_id=u.id
            LEFT JOIN design_workspace dw ON dw.content_item_id=ci.id AND dw.user_id=u.id
            WHERE ci.track=? AND ci.lifecycle='ACTIVE'
            ORDER BY si.raw_metadata_json->>'category', ci.display_title
            """, auth.getName(), normalizedTrack);
    }

    @GetMapping("/items/{id}/workspace")
    Object workspace(Authentication auth, @PathVariable UUID id) {
        String track = itemTrack(id);
        var rows = jdbc.queryForList("SELECT dw.response_json::text AS response, dw.updated_at AS \"updatedAt\", " +
            "dw.completed_at AS \"completedAt\", (SELECT next_review_at FROM learning_state ls " +
            "WHERE ls.user_id=dw.user_id AND ls.content_item_id=dw.content_item_id) AS \"nextReviewAt\", " +
            "(SELECT coalesce(confidence,independence_rating) FROM learning_attempt la " +
            "WHERE la.user_id=dw.user_id AND la.content_item_id=dw.content_item_id AND la.outcome='COMPLETED' " +
            "ORDER BY la.occurred_at DESC LIMIT 1) AS confidence FROM design_workspace dw " +
            "WHERE dw.user_id=(SELECT id FROM user_account WHERE email=?) AND dw.content_item_id=?", auth.getName(), id);
        if (rows.isEmpty()) return Map.of("track", track, "response", Map.of(), "completedAt", "", "nextReviewAt", "", "confidence", "");
        var row = rows.getFirst();
        try {
            return Map.of("track", track, "response", mapper.readValue((String) row.get("response"), Map.class),
                "updatedAt", row.get("updatedAt"), "completedAt", row.get("completedAt") == null ? "" : row.get("completedAt"),
                "nextReviewAt", row.get("nextReviewAt") == null ? "" : row.get("nextReviewAt"),
                "confidence", row.get("confidence") == null ? "" : row.get("confidence"));
        } catch (Exception e) {
            throw new IllegalStateException("Saved design workspace data could not be read", e);
        }
    }

    @PutMapping("/items/{id}/workspace")
    @Transactional
    Object saveWorkspace(Authentication auth, @PathVariable UUID id, @RequestBody WorkspaceRequest body) {
        String track = itemTrack(id);
        Map<String, String> responses = body.responses() == null ? Map.of() : body.responses();
        Set<String> allowedFields = "LLD".equals(track) ? LLD_FIELDS : SYSTEM_DESIGN_FIELDS;
        if (!allowedFields.containsAll(responses.keySet()))
            throw new ResponseStatusException(BAD_REQUEST, "This design packet contains an unsupported field.");
        int totalLength = 0;
        for (var entry : responses.entrySet()) {
            if (entry.getValue() == null || entry.getValue().length() > 12_000)
                throw new ResponseStatusException(BAD_REQUEST, "Each design note must be under 12,000 characters.");
            totalLength += entry.getValue().length();
        }
        if (totalLength > 50_000)
            throw new ResponseStatusException(BAD_REQUEST, "Your design notes exceed the 50,000 character limit.");
        boolean complete = Boolean.TRUE.equals(body.complete());
        Integer confidence = body.confidence();
        if (confidence != null && (confidence < 1 || confidence > 5))
            throw new ResponseStatusException(BAD_REQUEST, "Confidence must be between 1 and 5.");
        if (complete && (confidence == null || responses.getOrDefault("requirements", "").isBlank()))
            throw new ResponseStatusException(BAD_REQUEST, "Add requirements and choose a confidence rating before completing.");

        UUID userId = userId(auth.getName());
        try {
            String json = mapper.writeValueAsString(responses);
            jdbc.update("INSERT INTO design_workspace(user_id,content_item_id,track,response_json,completed_at) " +
                "VALUES (?,?,?,?::jsonb,CASE WHEN ? THEN now() ELSE NULL END) " +
                "ON CONFLICT(user_id,content_item_id) DO UPDATE SET track=EXCLUDED.track,response_json=EXCLUDED.response_json, " +
                "updated_at=now(), completed_at=CASE WHEN ? THEN now() ELSE design_workspace.completed_at END",
                userId, id, track, json, complete, complete);
        } catch (Exception e) {
            if (e instanceof ResponseStatusException responseStatusException) throw responseStatusException;
            throw new IllegalStateException("Design packet could not be saved", e);
        }

        jdbc.update("INSERT INTO learning_state(user_id,content_item_id,status,updated_at) VALUES (?,?, 'IN_PROGRESS',now()) " +
            "ON CONFLICT(user_id,content_item_id) DO UPDATE SET " +
            "status=CASE WHEN learning_state.status='COMPLETED' THEN 'COMPLETED' ELSE 'IN_PROGRESS' END, updated_at=now()",
            userId, id);
        if (complete) {
            jdbc.update("UPDATE learning_state SET status='COMPLETED',completed_at=now(),latest_attempt_at=now(), " +
                "next_review_at=now()+interval '3 days',updated_at=now() WHERE user_id=? AND content_item_id=?", userId, id);
            String note = responses.getOrDefault("retrospective", "");
            if (note.length() > 2_000) note = note.substring(0, 2_000);
            jdbc.update("INSERT INTO learning_attempt(user_id,content_item_id,outcome,confidence,note) " +
                "VALUES (?,?,'COMPLETED',?,?)", userId, id, confidence, note.isBlank() ? null : note);
        }
        Object nextReviewAt = complete ? jdbc.queryForObject("SELECT next_review_at FROM learning_state WHERE user_id=? AND content_item_id=?", Object.class, userId, id) : "";
        return Map.of("saved", true, "completed", complete, "nextReviewAt", nextReviewAt == null ? "" : nextReviewAt);
    }

    private String itemTrack(UUID id) {
        try {
            String track = jdbc.queryForObject("SELECT track FROM content_item WHERE id=? AND lifecycle='ACTIVE'", String.class, id);
            if (!"SYSTEM_DESIGN".equals(track) && !"LLD".equals(track))
                throw new ResponseStatusException(BAD_REQUEST, "This item does not have a design workspace.");
            return "SYSTEM_DESIGN".equals(track) ? "SYSTEM_DESIGN" : "LLD";
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(NOT_FOUND, "Study item was not found.");
        }
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email=?", UUID.class, email);
    }

    private String normalizeTrack(String track) {
        String value = track.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SYSTEM_DESIGN", "LLD").contains(value))
            throw new ResponseStatusException(BAD_REQUEST, "Track must be SYSTEM_DESIGN or LLD.");
        return value;
    }

    public record WorkspaceRequest(Map<String, String> responses, Boolean complete, @Min(1) @Max(5) Integer confidence) {}
}

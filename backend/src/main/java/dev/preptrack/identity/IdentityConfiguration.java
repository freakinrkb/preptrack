package dev.preptrack.identity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class IdentityConfiguration {
    @Bean
    UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return email -> jdbc.query("SELECT email, password_hash, role FROM user_account WHERE email=? AND status='ACTIVE'",
            rs -> {
                if (!rs.next()) throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Account not found");
                return User.withUsername(rs.getString("email")).password(rs.getString("password_hash"))
                    .roles(rs.getString("role")).build();
            }, email);
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}

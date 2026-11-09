package dev.preptrack.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/admin/catalog/corrections")
public class CatalogCorrectionController {
    private final JdbcTemplate jdbc;

    public CatalogCorrectionController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/search")
    Object search(@RequestParam(defaultValue = "") String q) {
        String query = q.trim();
        if (query.length() < 2) return List.of();
        String term = "%" + query.toLowerCase() + "%";
        return jdbc.queryForList("SELECT ci.id,ci.track,ci.display_title AS title,ci.canonical_title AS \"sourceTitle\", " +
            "ci.canonical_url AS url FROM catalogue_item ci WHERE ci.lifecycle='ACTIVE' AND " +
            "(lower(ci.display_title) LIKE ? OR lower(ci.canonical_title) LIKE ? OR lower(ci.canonical_url) LIKE ?) " +
            "ORDER BY ci.track,ci.display_title LIMIT 25", term, term, term);
    }

    @GetMapping("/history")
    Object history(@RequestParam(required = false) UUID itemId) {
        String sql = "SELECT cc.id,cc.content_item_id AS \"contentItemId\",ci.track,cc.field_name AS \"fieldName\", " +
            "cc.previous_value AS \"previousValue\",cc.corrected_value AS \"correctedValue\",cc.evidence_url AS \"evidenceUrl\", " +
            "cc.reason,cc.status,cc.created_at AS \"createdAt\",u.email AS actor " +
            "FROM catalogue_correction cc JOIN content_item ci ON ci.id=cc.content_item_id " +
            "JOIN user_account u ON u.id=cc.actor_user_id WHERE (?::uuid IS NULL OR cc.content_item_id=?) " +
            "ORDER BY cc.created_at DESC LIMIT 100";
        return jdbc.queryForList(sql, itemId, itemId);
    }

    @PostMapping
    @Transactional
    Object correct(Authentication auth, @Valid @RequestBody CorrectionRequest body) {
        if (body.contentItemId() == null) throw new ResponseStatusException(BAD_REQUEST, "Choose a catalogue item.");
        String title = body.correctedTitle() == null ? "" : body.correctedTitle().trim();
        String reason = body.reason() == null ? "" : body.reason().trim();
        String evidence = body.evidenceUrl() == null ? "" : body.evidenceUrl().trim();
        if (title.isBlank() || title.length() > 300) throw new ResponseStatusException(BAD_REQUEST, "Corrected title must be 1–300 characters.");
        if (reason.length() < 8 || reason.length() > 1_000) throw new ResponseStatusException(BAD_REQUEST, "Explain the correction in 8–1,000 characters.");
        try {
            URI uri = URI.create(evidence);
            if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Add an evidence URL beginning with http:// or https://.");
        }
        String previous;
        try {
            previous = jdbc.queryForObject("SELECT display_title FROM catalogue_item WHERE id=? AND lifecycle='ACTIVE'", String.class, body.contentItemId());
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(NOT_FOUND, "Catalogue item was not found.");
        }
        if (previous.equals(title)) throw new ResponseStatusException(BAD_REQUEST, "The corrected title is the same as the current title.");

        UUID actor = jdbc.queryForObject("SELECT id FROM user_account WHERE email=?", UUID.class, auth.getName());
        jdbc.update("UPDATE catalogue_correction SET status='SUPERSEDED' WHERE content_item_id=? AND field_name='TITLE' AND status='ACTIVE'",
            body.contentItemId());
        UUID correctionId = jdbc.queryForObject("INSERT INTO catalogue_correction(content_item_id,actor_user_id,field_name,previous_value,corrected_value,evidence_url,reason) " +
            "VALUES (?,?,'TITLE',?,?,?,?) RETURNING id", UUID.class, body.contentItemId(), actor, previous, title, evidence, reason);
        jdbc.update("INSERT INTO audit_event(actor_user_id,event_type,safe_metadata) VALUES (?,'CATALOGUE_TITLE_CORRECTED', " +
            "jsonb_build_object('correctionId',?::text,'contentItemId',?::text))", actor, correctionId, body.contentItemId());
        return Map.of("id", correctionId, "contentItemId", body.contentItemId(), "previousTitle", previous,
            "correctedTitle", title, "status", "ACTIVE");
    }

    public record CorrectionRequest(UUID contentItemId,
                                    @NotBlank @Size(min = 1, max = 300) String correctedTitle,
                                    @NotBlank @Size(max = 2_000) String evidenceUrl,
                                    @NotBlank @Size(min = 8, max = 1_000) String reason) {}
}

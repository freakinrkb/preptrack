package dev.preptrack.learning;

import tools.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/learning")
public class ProgressController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ProgressController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping("/reviews")
    Object reviews(Authentication auth, @RequestParam(defaultValue = "ALL") String track) {
        String normalized = normalizeTrack(track, true);
        UUID userId = userId(auth.getName());
        return jdbc.queryForList("""
            SELECT ci.id,ci.track,ci.kind,ci.display_title AS title,ci.canonical_url AS url,
                   ls.status,ls.next_review_at AS "dueAt",
                   (SELECT coalesce(confidence,independence_rating) FROM learning_attempt a
                    WHERE a.user_id=ls.user_id AND a.content_item_id=ci.id AND a.outcome='COMPLETED'
                    ORDER BY a.occurred_at DESC LIMIT 1) AS rating,
                   coalesce((SELECT json_agg(t.name ORDER BY t.name)::text FROM content_topic ct
                             JOIN topic t ON t.id=ct.topic_id WHERE ct.content_item_id=ci.id),'[]') AS topics
            FROM learning_state ls JOIN catalogue_item ci ON ci.id=ls.content_item_id
            WHERE ls.user_id=? AND ci.lifecycle='ACTIVE' AND ls.next_review_at<=now()
              AND (?='ALL' OR ci.track=?)
            ORDER BY ls.next_review_at,ci.display_title
            """, userId, normalized, normalized);
    }

    @PostMapping("/items/{id}/review-date")
    Object reschedule(Authentication auth, @PathVariable UUID id, @RequestBody RescheduleRequest body) {
        int days = body.days() == null ? 0 : body.days();
        if (!Set.of(1, 3, 7).contains(days))
            throw new ResponseStatusException(BAD_REQUEST, "Choose a review delay of 1, 3, or 7 days.");
        UUID userId = userId(auth.getName());
        int updated = jdbc.update("UPDATE learning_state ls SET next_review_at=now()+(? * interval '1 day'),updated_at=now() " +
            "FROM content_item ci WHERE ls.content_item_id=ci.id AND ls.user_id=? AND ci.id=? AND ci.lifecycle='ACTIVE'", days, userId, id);
        if (updated == 0) throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Review item was not found.");
        return Map.of("rescheduled", true, "days", days);
    }

    @GetMapping("/insights")
    Object insights(Authentication auth, @RequestParam(defaultValue = "ALL") String track,
                    @RequestParam(required = false) String topic) {
        String normalizedTrack = normalizeTrack(track, true);
        String normalizedTopic = topic == null || topic.isBlank() ? null : topic.trim();
        if (normalizedTopic != null && !normalizedTopic.matches("[a-z0-9-]{1,140}"))
            throw new ResponseStatusException(BAD_REQUEST, "Topic filter is invalid.");
        UUID userId = userId(auth.getName());
        String cte = """
            WITH scored AS (
                SELECT ci.id,ci.track,ls.status,ls.next_review_at,r.rating
                FROM content_item ci
                LEFT JOIN learning_state ls ON ls.content_item_id=ci.id AND ls.user_id=?
                LEFT JOIN LATERAL (
                    SELECT coalesce(a.confidence,a.independence_rating)::numeric AS rating
                    FROM learning_attempt a WHERE a.user_id=? AND a.content_item_id=ci.id AND a.outcome='COMPLETED'
                      AND coalesce(a.confidence,a.independence_rating) IS NOT NULL
                    ORDER BY a.occurred_at DESC LIMIT 1
                ) r ON true
                WHERE ci.lifecycle='ACTIVE' AND (?='ALL' OR ci.track=?)
                  AND (CAST(? AS text) IS NULL OR EXISTS(
                    SELECT 1 FROM content_topic ct JOIN topic t ON t.id=ct.topic_id
                    WHERE ct.content_item_id=ci.id AND t.slug=?))
            )
            """;
        Object[] args = {userId, userId, normalizedTrack, normalizedTrack, normalizedTopic, normalizedTopic};
        Map<String, Object> summary = jdbc.queryForMap(cte + "SELECT count(*) AS \"activeItems\", " +
            "count(*) FILTER(WHERE status='COMPLETED') AS completed, count(rating) AS \"ratedSampleSize\", " +
            "round(avg(rating),2) AS \"averageRating\", count(*) FILTER(WHERE next_review_at<=now()) AS \"dueItems\" FROM scored", args);
        List<Map<String, Object>> byTrack = jdbc.queryForList(cte + "SELECT track,count(*) AS \"activeItems\", " +
            "count(*) FILTER(WHERE status='COMPLETED') AS completed,count(rating) AS \"ratedSampleSize\", " +
            "round(avg(rating),2) AS \"averageRating\" FROM scored GROUP BY track ORDER BY track", args);
        List<Map<String, Object>> byTopic = jdbc.queryForList(cte + "SELECT t.name AS topic,t.slug AS \"topicSlug\",s.track,count(*) AS \"activeItems\", " +
            "count(*) FILTER(WHERE s.status='COMPLETED') AS completed,count(s.rating) AS \"ratedSampleSize\", " +
            "round(avg(s.rating),2) AS \"averageRating\" FROM scored s JOIN content_topic ct ON ct.content_item_id=s.id " +
            "JOIN topic t ON t.id=ct.topic_id GROUP BY t.name,t.slug,s.track ORDER BY t.name,s.track", args);
        return Map.of("track", normalizedTrack, "topic", normalizedTopic == null ? "" : normalizedTopic,
            "summary", summary, "byTrack", byTrack, "byTopic", byTopic, "streakDays", streakDays(userId));
    }

    @GetMapping("/activity")
    Object activity(Authentication auth) {
        UUID userId = userId(auth.getName());
        String timezone = jdbc.queryForObject("SELECT timezone FROM user_preference WHERE user_id=?", String.class, userId);
        ZoneId zoneId;
        try { zoneId = ZoneId.of(timezone); } catch (Exception ignored) { zoneId = ZoneId.of("UTC"); }
        LocalDate today = LocalDate.now(zoneId);
        LocalDate firstDay = today.minusDays(6);
        List<Map<String, Object>> days = jdbc.queryForList("""
            WITH days AS (
                SELECT generate_series(CAST(? AS date), CAST(? AS date), interval '1 day')::date AS day
            )
            SELECT to_char(days.day, 'YYYY-MM-DD') AS date, count(a.id) AS attempts
            FROM days LEFT JOIN learning_attempt a
              ON a.user_id=? AND (a.occurred_at AT TIME ZONE ?)::date=days.day
            GROUP BY days.day ORDER BY days.day
            """, firstDay, today, userId, zoneId.getId());
        int totalAttempts = days.stream().mapToInt(day -> ((Number) day.get("attempts")).intValue()).sum();
        long activeDays = days.stream().filter(day -> ((Number) day.get("attempts")).intValue() > 0).count();
        return Map.of("timezone", zoneId.getId(), "today", today.toString(), "days", days,
            "activeDays", activeDays, "totalAttempts", totalAttempts);
    }

    @GetMapping("/bookmarks")
    Object bookmarks(Authentication auth) {
        UUID userId = userId(auth.getName());
        return jdbc.queryForList("""
            SELECT ci.id,ci.track,ci.kind,ci.display_title AS title,ci.canonical_url AS url,
                   coalesce(ls.status,'NOT_STARTED') AS status,
                   coalesce((SELECT json_agg(t.name ORDER BY t.name)::text FROM content_topic ct
                             JOIN topic t ON t.id=ct.topic_id WHERE ct.content_item_id=ci.id),'[]') AS topics,
                   b.created_at AS "savedAt"
            FROM bookmark b JOIN catalogue_item ci ON ci.id=b.content_item_id
            LEFT JOIN learning_state ls ON ls.content_item_id=ci.id AND ls.user_id=b.user_id
            WHERE b.user_id=? AND ci.lifecycle='ACTIVE' ORDER BY b.created_at DESC
            """, userId);
    }

    @GetMapping("/export")
    ResponseEntity<byte[]> export(Authentication auth) {
        UUID userId = userId(auth.getName());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", java.time.Instant.now().toString());
        data.put("learningState", jdbc.queryForList("SELECT ci.track,ci.kind,ci.display_title AS title,ci.canonical_url AS url, " +
            "ls.status,ls.completed_at AS \"completedAt\",ls.latest_attempt_at AS \"latestAttemptAt\",ls.next_review_at AS \"nextReviewAt\" " +
            "FROM learning_state ls JOIN catalogue_item ci ON ci.id=ls.content_item_id WHERE ls.user_id=? ORDER BY ci.track,ci.display_title", userId));
        data.put("attempts", jdbc.queryForList("SELECT ci.track,ci.display_title AS title,ci.canonical_url AS url,la.occurred_at AS \"occurredAt\", " +
            "la.outcome,la.independence_rating AS \"independenceRating\",la.confidence,la.duration_seconds AS \"durationSeconds\",la.note FROM learning_attempt la " +
            "JOIN catalogue_item ci ON ci.id=la.content_item_id WHERE la.user_id=? ORDER BY la.occurred_at", userId));
        data.put("bookmarks", jdbc.queryForList("SELECT ci.track,ci.display_title AS title,ci.canonical_url AS url,b.created_at AS \"savedAt\" " +
            "FROM bookmark b JOIN catalogue_item ci ON ci.id=b.content_item_id WHERE b.user_id=? ORDER BY b.created_at", userId));
        data.put("designWorkspaces", jdbc.queryForList("SELECT ci.track,ci.display_title AS title,ci.canonical_url AS url, " +
            "dw.response_json AS responses,dw.updated_at AS \"updatedAt\",dw.completed_at AS \"completedAt\" FROM design_workspace dw " +
            "JOIN catalogue_item ci ON ci.id=dw.content_item_id WHERE dw.user_id=? ORDER BY ci.track,ci.display_title", userId));
        try {
            byte[] json = mapper.writeValueAsBytes(data);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=preptrack-learning-export.json")
                .body(json);
        } catch (Exception e) {
            throw new IllegalStateException("Your learning export could not be prepared", e);
        }
    }

    private int streakDays(UUID userId) {
        String zone = jdbc.queryForObject("SELECT timezone FROM user_preference WHERE user_id=?", String.class, userId);
        ZoneId zoneId;
        try { zoneId = ZoneId.of(zone); } catch (Exception ignored) { zoneId = ZoneId.of("UTC"); }
        LocalDate today = LocalDate.now(zoneId);
        List<String> recentDays = jdbc.queryForList("SELECT DISTINCT to_char((occurred_at AT TIME ZONE ?)::date,'YYYY-MM-DD') " +
            "FROM learning_attempt WHERE user_id=? AND occurred_at>=now()-interval '400 days'", String.class, zoneId.getId(), userId);
        Set<LocalDate> activityDays = new HashSet<>();
        recentDays.forEach(day -> activityDays.add(LocalDate.parse(day)));
        LocalDate cursor = today;
        if (!activityDays.contains(cursor)) {
            if (!activityDays.contains(today.minusDays(1))) return 0;
            cursor = today.minusDays(1);
        }
        int streak = 0;
        while (activityDays.contains(cursor)) { streak++; cursor = cursor.minusDays(1); }
        return streak;
    }

    private String normalizeTrack(String track, boolean allowAll) {
        String value = track.trim().toUpperCase(Locale.ROOT);
        Set<String> values = allowAll ? Set.of("ALL","DSA","SYSTEM_DESIGN","LLD") : Set.of("DSA","SYSTEM_DESIGN","LLD");
        if (!values.contains(value)) throw new ResponseStatusException(BAD_REQUEST, "Choose a valid learning track.");
        return value;
    }

    private UUID userId(String email) {
        return jdbc.queryForObject("SELECT id FROM user_account WHERE email=?", UUID.class, email);
    }

    public record RescheduleRequest(@Min(1) @Max(7) Integer days) {}
}

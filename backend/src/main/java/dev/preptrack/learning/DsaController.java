package dev.preptrack.learning;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@RestController
public class DsaController {
    private final JdbcTemplate jdbc;
    public DsaController(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @GetMapping("/api/catalog/companies")
    Object companies(){return jdbc.queryForList("SELECT c.id,c.name,c.slug,count(DISTINCT ci.id) FILTER(WHERE ci.lifecycle='ACTIVE') AS total " +
        "FROM company c LEFT JOIN content_company cc ON cc.company_id=c.id LEFT JOIN content_item ci ON ci.id=cc.content_item_id " +
        "GROUP BY c.id ORDER BY c.name");}

    @GetMapping("/api/catalog/summary")
    Object catalogSummary(){return jdbc.queryForMap("SELECT count(*) AS questions FROM content_item WHERE track='DSA' AND lifecycle='ACTIVE'");}

    @GetMapping("/api/catalog/questions")
    Object questions(Authentication auth,@RequestParam(required=false) String company,@RequestParam(required=false) String topic,
                     @RequestParam(required=false) String q,@RequestParam(defaultValue="0") @Min(0) int page,
                     @RequestParam(defaultValue="40") @Min(1) @Max(100) int size){
        String email=auth.getName();
        String sql="SELECT ci.id,ci.display_title AS title,ci.canonical_url AS url, " +
            "CASE WHEN pref.show_difficulty THEN si.raw_metadata_json->>'difficulty' ELSE NULL END AS difficulty, " +
            "si.raw_metadata_json->>'frequency' AS frequency, " +
            "coalesce((SELECT json_agg(t.name ORDER BY t.name)::text FROM content_topic ct JOIN topic t ON t.id=ct.topic_id WHERE ct.content_item_id=ci.id),'[]') AS topics, " +
            "coalesce((SELECT json_agg(c.name ORDER BY c.name)::text FROM content_company cc JOIN company c ON c.id=cc.company_id WHERE cc.content_item_id=ci.id),'[]') AS companies, " +
            "coalesce(ls.status,'NOT_STARTED') AS status, (b.user_id IS NOT NULL) AS bookmarked " +
            "FROM catalogue_item ci JOIN LATERAL (SELECT raw_metadata_json FROM source_item WHERE content_item_id=ci.id AND active=true ORDER BY external_key LIMIT 1) si ON true " +
            "JOIN user_account u ON u.email=? JOIN user_preference pref ON pref.user_id=u.id " +
            "LEFT JOIN learning_state ls ON ls.content_item_id=ci.id AND ls.user_id=u.id " +
            "LEFT JOIN bookmark b ON b.content_item_id=ci.id AND b.user_id=u.id WHERE ci.lifecycle='ACTIVE' AND ci.track='DSA' " +
            "AND (CAST(? AS text) IS NULL OR EXISTS(SELECT 1 FROM content_company cc JOIN company c ON c.id=cc.company_id WHERE cc.content_item_id=ci.id AND c.slug=?)) " +
            "AND (CAST(? AS text) IS NULL OR EXISTS(SELECT 1 FROM content_topic ct JOIN topic t ON t.id=ct.topic_id WHERE ct.content_item_id=ci.id AND t.slug=?)) " +
            "AND (CAST(? AS text) IS NULL OR ci.display_title ILIKE ? OR ci.canonical_url ILIKE ?) " +
            "GROUP BY ci.id,ci.display_title,ci.canonical_url,si.raw_metadata_json,pref.show_difficulty,ls.status,b.user_id ORDER BY ci.display_title LIMIT ? OFFSET ?";
        String term=q==null||q.isBlank()?null:"%"+q.trim()+"%";
        return jdbc.queryForList(sql,email,company,company,topic,topic,term,term,term,size,page*size);
    }

    @PostMapping("/api/learning/items/{id}/bookmark")
    @Transactional
    Map<String,Boolean> toggleBookmark(Authentication auth,@PathVariable UUID id){
        UUID user=uuidFor(auth.getName());
        int deleted=jdbc.update("DELETE FROM bookmark WHERE user_id=? AND content_item_id=?",user,id);
        if(deleted==0)jdbc.update("INSERT INTO bookmark(user_id,content_item_id) VALUES (?,?) ON CONFLICT DO NOTHING",user,id);
        return Map.of("bookmarked",deleted==0);
    }

    @PostMapping("/api/learning/items/{id}/attempts")
    @Transactional
    Map<String,Object> attempt(Authentication auth,@PathVariable UUID id,@Valid @RequestBody AttemptRequest body){
        UUID user=uuidFor(auth.getName());
        jdbc.queryForObject("SELECT id FROM content_item WHERE id=? AND lifecycle='ACTIVE'",UUID.class,id);
        jdbc.update("INSERT INTO learning_attempt(user_id,content_item_id,outcome,independence_rating,note,duration_seconds) VALUES (?,?,?,?,?,?)",user,id,body.completed()?"COMPLETED":"IN_PROGRESS",body.completed()?body.independenceRating():null,body.note(),body.durationSeconds());
        jdbc.update("INSERT INTO learning_state(user_id,content_item_id,status,completed_at,latest_attempt_at,next_review_at,updated_at) " +
            "VALUES (?,?,?,CASE WHEN ? THEN now() ELSE NULL END,now(),CASE WHEN ? THEN now()+interval '3 days' ELSE NULL END,now()) " +
            "ON CONFLICT(user_id,content_item_id) DO UPDATE SET status=CASE WHEN learning_state.status='COMPLETED' OR excluded.status='COMPLETED' THEN 'COMPLETED' ELSE 'IN_PROGRESS' END, " +
            "completed_at=coalesce(excluded.completed_at,learning_state.completed_at),latest_attempt_at=now(),next_review_at=excluded.next_review_at,updated_at=now()",
            user,id,body.completed()?"COMPLETED":"IN_PROGRESS",body.completed(),body.completed());
        return Map.of("saved",true,"status",body.completed()?"COMPLETED":"IN_PROGRESS");
    }

    @GetMapping("/api/learning/summary")
    Object summary(Authentication auth){return jdbc.queryForMap("SELECT count(*) FILTER(WHERE ls.status='COMPLETED') AS completed, count(*) AS tracked, " +
        "count(*) FILTER(WHERE ls.next_review_at<=now()) AS due FROM learning_state ls JOIN content_item ci ON ci.id=ls.content_item_id " +
        "WHERE ls.user_id=? AND ci.track='DSA'",uuidFor(auth.getName()));}

    @GetMapping("/api/learning/recent")
    Object recent(Authentication auth){return jdbc.queryForList("SELECT ci.id,ci.track,ci.display_title AS title,la.outcome,la.independence_rating AS \"independenceRating\",la.confidence,la.duration_seconds AS \"durationSeconds\",la.occurred_at AS \"occurredAt\" " +
        "FROM learning_attempt la JOIN catalogue_item ci ON ci.id=la.content_item_id WHERE la.user_id=? ORDER BY la.occurred_at DESC LIMIT 5",uuidFor(auth.getName()));}

    @GetMapping("/api/learning/items/{id}/attempts")
    Object attempts(Authentication auth,@PathVariable UUID id){
        return jdbc.queryForList("SELECT occurred_at AS \"occurredAt\",outcome,independence_rating AS \"independenceRating\",confidence,duration_seconds AS \"durationSeconds\",note " +
            "FROM learning_attempt WHERE user_id=? AND content_item_id=? ORDER BY occurred_at DESC LIMIT 30",uuidFor(auth.getName()),id);
    }

    private UUID uuidFor(String email){return jdbc.queryForObject("SELECT id FROM user_account WHERE email=?",UUID.class,email);}
    record AttemptRequest(boolean completed,@Min(1) @Max(5) Integer independenceRating,@Size(max=2000) String note,@Min(0) @Max(86400) Integer durationSeconds){
        @jakarta.validation.constraints.AssertTrue(message="Completed items require an independence rating")
        public boolean isRatingValid(){return !completed||independenceRating!=null;}
    }
}

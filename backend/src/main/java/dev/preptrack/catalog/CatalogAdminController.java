package dev.preptrack.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/catalog")
public class CatalogAdminController {
    private final CatalogSyncService sync;
    private final JdbcTemplate jdbc;

    public CatalogAdminController(CatalogSyncService sync,JdbcTemplate jdbc){this.sync=sync;this.jdbc=jdbc;}

    @GetMapping("/companies")
    Object companies(){return jdbc.queryForList("SELECT c.id,c.name,c.slug,count(DISTINCT cc.content_item_id) AS question_count FROM company c LEFT JOIN content_company cc ON cc.company_id=c.id LEFT JOIN content_item ci ON ci.id=cc.content_item_id AND ci.lifecycle='ACTIVE' GROUP BY c.id ORDER BY c.name");}

    @PostMapping("/sync/preview")
    Object preview() throws Exception{return sync.preview();}

    @PostMapping("/sync/apply")
    Object apply(Authentication auth,@Valid @RequestBody ApplyRequest body) throws Exception {return sync.apply(body.fingerprint(),auth.getName());}

    @GetMapping("/sync/status")
    Object status(){return jdbc.queryForList("SELECT sr.revision_ref,sr.fetched_at,sr.status,sr.summary FROM source_revision sr JOIN source s ON s.id=sr.source_id WHERE s.slug='leetcode-company-wise-problems' ORDER BY sr.fetched_at DESC LIMIT 10");}

    record ApplyRequest(@NotBlank String fingerprint){}
}

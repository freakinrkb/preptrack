package dev.preptrack.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest
class CatalogSyncIsolationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv().getOrDefault("PREPTRACK_TEST_DB_URL", "jdbc:postgresql://localhost:5432/preptrack_test"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("PREPTRACK_TEST_DB_USER", System.getenv().getOrDefault("USER", "postgres")));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("PREPTRACK_TEST_DB_PASSWORD", ""));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogSyncService sync;
    @MockitoBean DsaSourceAdapter adapter;

    @BeforeEach
    void cleanData() {
        jdbc.update("DELETE FROM learning_attempt"); jdbc.update("DELETE FROM learning_state"); jdbc.update("DELETE FROM bookmark");
        jdbc.update("DELETE FROM content_topic"); jdbc.update("DELETE FROM content_company"); jdbc.update("DELETE FROM source_item");
        jdbc.update("DELETE FROM content_item"); jdbc.update("DELETE FROM topic"); jdbc.update("DELETE FROM company");
        jdbc.update("DELETE FROM audit_event"); jdbc.update("DELETE FROM invite"); jdbc.update("DELETE FROM user_preference"); jdbc.update("DELETE FROM user_account");
    }

    @Test
    void applyingCatalogueMetadataPreservesUserAttemptsBookmarksAndCompletion() throws Exception {
        jdbc.update("INSERT INTO user_account(email,password_hash,role) VALUES ('owner@example.test','not-used','ADMIN')");
        jdbc.update("INSERT INTO user_preference(user_id) SELECT id FROM user_account WHERE email='owner@example.test'");
        var user=jdbc.queryForObject("SELECT id FROM user_account WHERE email='owner@example.test'",java.util.UUID.class);
        var source=jdbc.queryForObject("SELECT id FROM source WHERE slug='leetcode-company-wise-problems'",java.util.UUID.class);
        var content=jdbc.queryForObject("INSERT INTO content_item(track,kind,canonical_title,canonical_url) VALUES ('DSA','QUESTION','Two Sum','https://leetcode.com/problems/two-sum') RETURNING id",java.util.UUID.class);
        String marker=java.util.UUID.randomUUID().toString();
        var revision=jdbc.queryForObject("INSERT INTO source_revision(source_id,revision_ref,status) VALUES (?, ?,'APPLIED') RETURNING id",java.util.UUID.class,source,"old-"+marker);
        var si=jdbc.queryForObject("INSERT INTO source_item(source_id,external_key,content_item_id,raw_metadata_json,last_seen_revision_id) VALUES (?, 'Amazon/5. All.csv/https://leetcode.com/problems/two-sum', ?, '{\"difficulty\":\"EASY\",\"topics\":[\"Array\"]}'::jsonb, ?) RETURNING id",java.util.UUID.class,source,content,revision);
        var company=jdbc.queryForObject("INSERT INTO company(name,slug) VALUES ('Amazon','amazon') RETURNING id",java.util.UUID.class);
        jdbc.update("INSERT INTO content_company(content_item_id,company_id,evidence_source_item_id) VALUES (?,?,?)",content,company,si);
        var topic=jdbc.queryForObject("INSERT INTO topic(name,slug,provenance) VALUES ('Array','array','SOURCE') RETURNING id",java.util.UUID.class);
        jdbc.update("INSERT INTO content_topic(content_item_id,topic_id) VALUES (?,?)",content,topic);
        jdbc.update("INSERT INTO learning_state(user_id,content_item_id,status,completed_at,latest_attempt_at) VALUES (?,?,'COMPLETED',now(),now())",user,content);
        jdbc.update("INSERT INTO learning_attempt(user_id,content_item_id,outcome,independence_rating) VALUES (?,?,'COMPLETED',4)",user,content);
        jdbc.update("INSERT INTO bookmark(user_id,content_item_id) VALUES (?,?)",user,content);

        var item=new DsaSourceAdapter.Item("Amazon","Two Sum (updated source title)","https://leetcode.com/problems/two-sum",
            List.of("Array","Hash Table"),Map.of("difficulty","EASY","frequency","100.0","acceptance rate","0.0058"));
        var snapshot=new DsaSourceAdapter.Snapshot("new-"+marker,List.of(item));
        when(adapter.fetch()).thenReturn(snapshot);

        sync.apply(snapshot.fingerprint(),"owner@example.test");

        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM learning_state WHERE user_id=? AND content_item_id=?",String.class,user,content));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM learning_attempt WHERE user_id=? AND content_item_id=?",Integer.class,user,content));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM bookmark WHERE user_id=? AND content_item_id=?",Integer.class,user,content));
        assertEquals("Two Sum (updated source title)",jdbc.queryForObject("SELECT canonical_title FROM content_item WHERE id=?",String.class,content));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM content_topic WHERE content_item_id=?",Integer.class,content));
    }
}

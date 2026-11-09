package dev.preptrack.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** One-time, opt-in import into a dedicated local PrepTrack database. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named="PREPTRACK_LIVE_IMPORT",matches="enabled")
class LiveDsaCatalogImportTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv().getOrDefault("PREPTRACK_TEST_DB_URL", "jdbc:postgresql://localhost:5432/preptrack_app"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("PREPTRACK_TEST_DB_USER", System.getenv().getOrDefault("USER", "postgres")));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("PREPTRACK_TEST_DB_PASSWORD", ""));
    }

    @Autowired CatalogSyncService sync;
    @Autowired JdbcTemplate jdbc;

    @Test
    void previewAndApplyCurrentPublicSource() throws Exception {
        var preview=sync.preview();
        System.out.printf("Live DSA source preview: %d rows, %d companies, %d new questions, %d metadata changes, %d removals%n",
            preview.rows(),preview.companies(),preview.newQuestions(),preview.metadataUpdates(),preview.removedAssociations());
        var result=sync.apply(preview.fingerprint(),"catalog-import@preptrack.local");
        Long count=jdbc.queryForObject("SELECT count(*) FROM content_item WHERE track='DSA' AND lifecycle='ACTIVE'",Long.class);
        assertTrue(result.rows()>0 && count!=null && count>0);
        System.out.printf("Imported %d source rows; %d active unique DSA questions are ready.%n",result.rows(),count);
    }
}

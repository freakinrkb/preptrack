package dev.preptrack.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityIsolationTest {
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv().getOrDefault("PREPTRACK_TEST_DB_URL", "jdbc:postgresql://localhost:5432/preptrack_test"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("PREPTRACK_TEST_DB_USER", "freakinrkb"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("PREPTRACK_TEST_DB_PASSWORD", ""));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void seedAccounts() {
        jdbc.update("DELETE FROM learning_attempt");
        jdbc.update("DELETE FROM learning_state");
        jdbc.update("DELETE FROM bookmark");
        jdbc.update("DELETE FROM content_topic");
        jdbc.update("DELETE FROM content_company");
        jdbc.update("DELETE FROM source_item");
        jdbc.update("DELETE FROM content_item");
        jdbc.update("DELETE FROM topic");
        jdbc.update("DELETE FROM company");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM invite");
        jdbc.update("DELETE FROM user_preference");
        jdbc.update("DELETE FROM user_account");
        createAccount("one@example.test", "first-password-123", "USER");
        createAccount("two@example.test", "second-password-456", "USER");
        jdbc.update("INSERT INTO content_item(track,kind,canonical_title,canonical_url) VALUES ('DSA','QUESTION','Two Sum','https://leetcode.com/problems/two-sum')");
        jdbc.update("INSERT INTO company(name,slug) VALUES ('Amazon','amazon')");
        UUIDs ids=jdbc.query("SELECT id FROM content_item WHERE canonical_url='https://leetcode.com/problems/two-sum'",rs->{rs.next();return new UUIDs(rs.getObject(1,java.util.UUID.class));});
        var sourceId=jdbc.queryForObject("SELECT id FROM source WHERE slug='leetcode-company-wise-problems'",java.util.UUID.class);
        var companyId=jdbc.queryForObject("SELECT id FROM company WHERE slug='amazon'",java.util.UUID.class);
        var sourceItemId=jdbc.queryForObject("INSERT INTO source_item(source_id,external_key,content_item_id,raw_metadata_json) VALUES (?, 'Amazon/5. All.csv/two-sum', ?, '{\"difficulty\":\"EASY\",\"topics\":[\"Array\",\"Hash Table\"]}'::jsonb) RETURNING id",java.util.UUID.class,sourceId,ids.contentId());
        jdbc.update("INSERT INTO content_company(content_item_id,company_id,evidence_source_item_id) VALUES (?,?,?)",ids.contentId(),companyId,sourceItemId);
        jdbc.update("INSERT INTO topic(name,slug) VALUES ('Array','array'),('Hash Table','hash-table')");
        jdbc.update("INSERT INTO content_topic(content_item_id,topic_id) SELECT ?,id FROM topic",ids.contentId());
    }

    @Test
    void privateEndpointsRejectAnonymousRequests() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/preferences").with(csrf())
            .contentType("application/json")
            .content("{\"showDifficulty\":true,\"timezone\":\"Asia/Kolkata\",\"theme\":\"dark\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void eachSessionReadsAndChangesOnlyItsOwnPreferences() throws Exception {
        MvcResult one = login("one@example.test", "first-password-123");
        MvcResult two = login("two@example.test", "second-password-456");

        mvc.perform(withSession(one, get("/api/auth/me")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("one@example.test"));
        mvc.perform(withSession(two, get("/api/auth/me")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("two@example.test"));

        mvc.perform(withSession(one, post("/api/auth/preferences"))
            .with(csrf()).contentType("application/json")
            .content("{\"showDifficulty\":true,\"timezone\":\"Asia/Kolkata\",\"theme\":\"dark\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.showDifficulty").value(true));

        mvc.perform(withSession(two, get("/api/auth/me")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.showDifficulty").value(false));
    }

    @Test
    void adminInviteEndpointRejectsRegularUsers() throws Exception {
        MvcResult user = login("one@example.test", "first-password-123");
        mvc.perform(withSession(user, post("/api/auth/admin/invites"))
            .with(csrf()).contentType("application/json").content("{\"email\":\"new@example.test\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void dsaAttemptsRemainPrivateToTheirOwner() throws Exception {
        MvcResult one=login("one@example.test","first-password-123");
        MvcResult two=login("two@example.test","second-password-456");
        var itemId=jdbc.queryForObject("SELECT id FROM content_item WHERE canonical_title='Two Sum'",java.util.UUID.class);
        mvc.perform(withSession(one, post("/api/learning/items/"+itemId+"/attempts"))
            .with(csrf()).contentType("application/json").content("{\"completed\":true,\"independenceRating\":5}"))
            .andExpect(status().isOk());
        mvc.perform(withSession(one, get("/api/catalog/questions?company=amazon")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("COMPLETED"));
        mvc.perform(withSession(two, get("/api/catalog/questions?company=amazon")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("NOT_STARTED"));
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk()).andReturn();
    }

    private MockHttpServletRequestBuilder withSession(MvcResult login, MockHttpServletRequestBuilder request) {
        return request.cookie(login.getResponse().getCookies());
    }

    private void createAccount(String email, String password, String role) {
        jdbc.update("INSERT INTO user_account(email,password_hash,role) VALUES (?,?,?)", email, encoder.encode(password), role);
        jdbc.update("INSERT INTO user_preference(user_id) SELECT id FROM user_account WHERE email=?", email);
    }

    private record UUIDs(java.util.UUID contentId){}
}

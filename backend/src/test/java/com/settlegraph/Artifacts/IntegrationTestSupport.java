package com.settlegraph.Artifacts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for *IT.java integration tests: the full Spring context and security
 * filter chain, driven through MockMvc, against the REAL local
 * {@code settlegraph_test} Postgres database (profile "it"). Run by failsafe
 * in {@code mvn verify}; never part of the fast {@code mvn test} suite.
 *
 * Every test starts from empty tables. No {@code @Transactional} rollback:
 * requests commit for real, exactly as in production, so transaction and
 * constraint behaviour is what's actually being tested.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
public abstract class IntegrationTestSupport {

    private static final String TEST_DATABASE = "settlegraph_test";
    protected static final List<String> TABLES = List.of("notification", "audit_log", "settlement",
            "expense_split", "expense", "group_invite", "group_member", "app_group", "app_user");
    protected static final String PASSWORD = "it-password-1";

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    @Autowired private DataSource dataSource;

    /** A registered user: their id, username, email and a real JWT from the login flow. */
    protected record TestUser(Long id, String username, String email, String token) {
        public String bearer() { return "Bearer " + token; }
    }

    @BeforeEach
    void resetDatabase() throws Exception {
        // Guard: this TRUNCATEs every table. Refuse unless we are provably
        // connected to the dedicated test database, whatever the config says.
        try (Connection connection = dataSource.getConnection()) {
            String database = connection.getCatalog();
            if (!TEST_DATABASE.equals(database)) {
                throw new IllegalStateException("Refusing to truncate: connected to '" + database
                        + "' (" + connection.getMetaData().getURL() + "), not " + TEST_DATABASE);
            }
        }
        jdbc.execute("TRUNCATE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE");
    }

    // ---- helpers: all setup goes through the real HTTP API ----

    protected JsonNode body(ResultActions actions) throws Exception {
        MvcResult result = actions.andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, Object payload)
            throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
    }

    protected TestUser register(String username) throws Exception {
        String email = username + "@example.test";
        JsonNode auth = body(mvc.perform(withJson(post("/api/auth/register"), Map.of(
                "username", username, "name", username, "email", email, "password", PASSWORD)))
                .andExpect(status().isOk()));
        return new TestUser(auth.get("userId").asLong(), username, email, auth.get("token").asText());
    }

    protected Long createGroup(TestUser owner, String name) throws Exception {
        return body(mvc.perform(withJson(post("/api/groups"), Map.of("name", name))
                .header("Authorization", owner.bearer()))
                .andExpect(status().isOk())).get("id").asLong();
    }

    protected Long invite(TestUser inviter, Long groupId, TestUser invitee) throws Exception {
        return body(mvc.perform(withJson(post("/api/groups/" + groupId + "/invites"),
                        Map.of("username", invitee.username()))
                .header("Authorization", inviter.bearer()))
                .andExpect(status().isOk())).get("id").asLong();
    }

    protected void accept(TestUser invitee, Long inviteId) throws Exception {
        mvc.perform(post("/api/invites/" + inviteId + "/accept").header("Authorization", invitee.bearer()))
                .andExpect(status().isOk());
    }

    /** Invite + accept: the only way into a group besides creating it. */
    protected void join(TestUser inviter, Long groupId, TestUser joiner) throws Exception {
        accept(joiner, invite(inviter, groupId, joiner));
    }

    protected JsonNode addEqualExpense(TestUser payer, Long groupId, String amount,
                                       List<TestUser> participants, boolean recurring) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("groupId", groupId);
        request.put("amount", amount);
        request.put("currency", "INR");
        request.put("description", "Expense of " + amount);
        request.put("splitType", "EQUAL");
        request.put("participantIds", participants.stream().map(TestUser::id).toList());
        if (recurring) {
            request.put("recurring", true);
            request.put("recurrenceFrequency", "MONTHLY");
        }
        return body(mvc.perform(withJson(post("/api/expenses"), request)
                .header("Authorization", payer.bearer()))
                .andExpect(status().isOk()));
    }

    /** Row counts for every table plus the mutable state, to prove a rejected request changed nothing. */
    protected String databaseSnapshot() {
        StringBuilder snapshot = new StringBuilder();
        for (String table : TABLES) {
            snapshot.append(table).append('=')
                    .append(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).append(' ');
        }
        snapshot.append("settlements=").append(jdbc.queryForList(
                "SELECT id || ':' || status FROM settlement ORDER BY id", String.class));
        snapshot.append(" recurring=").append(jdbc.queryForList(
                "SELECT id || ':' || is_recurring FROM expense ORDER BY id", String.class));
        snapshot.append(" invites=").append(jdbc.queryForList(
                "SELECT id || ':' || status FROM group_invite ORDER BY id", String.class));
        snapshot.append(" read=").append(jdbc.queryForList(
                "SELECT id || ':' || is_read FROM notification ORDER BY id", String.class));
        return snapshot.toString();
    }
}

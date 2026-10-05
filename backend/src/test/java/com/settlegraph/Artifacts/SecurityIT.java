package com.settlegraph.Artifacts;

import com.settlegraph.Artifacts.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication through the REAL security filter chain: a request without a
 * valid token is a 401 — distinct from 403, which means "authenticated but
 * not allowed" — and is turned away before anything is written.
 */
class SecurityIT extends IntegrationTestSupport {

    @Value("${settlegraph.jwt.secret}")
    private String secret;

    /** Every endpoint except /api/auth/**. IDs are arbitrary: a 401 must happen before any lookup. */
    static Stream<Arguments> protectedEndpoints() {
        return Stream.of(
                arguments("GET", "/api/groups"),
                arguments("POST", "/api/groups"),
                arguments("GET", "/api/groups/1"),
                arguments("GET", "/api/groups/1/members"),
                arguments("POST", "/api/groups/1/invites"),
                arguments("GET", "/api/groups/1/invites"),
                arguments("GET", "/api/invites/mine"),
                arguments("POST", "/api/invites/1/accept"),
                arguments("POST", "/api/invites/1/decline"),
                arguments("GET", "/api/users/search?q=ab"),
                arguments("POST", "/api/expenses"),
                arguments("GET", "/api/expenses/group/1"),
                arguments("PATCH", "/api/expenses/1/stop-recurring"),
                arguments("GET", "/api/groups/1/balances"),
                arguments("POST", "/api/groups/1/settlements/generate"),
                arguments("GET", "/api/groups/1/settlements"),
                arguments("PATCH", "/api/groups/1/settlements/1/mark-paid"),
                arguments("GET", "/api/groups/1/analytics/by-category"),
                arguments("GET", "/api/groups/1/analytics/by-month"),
                arguments("GET", "/api/groups/1/audit-log"),
                arguments("GET", "/api/notifications"),
                arguments("PATCH", "/api/notifications/1/read"));
    }

    @ParameterizedTest(name = "{0} {1} without a token -> 401")
    @MethodSource("protectedEndpoints")
    void everyProtectedEndpoint_withoutAToken_is401_andWritesNothing(String method, String path) throws Exception {
        String before = databaseSnapshot();

        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isUnauthorized());

        assertEquals(before, databaseSnapshot());
    }

    // ---- the token variants, end to end through the filter chain ----

    @Test
    void validToken_isAccepted() throws Exception {
        // Control: proves the 401s below come from the token, not the endpoint.
        TestUser alice = register("alice");
        mvc.perform(get("/api/groups").header("Authorization", alice.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void expiredToken_is401() throws Exception {
        TestUser alice = register("alice");
        String expired = new JwtService(secret, -1_000).generateToken(alice.email(), alice.id());

        mvc.perform(get("/api/groups").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedToken_is401() throws Exception {
        mvc.perform(get("/api/groups").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithTheWrongSecret_is401_evenForARealUser() throws Exception {
        TestUser alice = register("alice");
        String forged = new JwtService("a-completely-different-secret-also-32-bytes-xyz", 60_000)
                .generateToken(alice.email(), alice.id());

        mvc.perform(get("/api/groups").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authEndpoints_workWithoutAToken() throws Exception {
        register("alice"); // asserts 200 on POST /api/auth/register without a token

        mvc.perform(withJson(post("/api/auth/login"), Map.of("identifier", "alice", "password", PASSWORD)))
                .andExpect(status().isOk());
    }
}

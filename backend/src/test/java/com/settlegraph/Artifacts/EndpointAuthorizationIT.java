package com.settlegraph.Artifacts;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization through the real controllers, filter chain and database.
 * Every group-scoped endpoint: a non-member gets 403 and nothing in the
 * database changes; an ordinary (non-owner) member gets 200. Plus the
 * endpoints scoped to the caller rather than a group.
 *
 * Fixture, rebuilt from empty tables before every test, all via the API:
 * alice creates a group, bob joins by invite, alice adds a recurring EQUAL
 * 90.00 expense for the two of them, and a plan is generated (bob owes 45).
 * carol is registered but not invited; mallory is the outsider.
 */
class EndpointAuthorizationIT extends IntegrationTestSupport {

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;
    private TestUser mallory;
    private Fixture fixture;

    record Fixture(Long groupId, Long aliceId, Long bobId, String carolUsername, Long expenseId, Long settlementId) {}

    @BeforeEach
    void buildFixture() throws Exception {
        alice = register("alice");
        bob = register("bob");
        carol = register("carol");
        mallory = register("mallory");
        Long groupId = createGroup(alice, "Goa Trip");
        join(alice, groupId, bob);
        Long expenseId = addEqualExpense(alice, groupId, "90.00", List.of(alice, bob), true).get("id").asLong();
        JsonNode plan = body(mvc.perform(post("/api/groups/" + groupId + "/settlements/generate")
                .header("Authorization", alice.bearer())).andExpect(status().isOk()));
        assertEquals(1, plan.size(), "fixture: expected exactly bob -> alice 45.00");
        fixture = new Fixture(groupId, alice.id(), bob.id(), carol.username(), expenseId, plan.get(0).get("id").asLong());
    }

    private static Arguments call(String name, Function<Fixture, MockHttpServletRequestBuilder> request) {
        return arguments(name, request);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    static Stream<Arguments> groupScopedEndpoints() {
        return Stream.of(
                call("GET group", f -> get("/api/groups/" + f.groupId())),
                call("GET members", f -> get("/api/groups/" + f.groupId() + "/members")),
                call("POST invite", f -> json(post("/api/groups/" + f.groupId() + "/invites"),
                        "{\"username\":\"" + f.carolUsername() + "\"}")),
                call("GET group invites", f -> get("/api/groups/" + f.groupId() + "/invites")),
                call("POST expense", f -> json(post("/api/expenses"),
                        "{\"groupId\":" + f.groupId() + ",\"amount\":\"30.00\",\"currency\":\"INR\","
                                + "\"description\":\"Taxi\",\"splitType\":\"EQUAL\","
                                + "\"participantIds\":[" + f.aliceId() + "," + f.bobId() + "]}")),
                call("GET expenses", f -> get("/api/expenses/group/" + f.groupId())),
                call("PATCH stop-recurring", f -> patch("/api/expenses/" + f.expenseId() + "/stop-recurring")),
                call("GET balances", f -> get("/api/groups/" + f.groupId() + "/balances")),
                call("POST generate plan", f -> post("/api/groups/" + f.groupId() + "/settlements/generate")),
                call("GET settlements", f -> get("/api/groups/" + f.groupId() + "/settlements")),
                call("PATCH mark-paid", f -> patch("/api/groups/" + f.groupId() + "/settlements/"
                        + f.settlementId() + "/mark-paid")),
                call("GET analytics by-category", f -> get("/api/groups/" + f.groupId() + "/analytics/by-category")),
                call("GET analytics by-month", f -> get("/api/groups/" + f.groupId() + "/analytics/by-month")),
                call("GET audit log", f -> get("/api/groups/" + f.groupId() + "/audit-log")));
    }

    @ParameterizedTest(name = "{0} by a non-member -> 403, nothing changed")
    @MethodSource("groupScopedEndpoints")
    void groupScopedEndpoint_byANonMember_is403_andChangesNothing(
            String name, Function<Fixture, MockHttpServletRequestBuilder> request) throws Exception {
        String before = databaseSnapshot();

        mvc.perform(request.apply(fixture).header("Authorization", mallory.bearer()))
                .andExpect(status().isForbidden());

        assertEquals(before, databaseSnapshot(), name + " changed the database for a non-member");
    }

    @ParameterizedTest(name = "{0} by a member -> 200")
    @MethodSource("groupScopedEndpoints")
    void groupScopedEndpoint_byAMember_is200(
            String name, Function<Fixture, MockHttpServletRequestBuilder> request) throws Exception {
        // bob is an ordinary member, not the creator: membership is what's checked, not ownership.
        mvc.perform(request.apply(fixture).header("Authorization", bob.bearer()))
                .andExpect(status().isOk());
    }

    // ---- endpoints scoped to the caller rather than to a group ----

    private Long aNotificationOf(TestUser user) {
        return jdbc.queryForObject("SELECT min(id) FROM notification WHERE user_id = ?", Long.class, user.id());
    }

    @Test
    void markNotificationRead_bySomeoneElse_is403_andItStaysUnread() throws Exception {
        Long bobsNotification = aNotificationOf(bob);
        String before = databaseSnapshot();

        mvc.perform(patch("/api/notifications/" + bobsNotification + "/read")
                        .header("Authorization", mallory.bearer()))
                .andExpect(status().isForbidden());

        assertEquals(before, databaseSnapshot());
    }

    @Test
    void markNotificationRead_byItsOwner_is204_andIsReadInTheDatabase() throws Exception {
        Long bobsNotification = aNotificationOf(bob);

        mvc.perform(patch("/api/notifications/" + bobsNotification + "/read")
                        .header("Authorization", bob.bearer()))
                .andExpect(status().isNoContent());

        assertTrue(jdbc.queryForObject("SELECT is_read FROM notification WHERE id = ?", Boolean.class, bobsNotification));
    }

    @Test
    void acceptOrDeclineSomeoneElsesInvite_is404_exactlyLikeAMissingInvite_andChangesNothing() throws Exception {
        Long carolsInvite = invite(alice, fixture.groupId(), carol);
        String before = databaseSnapshot();

        for (String action : List.of("accept", "decline")) {
            JsonNode notYours = body(mvc.perform(post("/api/invites/" + carolsInvite + "/" + action)
                    .header("Authorization", mallory.bearer())).andExpect(status().isNotFound()));
            JsonNode missing = body(mvc.perform(post("/api/invites/999999/" + action)
                    .header("Authorization", mallory.bearer())).andExpect(status().isNotFound()));
            assertEquals(missing, notYours, action + ": response reveals that the invite exists");
        }

        assertEquals(before, databaseSnapshot());
    }

    @Test
    void acceptingYourOwnInvite_turnsA403IntoAccess() throws Exception {
        mvc.perform(get("/api/groups/" + fixture.groupId()).header("Authorization", carol.bearer()))
                .andExpect(status().isForbidden());
        Long carolsInvite = invite(alice, fixture.groupId(), carol);

        mvc.perform(post("/api/invites/" + carolsInvite + "/accept").header("Authorization", carol.bearer()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/groups/" + fixture.groupId()).header("Authorization", carol.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void groupsAndNotifications_listOnlyTheCallersOwn() throws Exception {
        JsonNode mallorysGroups = body(mvc.perform(get("/api/groups").header("Authorization", mallory.bearer()))
                .andExpect(status().isOk()));
        JsonNode bobsGroups = body(mvc.perform(get("/api/groups").header("Authorization", bob.bearer()))
                .andExpect(status().isOk()));
        assertEquals(0, mallorysGroups.size());
        assertEquals(fixture.groupId(), bobsGroups.get(0).get("id").asLong());

        JsonNode mallorysNotifications = body(mvc.perform(get("/api/notifications")
                .header("Authorization", mallory.bearer())).andExpect(status().isOk()));
        JsonNode bobsNotifications = body(mvc.perform(get("/api/notifications")
                .header("Authorization", bob.bearer())).andExpect(status().isOk()));
        assertEquals(0, mallorysNotifications.size());
        assertFalse(bobsNotifications.isEmpty());
        bobsNotifications.forEach(n -> assertEquals(bob.id(), n.get("userId").asLong()));
    }

    @Test
    void pendingInvites_listOnlyTheCallersOwn() throws Exception {
        invite(alice, fixture.groupId(), carol);

        JsonNode carols = body(mvc.perform(get("/api/invites/mine").header("Authorization", carol.bearer()))
                .andExpect(status().isOk()));
        JsonNode mallorys = body(mvc.perform(get("/api/invites/mine").header("Authorization", mallory.bearer()))
                .andExpect(status().isOk()));

        assertEquals(1, carols.size());
        assertEquals(fixture.groupId(), carols.get(0).get("groupId").asLong());
        assertEquals(0, mallorys.size());
    }

    @Test
    void userSearch_findsOthers_butNeverTheSearcher() throws Exception {
        JsonNode results = body(mvc.perform(get("/api/users/search").param("q", "ali")
                .header("Authorization", alice.bearer())).andExpect(status().isOk()));
        assertEquals(0, results.size(), "alice searching 'ali' must not find herself");

        JsonNode found = body(mvc.perform(get("/api/users/search").param("q", "car")
                .header("Authorization", alice.bearer())).andExpect(status().isOk()));
        assertEquals(1, found.size());
        assertEquals("carol", found.get(0).get("username").asText());
    }
}

package com.settlegraph.Artifacts.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * JwtAuthFilter called directly with Spring's mock servlet objects and a real
 * JwtService — no application context. The filter only decides WHO the
 * request is; it never rejects. A bad token must leave the request
 * unauthenticated (so the security chain turns it away later), and the chain
 * must always continue.
 */
class JwtAuthFilterTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-0123456789";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtService);
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** Runs the filter with the given Authorization header (null = none); returns who it authenticated. */
    private Authentication filterWith(String authorizationHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/groups");
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response); // always continues, never short-circuits
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void validBearerToken_authenticatesTheRequestAsThatUser() throws Exception {
        Authentication auth = filterWith("Bearer " + jwtService.generateToken("alice@example.com", 7L));

        assertNotNull(auth);
        AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
        assertEquals(7L, user.getUserId());
        assertEquals("alice@example.com", user.getEmail());
    }

    @Test
    void noAuthorizationHeader_leavesTheRequestUnauthenticated() throws Exception {
        assertNull(filterWith(null));
    }

    @Test
    void expiredToken_leavesTheRequestUnauthenticated() throws Exception {
        String expired = new JwtService(SECRET, -1_000).generateToken("alice@example.com", 7L);
        assertNull(filterWith("Bearer " + expired));
    }

    @Test
    void malformedToken_leavesTheRequestUnauthenticated() throws Exception {
        assertNull(filterWith("Bearer not-a-jwt"));
    }

    @Test
    void tokenSignedWithTheWrongSecret_leavesTheRequestUnauthenticated() throws Exception {
        String forged = new JwtService("a-completely-different-secret-also-32-bytes-xyz", 60_000)
                .generateToken("alice@example.com", 7L);
        assertNull(filterWith("Bearer " + forged));
    }

    @Test
    void nonBearerScheme_isIgnored_evenWithAValidTokenInIt() throws Exception {
        String valid = jwtService.generateToken("alice@example.com", 7L);
        assertNull(filterWith("Basic " + valid));
    }
}

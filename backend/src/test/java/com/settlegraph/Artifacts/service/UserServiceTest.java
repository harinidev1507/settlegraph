package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.UserSearchResponse;
import com.settlegraph.Artifacts.entity.User;
import com.settlegraph.Artifacts.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * User search (used by the invite form). Repository mocked — no Spring
 * context or database.
 */
class UserServiceTest {

    private static final Long SEARCHER = 5L;

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
    }

    @Test
    void search_withFewerThanTwoCharactersAfterTrimming_returnsNothing_withoutQueryingTheDatabase() {
        assertTrue(userService.search(null, SEARCHER).isEmpty());
        assertTrue(userService.search("", SEARCHER).isEmpty());
        assertTrue(userService.search(" b  ", SEARCHER).isEmpty());

        verifyNoInteractions(userRepository);
    }

    @Test
    void search_trimsTheQuery_excludesTheSearcher_capsAtTenResults_andReturnsOnlyIdUsernameAndName() {
        User bob = spy(new User("bobby", "Bob", "bob@example.com", "hash"));
        doReturn(6L).when(bob).getId();
        when(userRepository.findByUsernameContainingIgnoreCaseAndIdNot(eq("bob"), eq(SEARCHER), any()))
                .thenReturn(new PageImpl<>(List.of(bob)));

        List<UserSearchResponse> results = userService.search("  bob ", SEARCHER);

        // The searcher is excluded and the page size is 10 — both enforced in the query itself.
        verify(userRepository).findByUsernameContainingIgnoreCaseAndIdNot("bob", SEARCHER, PageRequest.of(0, 10));
        assertEquals(1, results.size());
        assertEquals(6L, results.get(0).getUserId());
        assertEquals("bobby", results.get(0).getUsername());
        assertEquals("Bob", results.get(0).getName());
        // UserSearchResponse has no email field at all, so search can't leak one.
    }
}

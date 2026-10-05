package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.AuthResponse;
import com.settlegraph.Artifacts.dto.LoginRequest;
import com.settlegraph.Artifacts.dto.RegisterRequest;
import com.settlegraph.Artifacts.entity.User;
import com.settlegraph.Artifacts.repository.UserRepository;
import com.settlegraph.Artifacts.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Registration and login. The repository is mocked; the password encoder
 * (BCrypt) and JwtService are REAL, so these tests check the actual stored
 * hash and an actual verifiable token — not just that some method was called.
 * No Spring context or database.
 */
class AuthServiceTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-0123456789";
    private static final Long ALICE_ID = 7L;

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService(SECRET, 60_000);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    /** An existing user row with a database id and a real BCrypt hash of {@code password}. */
    private User existingAlice(String password) {
        User alice = spy(new User("alice", "Alice", "alice@example.com", passwordEncoder.encode(password)));
        doReturn(ALICE_ID).when(alice).getId();
        return alice;
    }

    private RegisterRequest registration(String username, String email) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setName("Alice");
        request.setEmail(email);
        request.setPassword("correct-horse");
        return request;
    }

    private LoginRequest login(String identifier, String password) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(identifier);
        request.setPassword(password);
        return request;
    }

    @Test
    void register_storesABcryptHashNotThePassword_lowercasesTheUsername_andReturnsAVerifiableToken() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = spy(inv.<User>getArgument(0));
            doReturn(ALICE_ID).when(saved).getId();
            return saved;
        });

        AuthResponse response = authService.register(registration("Alice", "alice@example.com"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("alice", saved.getValue().getUsername());
        assertNotEquals("correct-horse", saved.getValue().getPasswordHash(), "plaintext password stored");
        assertTrue(passwordEncoder.matches("correct-horse", saved.getValue().getPasswordHash()));

        assertEquals(ALICE_ID, response.getUserId());
        assertEquals("alice", response.getUsername());
        assertTrue(jwtService.isValid(response.getToken()));
        assertEquals(ALICE_ID, jwtService.extractUserId(response.getToken()));
        assertEquals("alice@example.com", jwtService.extractEmail(response.getToken()));
    }

    @Test
    void register_withAnEmailAlreadyInUse_isRejected_andNothingIsSaved() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> authService.register(registration("someone-new", "alice@example.com")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_withAUsernameTakenInADifferentCase_isRejected_andNothingIsSaved() {
        // "Alice" must collide with the existing "alice": usernames are stored lowercased.
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> authService.register(registration("Alice", "new@example.com")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_byEmail_withTheRightPassword_returnsATokenForThatUser() {
        User alice = existingAlice("correct-horse");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        AuthResponse response = authService.login(login("alice@example.com", "correct-horse"));

        assertEquals(ALICE_ID, response.getUserId());
        assertEquals(ALICE_ID, jwtService.extractUserId(response.getToken()));
    }

    @Test
    void login_byUsername_isCaseInsensitive_andTrimmed() {
        User alice = existingAlice("correct-horse");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        AuthResponse response = authService.login(login("  ALICE ", "correct-horse"));

        assertEquals(ALICE_ID, response.getUserId());
    }

    @Test
    void login_wrongPassword_andUnknownUser_failWithTheSameMessage_soAccountsCantBeEnumerated() {
        User alice = existingAlice("correct-horse");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        IllegalArgumentException wrongPassword = assertThrows(IllegalArgumentException.class,
                () -> authService.login(login("alice@example.com", "wrong-password")));
        IllegalArgumentException unknownUser = assertThrows(IllegalArgumentException.class,
                () -> authService.login(login("nobody@example.com", "correct-horse")));

        assertEquals(unknownUser.getMessage(), wrongPassword.getMessage());
    }
}

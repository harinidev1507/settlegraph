package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.dto.AuthResponse;
import com.settlegraph.Artifacts.dto.LoginRequest;
import com.settlegraph.Artifacts.dto.RegisterRequest;
import com.settlegraph.Artifacts.entity.User;
import com.settlegraph.Artifacts.repository.UserRepository;
import com.settlegraph.Artifacts.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        String username = request.getUsername().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("This username is already taken");
        }
        String hash = passwordEncoder.encode(request.getPassword());
        User user = new User(username, request.getName(), request.getEmail(), hash);
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getName());
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier.toLowerCase()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid email/username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email/username or password");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getName());
    }
}

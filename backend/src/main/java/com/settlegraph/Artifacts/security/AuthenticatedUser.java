package com.settlegraph.Artifacts.security;

// A small, simple stand-in for "who is making this request", extracted from
// the JWT by JwtAuthFilter. Controllers can grab this instead of re-parsing tokens.
public class AuthenticatedUser {
    private final Long userId;
    private final String email;

    public AuthenticatedUser(Long userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public Long getUserId() { return userId; }
    public String getEmail() { return email; }
}

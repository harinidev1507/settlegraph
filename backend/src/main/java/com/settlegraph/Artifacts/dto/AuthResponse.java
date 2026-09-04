package com.settlegraph.Artifacts.dto;

public class AuthResponse {
    private String token;
    private Long userId;
    private String username;
    private String name;

    public AuthResponse(String token, Long userId, String username, String name) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.name = name;
    }

    public String getToken() { return token; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getName() { return name; }
}

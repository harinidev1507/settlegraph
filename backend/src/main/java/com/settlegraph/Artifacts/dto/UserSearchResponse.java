package com.settlegraph.Artifacts.dto;

public class UserSearchResponse {
    private Long userId;
    private String username;
    private String name;

    public UserSearchResponse(Long userId, String username, String name) {
        this.userId = userId;
        this.username = username;
        this.name = name;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getName() { return name; }
}

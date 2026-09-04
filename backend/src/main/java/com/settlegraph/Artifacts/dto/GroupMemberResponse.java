package com.settlegraph.Artifacts.dto;

public class GroupMemberResponse {
    private Long userId;
    private String username;
    private String name;
    private String email;

    public GroupMemberResponse(Long userId, String username, String name, String email) {
        this.userId = userId;
        this.username = username;
        this.name = name;
        this.email = email;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}

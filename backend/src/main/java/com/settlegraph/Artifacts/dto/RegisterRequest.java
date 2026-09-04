package com.settlegraph.Artifacts.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_.]{3,30}$", message = "Username must be 3-30 characters: letters, numbers, underscore or dot")
    private String username;
    @NotBlank
    private String name;
    @Email @NotBlank
    private String email;
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

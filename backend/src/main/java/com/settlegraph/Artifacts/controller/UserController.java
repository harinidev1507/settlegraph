package com.settlegraph.Artifacts.controller;

import com.settlegraph.Artifacts.dto.UserSearchResponse;
import com.settlegraph.Artifacts.security.AuthenticatedUser;
import com.settlegraph.Artifacts.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> search(@RequestParam String q,
                                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(userService.search(q, user.getUserId()));
    }
}

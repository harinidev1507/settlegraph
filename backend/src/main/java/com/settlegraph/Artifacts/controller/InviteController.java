package com.settlegraph.Artifacts.controller;

import com.settlegraph.Artifacts.dto.GroupInviteResponse;
import com.settlegraph.Artifacts.security.AuthenticatedUser;
import com.settlegraph.Artifacts.service.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

    private final GroupService groupService;

    public InviteController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/mine")
    public ResponseEntity<List<GroupInviteResponse>> getMyInvites(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(groupService.getMyPendingInvites(user.getUserId()));
    }

    @PostMapping("/{inviteId}/accept")
    public ResponseEntity<GroupInviteResponse> accept(@PathVariable Long inviteId,
                                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(groupService.respondToInvite(inviteId, user.getUserId(), true));
    }

    @PostMapping("/{inviteId}/decline")
    public ResponseEntity<GroupInviteResponse> decline(@PathVariable Long inviteId,
                                                         @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(groupService.respondToInvite(inviteId, user.getUserId(), false));
    }
}

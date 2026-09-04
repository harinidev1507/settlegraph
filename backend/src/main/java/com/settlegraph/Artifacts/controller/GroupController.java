package com.settlegraph.Artifacts.controller;

import com.settlegraph.Artifacts.dto.CreateGroupRequest;
import com.settlegraph.Artifacts.dto.GroupInviteResponse;
import com.settlegraph.Artifacts.dto.GroupMemberResponse;
import com.settlegraph.Artifacts.dto.InviteRequest;
import com.settlegraph.Artifacts.entity.Group;
import com.settlegraph.Artifacts.security.AuthenticatedUser;
import com.settlegraph.Artifacts.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<Group> createGroup(@RequestBody CreateGroupRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(groupService.createGroup(request, user.getUserId()));
    }

    @GetMapping
    public ResponseEntity<List<Group>> getMyGroups(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(groupService.getGroupsForUser(user.getUserId()));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<Group> getGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getGroup(groupId));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberResponse>> getMembers(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getMembersWithDetails(groupId));
    }

    @PostMapping("/{groupId}/invites")
    public ResponseEntity<GroupInviteResponse> inviteMember(@PathVariable Long groupId,
                                                              @Valid @RequestBody InviteRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(groupService.inviteMember(groupId, user.getUserId(), request.getUsername()));
    }

    @GetMapping("/{groupId}/invites")
    public ResponseEntity<List<GroupInviteResponse>> getGroupInvites(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getInvitesForGroup(groupId));
    }
}

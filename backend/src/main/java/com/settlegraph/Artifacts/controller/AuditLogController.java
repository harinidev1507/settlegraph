package com.settlegraph.Artifacts.controller;

import com.settlegraph.Artifacts.entity.AuditLog;
import com.settlegraph.Artifacts.security.AuthenticatedUser;
import com.settlegraph.Artifacts.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/audit-log")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> get(@PathVariable Long groupId,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(auditLogService.getForGroup(groupId, user.getUserId()));
    }
}

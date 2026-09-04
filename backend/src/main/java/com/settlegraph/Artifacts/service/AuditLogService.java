package com.settlegraph.Artifacts.service;

import com.settlegraph.Artifacts.entity.AuditLog;
import com.settlegraph.Artifacts.repository.AuditLogRepository;
import com.settlegraph.Artifacts.security.GroupAccessGuard;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final GroupAccessGuard groupAccessGuard;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           GroupAccessGuard groupAccessGuard) {
        this.auditLogRepository = auditLogRepository;
        this.groupAccessGuard = groupAccessGuard;
    }

    public List<AuditLog> getForGroup(Long groupId, Long requestingUserId) {
        groupAccessGuard.requireMember(groupId, requestingUserId);
        return auditLogRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
    }
}

package com.settlegraph.Artifacts.repository;

import com.settlegraph.Artifacts.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByGroupIdOrderByCreatedAtDesc(Long groupId);
}

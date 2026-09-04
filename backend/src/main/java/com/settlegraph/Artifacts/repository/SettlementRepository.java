package com.settlegraph.Artifacts.repository;

import com.settlegraph.Artifacts.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findByGroupId(Long groupId);

    long deleteByGroupIdAndStatus(Long groupId, Settlement.Status status);
}

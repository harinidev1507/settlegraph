package com.settlegraph.Artifacts.repository;

import com.settlegraph.Artifacts.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findByGroupId(Long groupId);

    /** Scoped lookup: a settlement in another group is indistinguishable from a missing one. */
    Optional<Settlement> findByIdAndGroupId(Long id, Long groupId);

    long deleteByGroupIdAndStatus(Long groupId, Settlement.Status status);

    /**
     * PENDING -> PAID as a single conditional UPDATE. Returns rows changed:
     * 1 if this call did the transition, 0 if it was already PAID. Two
     * concurrent calls can't both get 1 — the second blocks on the row lock,
     * then re-checks {@code status = PENDING} and finds it no longer true.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Settlement s SET s.status = com.settlegraph.Artifacts.entity.Settlement.Status.PAID, "
            + "s.settledAt = :settledAt "
            + "WHERE s.id = :id AND s.status = com.settlegraph.Artifacts.entity.Settlement.Status.PENDING")
    int markPaidIfPending(@Param("id") Long id, @Param("settledAt") LocalDateTime settledAt);
}

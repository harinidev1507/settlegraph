package com.settlegraph.Artifacts.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement")
public class Settlement {

    public enum Status { PENDING, PAID }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    public Settlement() {}

    public Settlement(Long groupId, Long fromUserId, Long toUserId, BigDecimal amount) {
        this.groupId = groupId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getFromUserId() { return fromUserId; }
    public Long getToUserId() { return toUserId; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public void markPaid() { this.status = Status.PAID; this.settledAt = LocalDateTime.now(); }
    public LocalDateTime getSettledAt() { return settledAt; }
}

package com.settlegraph.Artifacts.dto;

import java.math.BigDecimal;

public class SettlementDto {
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private BigDecimal amount;
    private String status;

    public SettlementDto(Long id, Long fromUserId, Long toUserId, BigDecimal amount, String status) {
        this.id = id;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getFromUserId() { return fromUserId; }
    public Long getToUserId() { return toUserId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
}

package com.settlegraph.Artifacts.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class CreateExpenseRequest {
    private Long groupId;
    private BigDecimal amount;
    private String currency = "INR";
    private String category;
    private String description;
    private String splitType; // EQUAL, EXACT, PERCENTAGE, SHARES
    private Map<Long, BigDecimal> splits; // userId -> value (meaning depends on splitType)
    private List<Long> participantIds; // who shares an EQUAL split — caller must pass this explicitly

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSplitType() { return splitType; }
    public void setSplitType(String splitType) { this.splitType = splitType; }
    public Map<Long, BigDecimal> getSplits() { return splits; }
    public void setSplits(Map<Long, BigDecimal> splits) { this.splits = splits; }
    public List<Long> getParticipantIds() { return participantIds; }
    public void setParticipantIds(List<Long> participantIds) { this.participantIds = participantIds; }
}

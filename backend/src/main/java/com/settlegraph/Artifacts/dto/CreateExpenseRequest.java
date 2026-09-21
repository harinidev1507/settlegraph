package com.settlegraph.Artifacts.dto;

import com.settlegraph.Artifacts.entity.RecurrenceFrequency;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class CreateExpenseRequest {
    @NotNull
    private Long groupId;
    @NotNull @Positive
    @Digits(integer = 10, fraction = 2, message = "amount can have at most 2 decimal places")
    private BigDecimal amount;
    @NotBlank @Size(max = 10)
    private String currency = "INR";
    @Size(max = 100)
    private String category;
    @NotBlank @Size(max = 255)
    private String description;
    private String splitType; // EQUAL, EXACT, PERCENTAGE, SHARES
    private Map<Long, BigDecimal> splits; // userId -> value (meaning depends on splitType)
    private List<Long> participantIds; // who shares an EQUAL split — caller must pass this explicitly
    private boolean recurring = false; // true = this expense is also a template that repeats
    private RecurrenceFrequency recurrenceFrequency; // required when recurring; MONTHLY is the only option today

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
    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }
    public RecurrenceFrequency getRecurrenceFrequency() { return recurrenceFrequency; }
    public void setRecurrenceFrequency(RecurrenceFrequency recurrenceFrequency) { this.recurrenceFrequency = recurrenceFrequency; }
}

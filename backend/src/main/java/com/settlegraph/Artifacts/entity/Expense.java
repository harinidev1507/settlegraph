package com.settlegraph.Artifacts.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "paid_by", nullable = false)
    private Long paidBy;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency = "INR";

    private String category;
    private String description;

    @Column(name = "expense_date")
    private LocalDateTime expenseDate = LocalDateTime.now();

    @Column(name = "is_recurring")
    private boolean recurring = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_frequency")
    private RecurrenceFrequency recurrenceFrequency;

    @Column(name = "recurring_source_id")
    private Long recurringSourceId;

    @Column(name = "recurrence_period")
    private String recurrencePeriod;

    public Expense() {}

    public Expense(Long groupId, Long paidBy, BigDecimal amount, String currency, String category, String description) {
        this.groupId = groupId;
        this.paidBy = paidBy;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.description = description;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getPaidBy() { return paidBy; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public LocalDateTime getExpenseDate() { return expenseDate; }
    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }
    public RecurrenceFrequency getRecurrenceFrequency() { return recurrenceFrequency; }
    public void setRecurrenceFrequency(RecurrenceFrequency recurrenceFrequency) { this.recurrenceFrequency = recurrenceFrequency; }
    public Long getRecurringSourceId() { return recurringSourceId; }
    public void setRecurringSourceId(Long recurringSourceId) { this.recurringSourceId = recurringSourceId; }
    public String getRecurrencePeriod() { return recurrencePeriod; }
    public void setRecurrencePeriod(String recurrencePeriod) { this.recurrencePeriod = recurrencePeriod; }
}

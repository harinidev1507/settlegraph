package com.settlegraph.Artifacts.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "expense_split")
public class ExpenseSplit {

    @EmbeddedId
    private ExpenseSplitId id;

    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;

    public ExpenseSplit() {}

    public ExpenseSplit(Long expenseId, Long userId, BigDecimal shareAmount) {
        this.id = new ExpenseSplitId(expenseId, userId);
        this.shareAmount = shareAmount;
    }

    public ExpenseSplitId getId() { return id; }
    public BigDecimal getShareAmount() { return shareAmount; }

    @Embeddable
    public static class ExpenseSplitId implements Serializable {
        @Column(name = "expense_id")
        private Long expenseId;
        @Column(name = "user_id")
        private Long userId;

        public ExpenseSplitId() {}
        public ExpenseSplitId(Long expenseId, Long userId) {
            this.expenseId = expenseId;
            this.userId = userId;
        }
        public Long getExpenseId() { return expenseId; }
        public Long getUserId() { return userId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExpenseSplitId)) return false;
            ExpenseSplitId that = (ExpenseSplitId) o;
            return Objects.equals(expenseId, that.expenseId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() { return Objects.hash(expenseId, userId); }
    }
}

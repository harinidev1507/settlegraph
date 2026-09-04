package com.settlegraph.Artifacts.repository;

import com.settlegraph.Artifacts.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByGroupIdOrderByExpenseDateDesc(Long groupId);
}

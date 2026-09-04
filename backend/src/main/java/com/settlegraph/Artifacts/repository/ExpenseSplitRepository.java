package com.settlegraph.Artifacts.repository;

import com.settlegraph.Artifacts.entity.ExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseSplitRepository extends JpaRepository<ExpenseSplit, ExpenseSplit.ExpenseSplitId> {
    List<ExpenseSplit> findByIdExpenseId(Long expenseId);
}

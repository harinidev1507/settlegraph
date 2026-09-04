package com.settlegraph.Artifacts.controller;

import com.settlegraph.Artifacts.dto.CreateExpenseRequest;
import com.settlegraph.Artifacts.entity.Expense;
import com.settlegraph.Artifacts.security.AuthenticatedUser;
import com.settlegraph.Artifacts.service.ExpenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @PostMapping
    public ResponseEntity<Expense> addExpense(@RequestBody CreateExpenseRequest request,
                                               @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(expenseService.addExpense(request, user.getUserId()));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<Expense>> getExpensesForGroup(@PathVariable Long groupId,
                                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(expenseService.getExpensesForGroup(groupId, user.getUserId()));
    }
}

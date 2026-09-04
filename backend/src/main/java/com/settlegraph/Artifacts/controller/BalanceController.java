package com.settlegraph.Artifacts.controller;

import com.settlegraph.Artifacts.security.AuthenticatedUser;
import com.settlegraph.Artifacts.service.BalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/groups/{groupId}/balances")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping
    public ResponseEntity<Map<Long, BigDecimal>> getBalances(@PathVariable Long groupId,
                                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(balanceService.getNetBalances(groupId, user.getUserId()));
    }
}

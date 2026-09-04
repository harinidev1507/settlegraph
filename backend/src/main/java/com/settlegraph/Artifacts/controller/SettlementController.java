package com.settlegraph.Artifacts.controller;

import com.settlegraph.Artifacts.entity.Settlement;
import com.settlegraph.Artifacts.security.AuthenticatedUser;
import com.settlegraph.Artifacts.service.SettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    // This is the "Settle Up" button — triggers the debt-simplification algorithm.
    @PostMapping("/generate")
    public ResponseEntity<List<Settlement>> generate(@PathVariable Long groupId,
                                                       @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(settlementService.generateSettlementPlan(groupId, user.getUserId()));
    }

    @GetMapping
    public ResponseEntity<List<Settlement>> list(@PathVariable Long groupId,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(settlementService.getSettlementsForGroup(groupId, user.getUserId()));
    }

    @PatchMapping("/{settlementId}/mark-paid")
    public ResponseEntity<Settlement> markPaid(@PathVariable Long groupId,
                                                @PathVariable Long settlementId,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(settlementService.markPaid(groupId, settlementId, user.getUserId()));
    }
}

package com.settlegraph.Artifacts.controller;

import com.settlegraph.Artifacts.security.AuthenticatedUser;
import com.settlegraph.Artifacts.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/groups/{groupId}/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/by-category")
    public ResponseEntity<Map<String, BigDecimal>> byCategory(@PathVariable Long groupId,
                                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(analyticsService.spendByCategory(groupId, user.getUserId()));
    }

    @GetMapping("/by-month")
    public ResponseEntity<Map<String, BigDecimal>> byMonth(@PathVariable Long groupId,
                                                           @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(analyticsService.spendByMonth(groupId, user.getUserId()));
    }
}

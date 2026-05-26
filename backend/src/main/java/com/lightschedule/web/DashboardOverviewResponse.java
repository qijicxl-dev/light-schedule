package com.lightschedule.web;

import java.util.Map;

public record DashboardOverviewResponse(
        CapacitySummary capacitySummary,
        WorkOrderStats workOrderStats,
        ResourceStats resourceStats) {

    public record CapacitySummary(String status, double loadRate) {
    }

    public record WorkOrderStats(int total, int urgentCount, Map<String, Long> riskDistribution) {
    }

    public record ResourceStats(int total, int defaultPlannerCount) {
    }
}

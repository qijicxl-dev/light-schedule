package com.lightschedule.web;

import com.lightschedule.modules.capacity.CapacityAnalysisOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/capacity-analysis")
public class CapacityAnalysisController {

    private final CapacityAnalysisOverviewService capacityAnalysisOverviewService;

    public CapacityAnalysisController(CapacityAnalysisOverviewService capacityAnalysisOverviewService) {
        this.capacityAnalysisOverviewService = capacityAnalysisOverviewService;
    }

    @GetMapping("/overview")
    public CapacityAnalysisOverviewResponse overview() {
        var overview = capacityAnalysisOverviewService.buildOverview();

        // 总览计算下沉到 service，controller 只保留 HTTP DTO 映射职责。
        return new CapacityAnalysisOverviewResponse(
                overview.trends().stream()
                        .map(trend -> new CapacityTrendItemResponse(
                                trend.resourceId(),
                                trend.bucketLabel(),
                                trend.status(),
                                trend.loadRate()))
                        .toList(),
                overview.groupDiffs().stream()
                        .map(groupDiff -> new CapacityGroupDiffItemResponse(
                                groupDiff.groupName(),
                                groupDiff.gapRate()))
                        .toList(),
                overview.peakPeriods().stream()
                        .map(peakPeriod -> new CapacityPeakPeriodItemResponse(
                                peakPeriod.bucketLabel(),
                                peakPeriod.status(),
                                peakPeriod.loadRate()))
                        .toList());
    }
}

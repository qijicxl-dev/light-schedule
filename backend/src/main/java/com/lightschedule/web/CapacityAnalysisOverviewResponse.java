package com.lightschedule.web;

import java.util.List;

// 能力分析总览接口返回的 Web DTO。
public record CapacityAnalysisOverviewResponse(
        List<CapacityTrendItemResponse> trends,
        List<CapacityGroupDiffItemResponse> groupDiffs,
        List<CapacityPeakPeriodItemResponse> peakPeriods
) {
}

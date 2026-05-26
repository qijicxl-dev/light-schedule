package com.lightschedule.web;

// 产能峰值时段项接口返回的 Web DTO。
public record CapacityPeakPeriodItemResponse(
        String bucketLabel,
        String status,
        double loadRate
) {
}

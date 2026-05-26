package com.lightschedule.web;

// 能力趋势项接口返回的 Web DTO。
public record CapacityTrendItemResponse(
        String resourceId,
        String bucketLabel,
        String status,
        double loadRate
) {
}

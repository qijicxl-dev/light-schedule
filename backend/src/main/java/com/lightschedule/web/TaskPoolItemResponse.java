package com.lightschedule.web;

// 任务池接口返回的 Web DTO。
public record TaskPoolItemResponse(
        String workOrderCode,
        String dueAt,
        boolean urgent,
        String materialRisk,
        String readiness
) {
}

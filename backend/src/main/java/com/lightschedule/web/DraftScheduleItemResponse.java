package com.lightschedule.web;

// 初排结果项接口返回的 Web DTO。
public record DraftScheduleItemResponse(
        String taskId,
        String resourceId,
        String resourceGroupName,
        String startAt,
        String endAt
) {
}

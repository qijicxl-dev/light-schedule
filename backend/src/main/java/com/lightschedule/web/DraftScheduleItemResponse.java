package com.lightschedule.web;

import java.util.List;

// 初排结果项接口返回的 Web DTO。
public record DraftScheduleItemResponse(
        String taskId,
        String resourceId,
        String resourceGroupName,
        String startAt,
        String endAt,
        List<String> dependencyTaskIds
) {
}

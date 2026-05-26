package com.lightschedule.web;

import java.util.List;

public record ReplanScheduledItemRequest(
        String taskId,
        String resourceId,
        String startAt,
        String endAt,
        List<String> dependencyTaskIds) {
}

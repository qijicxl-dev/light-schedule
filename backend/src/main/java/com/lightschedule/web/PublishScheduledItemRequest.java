package com.lightschedule.web;

import java.util.List;

public record PublishScheduledItemRequest(
        String taskId,
        String resourceId,
        String startAt,
        String endAt,
        List<String> dependencyTaskIds) {
}

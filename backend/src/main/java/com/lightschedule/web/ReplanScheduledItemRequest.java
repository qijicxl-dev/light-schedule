package com.lightschedule.web;

public record ReplanScheduledItemRequest(
        String taskId,
        String resourceId,
        String startAt,
        String endAt) {
}

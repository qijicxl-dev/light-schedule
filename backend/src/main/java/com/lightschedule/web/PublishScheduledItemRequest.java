package com.lightschedule.web;

public record PublishScheduledItemRequest(
        String taskId,
        String resourceId,
        String startAt,
        String endAt) {
}

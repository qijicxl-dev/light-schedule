package com.lightschedule.web;

public record ResourceSaveRequest(
        String resourceId,
        String groupName,
        boolean defaultPlanner
) {
}

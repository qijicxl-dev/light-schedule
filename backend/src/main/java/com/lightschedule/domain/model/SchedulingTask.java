package com.lightschedule.domain.model;

public record SchedulingTask(
        String workOrderCode,
        String readiness,
        String materialRisk
) {
}

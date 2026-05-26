package com.lightschedule.domain.model;

public record ResourceUnit(
        String resourceCode,
        int availableMinutes,
        int usedMinutes
) {
}

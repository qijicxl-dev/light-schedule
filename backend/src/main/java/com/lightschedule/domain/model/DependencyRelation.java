package com.lightschedule.domain.model;

public record DependencyRelation(
        String parentWorkOrderCode,
        String childWorkOrderCode
) {
}

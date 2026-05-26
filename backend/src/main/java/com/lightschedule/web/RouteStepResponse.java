package com.lightschedule.web;

import java.util.List;

public record RouteStepResponse(
        String stepId,
        int requiredMinutes,
        List<String> dependencyStepIds
) {
}

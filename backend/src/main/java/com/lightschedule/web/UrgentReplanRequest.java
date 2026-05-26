package com.lightschedule.web;

import java.util.List;

public record UrgentReplanRequest(
        String urgentTaskId,
        String urgentResourceId,
        String urgentStartAt,
        String urgentEndAt,
        List<ReplanScheduledItemRequest> items) {
}

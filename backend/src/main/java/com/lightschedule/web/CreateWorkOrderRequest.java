package com.lightschedule.web;

import java.util.List;

public record CreateWorkOrderRequest(
        String workOrderCode,
        String status,
        int quantity,
        String dueAt,
        String routeId,
        boolean urgent,
        List<String> parentWorkOrderCodes,
        String materialRisk
) {
}

package com.lightschedule.domain.model;

import java.util.List;

public record WorkOrder(
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

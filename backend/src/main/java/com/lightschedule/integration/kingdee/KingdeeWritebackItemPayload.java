package com.lightschedule.integration.kingdee;

public record KingdeeWritebackItemPayload(
        String workOrderCode,
        String resourceId,
        String plannedStartAt,
        String plannedEndAt,
        String sequenceNo) {
}

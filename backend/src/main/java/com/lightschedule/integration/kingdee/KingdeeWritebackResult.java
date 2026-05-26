package com.lightschedule.integration.kingdee;

public record KingdeeWritebackResult(boolean success, String externalRequestId, String message) {
}

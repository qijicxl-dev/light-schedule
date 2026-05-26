package com.lightschedule.integration.kingdee;

public record KingdeeWritebackStatusResult(boolean completed, boolean success, String message) {
}

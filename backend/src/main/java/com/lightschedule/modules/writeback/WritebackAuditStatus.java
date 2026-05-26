package com.lightschedule.modules.writeback;

public enum WritebackAuditStatus {
    QUEUED,
    SUBMITTED,
    SUCCEEDED,
    RETRYABLE_FAILED,
    TERMINAL_FAILED
}

package com.lightschedule.modules.writeback;

public record WritebackAttemptDecision(boolean retryable, String nextStatus) {

    public static WritebackAttemptDecision succeeded() {
        return new WritebackAttemptDecision(false, WritebackAuditStatus.SUCCEEDED.name());
    }

    public static WritebackAttemptDecision retryLater() {
        return new WritebackAttemptDecision(true, WritebackAuditStatus.RETRYABLE_FAILED.name());
    }

    public static WritebackAttemptDecision terminalFailure() {
        return new WritebackAttemptDecision(false, WritebackAuditStatus.TERMINAL_FAILED.name());
    }
}

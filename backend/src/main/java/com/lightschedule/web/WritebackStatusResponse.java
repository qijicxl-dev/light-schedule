package com.lightschedule.web;

public record WritebackStatusResponse(
        String auditId,
        String draftId,
        String status,
        String writebackStatus,
        String message,
        boolean retryable,
        int attemptCount,
        int maxAttempts,
        String nextRetryAt) {
}

package com.lightschedule.web;

public record PublishResponse(String draftId, String auditId, String status, String writebackStatus) {
}

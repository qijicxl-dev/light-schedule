package com.lightschedule.domain.model;

public record ScheduleDraft(
        String id,
        int versionNo,
        String status,
        String draftPayload
) {
}

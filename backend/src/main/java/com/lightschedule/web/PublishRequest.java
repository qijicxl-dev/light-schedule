package com.lightschedule.web;

import java.util.List;

public record PublishRequest(String draftId, List<PublishScheduledItemRequest> items) {
}

package com.lightschedule.web;

import java.util.List;

// 急单重排接口返回的 Web DTO。
public record UrgentReplanResponse(
        String urgentTaskId,
        List<String> affectedTaskIds,
        List<UrgentReplanSuggestionResponse> suggestions
) {
}

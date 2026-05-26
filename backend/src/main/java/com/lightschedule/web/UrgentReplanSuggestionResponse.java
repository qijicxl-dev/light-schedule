package com.lightschedule.web;

// 急单重排建议项接口返回的 Web DTO。
public record UrgentReplanSuggestionResponse(
        String action,
        String reason
) {
}

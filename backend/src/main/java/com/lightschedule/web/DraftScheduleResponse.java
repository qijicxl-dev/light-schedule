package com.lightschedule.web;

import java.util.List;

// 初排接口返回的 Web DTO。
public record DraftScheduleResponse(String draftId, List<DraftScheduleItemResponse> items) {
}

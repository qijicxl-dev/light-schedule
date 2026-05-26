package com.lightschedule.web;

// 资源组能力差异项接口返回的 Web DTO。
public record CapacityGroupDiffItemResponse(
        String groupName,
        double gapRate
) {
}

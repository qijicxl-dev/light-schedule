package com.lightschedule.modules.replan;

import com.lightschedule.modules.resource.ResourceGroupService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SuggestionService {

    private final ResourceGroupService resourceGroupService;

    public SuggestionService(ResourceGroupService resourceGroupService) {
        this.resourceGroupService = resourceGroupService;
    }

    public List<Suggestion> build(List<String> overloadedResourceIds) {
        if (overloadedResourceIds.isEmpty()) {
            return List.of();
        }
        if (overloadedResourceIds.size() == 1) {
            String groupName = resourceGroupService.getGroupName(overloadedResourceIds.getFirst());
            // 单资源拥塞先优先尝试组内挪配，避免过早升级到顺延或人工加班。
            return List.of(new Suggestion("reassign_same_group", groupName + "仍有剩余能力"));
        }
        return List.of(
                new Suggestion("move_next_slot", "下一可用时间窗可承接"),
                new Suggestion("manual_overtime_review", "只有在必须保急单交期时才人工确认加班")
        );
    }

    public record Suggestion(String action, String reason) {
    }
}

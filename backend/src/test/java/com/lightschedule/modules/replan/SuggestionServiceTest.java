package com.lightschedule.modules.replan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lightschedule.modules.resource.ResourceCatalogService;
import com.lightschedule.modules.resource.ResourceGroupService;
import com.lightschedule.modules.resource.ResourceMapper;
import com.lightschedule.modules.resource.ResourceEntity;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuggestionServiceTest {

    private static ResourceCatalogService catalogWithMockData() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        ResourceEntity entityA = new ResourceEntity();
        entityA.setResourceId("LINE-A");
        entityA.setGroupName("冲压组");
        entityA.setDefaultPlanner(true);
        ResourceEntity entityB = new ResourceEntity();
        entityB.setResourceId("LINE-B");
        entityB.setGroupName("冲压组");
        entityB.setDefaultPlanner(true);
        when(mapper.selectList(null)).thenReturn(List.of(entityA, entityB));
        return new ResourceCatalogService(mapper);
    }

    @Test
    void shouldReturnSameGroupSuggestionWithResolvedGroupNameForSingleOverloadedResource() {
        SuggestionService service = new SuggestionService(new ResourceGroupService(catalogWithMockData()));

        var suggestions = service.build(java.util.List.of("LINE-A"));

        assertThat(suggestions)
                .extracting(SuggestionService.Suggestion::action)
                .containsExactly("reassign_same_group");
        assertThat(suggestions)
                .extracting(SuggestionService.Suggestion::reason)
                .containsExactly("冲压组仍有剩余能力");
    }

    @Test
    void shouldEscalateToNextSlotAndManualReviewForMultipleOverloadedResources() {
        SuggestionService service = new SuggestionService(new ResourceGroupService(catalogWithMockData()));

        var suggestions = service.build(java.util.List.of("LINE-A", "LINE-B"));

        assertThat(suggestions)
                .extracting(SuggestionService.Suggestion::action)
                .containsExactly("move_next_slot", "manual_overtime_review");
    }
}

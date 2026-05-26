package com.lightschedule.modules.replan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lightschedule.modules.resource.ResourceCatalogService;
import com.lightschedule.modules.resource.ResourceEntity;
import com.lightschedule.modules.resource.ResourceGroupService;
import com.lightschedule.modules.resource.ResourceMapper;
import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import com.lightschedule.modules.scheduling.PlannerScenarioService;
import java.util.List;
import org.junit.jupiter.api.Test;

class UrgentReplanServiceTest {

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
    void shouldReturnSameGroupSuggestionForSingleOverloadedResource() {
        UrgentReplanService service = new UrgentReplanService(
                new SuggestionService(new ResourceGroupService(catalogWithMockData())),
                new PlannerScenarioService(catalogWithMockData(), null, null));

        var result = service.replan("WO-URGENT-001", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z"),
                new ScheduledItem("TASK-002", "LINE-A", "2026-04-24T09:30:00Z", "2026-04-24T11:30:00Z"),
                new ScheduledItem("TASK-003", "LINE-B", "2026-04-24T08:30:00Z", "2026-04-24T09:30:00Z")
        ));

        assertThat(result.affectedTaskIds()).containsExactly("TASK-001", "TASK-002");
        assertThat(result.suggestions().stream().map(SuggestionService.Suggestion::action))
                .containsExactly("reassign_same_group");
        assertThat(result.suggestions().stream().map(SuggestionService.Suggestion::reason))
                .containsExactly("冲压组仍有剩余能力");
    }

    @Test
    void shouldNotSuggestReplanWhenNoResourceIsRepeated() {
        UrgentReplanService service = new UrgentReplanService(
                new SuggestionService(new ResourceGroupService(catalogWithMockData())),
                new PlannerScenarioService(catalogWithMockData(), null, null));

        var result = service.replan("WO-URGENT-001", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z"),
                new ScheduledItem("TASK-002", "LINE-B", "2026-04-24T08:30:00Z", "2026-04-24T09:30:00Z")
        ));

        assertThat(result.affectedTaskIds()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void shouldNotTreatSequentialTasksOnSameResourceAsOverloaded() {
        UrgentReplanService service = new UrgentReplanService(
                new SuggestionService(new ResourceGroupService(catalogWithMockData())),
                new PlannerScenarioService(catalogWithMockData(), null, null));

        var result = service.replan("WO-URGENT-001", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z"),
                new ScheduledItem("TASK-002", "LINE-A", "2026-04-24T10:00:00Z", "2026-04-24T11:30:00Z"),
                new ScheduledItem("TASK-003", "LINE-B", "2026-04-24T08:30:00Z", "2026-04-24T09:30:00Z")
        ));

        assertThat(result.affectedTaskIds()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
    }

    @Test
    void shouldIncludeDependentTasksWhenUpstreamTaskIsDirectlyImpacted() {
        UrgentReplanService service = new UrgentReplanService(
                new SuggestionService(new ResourceGroupService(catalogWithMockData())),
                new PlannerScenarioService(catalogWithMockData(), null, null));

        var result = service.replan("WO-URGENT-001", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z"),
                new ScheduledItem("TASK-URGENT", "LINE-A", "2026-04-24T09:30:00Z", "2026-04-24T10:30:00Z"),
                new ScheduledItem("TASK-002", "LINE-B", "2026-04-24T10:30:00Z", "2026-04-24T12:00:00Z")
        ));

        assertThat(result.affectedTaskIds()).containsExactly("TASK-001", "TASK-URGENT", "TASK-002");
    }

    @Test
    void shouldEscalateSuggestionsWhenMultipleResourcesAreOverloaded() {
        UrgentReplanService service = new UrgentReplanService(
                new SuggestionService(new ResourceGroupService(catalogWithMockData())),
                new PlannerScenarioService(catalogWithMockData(), null, null));

        var result = service.replan("WO-URGENT-001", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z"),
                new ScheduledItem("TASK-002", "LINE-A", "2026-04-24T09:30:00Z", "2026-04-24T11:30:00Z"),
                new ScheduledItem("TASK-003", "LINE-B", "2026-04-24T08:30:00Z", "2026-04-24T09:30:00Z"),
                new ScheduledItem("TASK-004", "LINE-B", "2026-04-24T09:00:00Z", "2026-04-24T10:30:00Z")
        ));

        assertThat(result.suggestions().stream().map(SuggestionService.Suggestion::action))
                .containsExactly("move_next_slot", "manual_overtime_review");
    }
}

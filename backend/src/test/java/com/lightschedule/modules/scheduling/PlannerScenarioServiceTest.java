package com.lightschedule.modules.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lightschedule.modules.resource.ResourceCatalogService;
import com.lightschedule.modules.resource.ResourceEntity;
import com.lightschedule.modules.resource.ResourceMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannerScenarioServiceTest {

    private static ResourceCatalogService catalogWithMockData() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        ResourceEntity entityA = new ResourceEntity();
        entityA.setResourceId("LINE-A");
        entityA.setGroupName("冲压组");
        entityA.setDefaultPlanner(true);
        when(mapper.selectList(null)).thenReturn(List.of(entityA));
        return new ResourceCatalogService(mapper);
    }

    @Test
    void shouldProvideSharedPlannerScenarioForTaskPoolAndDraftSchedule() {
        PlannerScenarioService service = new PlannerScenarioService(catalogWithMockData(), null, null);

        PlannerScenarioService.PlannerScenario scenario = service.loadDefaultScenario();

        assertThat(scenario.workOrders()).containsExactly(
                new PlannerScenarioService.PlannerWorkOrder(
                        "WO-001",
                        "released",
                        20,
                        "2026-04-24T08:00:00Z",
                        "ROUTE-01",
                        false,
                        List.of(),
                        "low"));
        assertThat(scenario.tasks()).containsExactly(
                new PlannerScenarioService.PlannerTask("TASK-001", "LINE-A", 120, List.of()),
                new PlannerScenarioService.PlannerTask("TASK-002", "LINE-A", 90, List.of("TASK-001")));
        assertThat(scenario.startAt()).isEqualTo("2026-04-24T08:00:00Z");
        assertThat(scenario.draftId()).isEqualTo("draft-1");
    }
}

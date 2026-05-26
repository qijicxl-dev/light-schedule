package com.lightschedule.modules.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lightschedule.modules.resource.ResourceCatalogService;
import com.lightschedule.modules.resource.ResourceEntity;
import com.lightschedule.modules.resource.ResourceGroupService;
import com.lightschedule.modules.resource.ResourceMapper;
import com.lightschedule.domain.model.WorkOrder;
import com.lightschedule.modules.scheduling.InitialSchedulingService;
import com.lightschedule.modules.scheduling.PlannerScenarioService;
import com.lightschedule.modules.scheduling.RouteStepService;
import com.lightschedule.modules.taskpool.WorkOrderService;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapacityAnalysisOverviewServiceTest {

    private static WorkOrderService workOrderServiceWithMockData() {
        WorkOrderService service = mock(WorkOrderService.class);
        when(service.loadAll()).thenReturn(List.of(
                new WorkOrder("WO-001", "released", 20, "2026-04-24T08:00:00Z", "ROUTE-01", false, List.of(), "low")));
        return service;
    }

    private static RouteStepService routeStepServiceWithMockData() {
        RouteStepService service = mock(RouteStepService.class);
        when(service.loadTasksForRouteIds(List.of("ROUTE-01"), "LINE-A")).thenReturn(List.of(
                new PlannerScenarioService.PlannerTask("TASK-001", "LINE-A", 120, List.of()),
                new PlannerScenarioService.PlannerTask("TASK-002", "LINE-A", 90, List.of("TASK-001"))));
        return service;
    }

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
    void shouldBuildOverviewFromDefaultPlannerScenario() {
        ResourceCatalogService resourceCatalogService = catalogWithMockData();
        CapacityAnalysisOverviewService service =
                new CapacityAnalysisOverviewService(
                        new CapacityAssessmentService(),
                        new CapacityBucketService(),
                        new ResourceGroupService(resourceCatalogService),
                        resourceCatalogService,
                        new PlannerScenarioService(resourceCatalogService, workOrderServiceWithMockData(), routeStepServiceWithMockData()),
                        new InitialSchedulingService());

        CapacityAnalysisOverviewService.CapacityAnalysisOverview overview = service.buildOverview();

        assertThat(overview.trends()).containsExactly(new CapacityAnalysisOverviewService.CapacityTrend(
                "LINE-A",
                "2026-04-24 08:00",
                "feasible",
                0.44));
        assertThat(overview.groupDiffs()).containsExactly(new CapacityAnalysisOverviewService.CapacityGroupDiff(
                "冲压组",
                0.44));
        assertThat(overview.peakPeriods()).containsExactly(new CapacityAnalysisOverviewService.CapacityPeakPeriod(
                "2026-04-24 08:00",
                "tight",
                1.0));
    }
}

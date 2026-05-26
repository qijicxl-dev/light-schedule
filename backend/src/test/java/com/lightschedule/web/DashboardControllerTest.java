package com.lightschedule.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lightschedule.domain.model.WorkOrder;
import com.lightschedule.modules.capacity.CapacityAssessmentService;
import com.lightschedule.modules.resource.ResourceCatalogService;
import com.lightschedule.modules.scheduling.PlannerScenarioService;
import com.lightschedule.modules.taskpool.WorkOrderService;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardControllerTest {

    @Test
    void shouldReturnOverviewWithCapacityWorkOrderAndResourceStats() {
        PlannerScenarioService plannerScenarioService = mock(PlannerScenarioService.class);
        CapacityAssessmentService capacityAssessmentService = mock(CapacityAssessmentService.class);
        WorkOrderService workOrderService = mock(WorkOrderService.class);
        ResourceCatalogService resourceCatalogService = mock(ResourceCatalogService.class);

        when(plannerScenarioService.loadDefaultScenario()).thenReturn(
                new PlannerScenarioService.PlannerScenario(
                        List.of(),
                        List.of(
                                new PlannerScenarioService.PlannerTask("TASK-001", "LINE-A", 120, List.of()),
                                new PlannerScenarioService.PlannerTask("TASK-002", "LINE-A", 90, List.of("TASK-001"))),
                        "2026-04-24T08:00:00Z",
                        "draft-1"));
        when(capacityAssessmentService.assessCoarse(210, 480))
                .thenReturn(new CapacityAssessmentService.AssessmentResult("feasible", 0.44));
        when(workOrderService.loadAll()).thenReturn(List.of(
                new WorkOrder("WO-001", "released", 20, "2026-04-24T08:00:00Z", "ROUTE-01", false, List.of(), "low"),
                new WorkOrder("WO-002", "released", 10, "2026-04-25T08:00:00Z", "ROUTE-01", true, List.of(), "high")));
        when(resourceCatalogService.list()).thenReturn(List.of(
                new ResourceCatalogService.ResourceDefinition("LINE-A", "冲压组", true),
                new ResourceCatalogService.ResourceDefinition("LINE-B", "冲压组", true),
                new ResourceCatalogService.ResourceDefinition("LINE-C", "装配组", false)));

        DashboardController controller = new DashboardController(
                plannerScenarioService, capacityAssessmentService, workOrderService, resourceCatalogService);
        DashboardOverviewResponse overview = controller.overview();

        assertThat(overview.capacitySummary().status()).isEqualTo("feasible");
        assertThat(overview.capacitySummary().loadRate()).isEqualTo(0.44);
        assertThat(overview.workOrderStats().total()).isEqualTo(2);
        assertThat(overview.workOrderStats().urgentCount()).isEqualTo(1);
        assertThat(overview.workOrderStats().riskDistribution()).containsEntry("low", 1L).containsEntry("high", 1L);
        assertThat(overview.resourceStats().total()).isEqualTo(3);
        assertThat(overview.resourceStats().defaultPlannerCount()).isEqualTo(2);
    }
}

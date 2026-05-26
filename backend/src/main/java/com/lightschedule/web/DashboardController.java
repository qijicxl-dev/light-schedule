package com.lightschedule.web;

import com.lightschedule.domain.model.WorkOrder;
import com.lightschedule.modules.capacity.CapacityAssessmentService;
import com.lightschedule.modules.resource.ResourceCatalogService;
import com.lightschedule.modules.scheduling.PlannerScenarioService;
import com.lightschedule.modules.taskpool.WorkOrderService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final PlannerScenarioService plannerScenarioService;
    private final CapacityAssessmentService capacityAssessmentService;
    private final WorkOrderService workOrderService;
    private final ResourceCatalogService resourceCatalogService;

    public DashboardController(
            PlannerScenarioService plannerScenarioService,
            CapacityAssessmentService capacityAssessmentService,
            WorkOrderService workOrderService,
            ResourceCatalogService resourceCatalogService) {
        this.plannerScenarioService = plannerScenarioService;
        this.capacityAssessmentService = capacityAssessmentService;
        this.workOrderService = workOrderService;
        this.resourceCatalogService = resourceCatalogService;
    }

    @GetMapping("/capacity-summary")
    public DashboardCapacitySummaryResponse capacitySummary() {
        var scenario = plannerScenarioService.loadDefaultScenario();
        int requiredMinutes = scenario.tasks().stream()
                .mapToInt(PlannerScenarioService.PlannerTask::requiredMinutes)
                .sum();
        int availableMinutes = (int) scenario.tasks().stream()
                .map(PlannerScenarioService.PlannerTask::resourceId)
                .distinct()
                .count() * 480;
        var result = capacityAssessmentService.assessCoarse(requiredMinutes, availableMinutes);

        // 驾驶舱摘要直接复用默认排程场景，避免和工作台草稿各自维护一套负荷口径。
        return new DashboardCapacitySummaryResponse(result.status(), roundToTwoDecimals(result.loadRate()));
    }

    @GetMapping("/overview")
    public DashboardOverviewResponse overview() {
        var scenario = plannerScenarioService.loadDefaultScenario();
        int requiredMinutes = scenario.tasks().stream()
                .mapToInt(PlannerScenarioService.PlannerTask::requiredMinutes)
                .sum();
        int availableMinutes = (int) scenario.tasks().stream()
                .map(PlannerScenarioService.PlannerTask::resourceId)
                .distinct()
                .count() * 480;
        var capacityResult = capacityAssessmentService.assessCoarse(requiredMinutes, availableMinutes);

        List<WorkOrder> workOrders = workOrderService != null ? workOrderService.loadAll() : List.of();
        int urgentCount = (int) workOrders.stream().filter(WorkOrder::urgent).count();
        Map<String, Long> riskDistribution = workOrders.stream()
                .collect(Collectors.groupingBy(WorkOrder::materialRisk, Collectors.counting()));

        var resources = resourceCatalogService.list();
        int defaultPlannerCount = (int) resources.stream().filter(ResourceCatalogService.ResourceDefinition::defaultPlannerResource).count();

        return new DashboardOverviewResponse(
                new DashboardOverviewResponse.CapacitySummary(capacityResult.status(), roundToTwoDecimals(capacityResult.loadRate())),
                new DashboardOverviewResponse.WorkOrderStats(workOrders.size(), urgentCount, riskDistribution),
                new DashboardOverviewResponse.ResourceStats(resources.size(), defaultPlannerCount)
        );
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

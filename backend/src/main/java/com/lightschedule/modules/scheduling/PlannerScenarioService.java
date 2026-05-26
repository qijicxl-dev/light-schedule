package com.lightschedule.modules.scheduling;

import com.lightschedule.domain.model.WorkOrder;
import com.lightschedule.modules.resource.ResourceCatalogService;
import com.lightschedule.modules.taskpool.WorkOrderService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PlannerScenarioService {

    private final ResourceCatalogService resourceCatalogService;
    private final WorkOrderService workOrderService;
    private final RouteStepService routeStepService;

    public PlannerScenarioService(ResourceCatalogService resourceCatalogService, WorkOrderService workOrderService, RouteStepService routeStepService) {
        this.resourceCatalogService = resourceCatalogService;
        this.workOrderService = workOrderService;
        this.routeStepService = routeStepService;
    }

    public PlannerScenario loadDefaultScenario() {
        return loadDefaultScenario("2026-04-24T08:00:00Z", "draft-1");
    }

    public PlannerScenario loadDefaultScenario(String startAt, String draftId) {
        List<String> defaultResourceIds = resourceCatalogService.listDefaultPlannerResourceIds();
        if (defaultResourceIds.isEmpty()) {
            return new PlannerScenario(List.of(), List.of(), startAt, draftId);
        }
        String primaryResourceId = defaultResourceIds.getFirst();

        List<WorkOrder> workOrders = workOrderService.loadAll();
        List<String> routeIds = workOrders.stream().map(WorkOrder::routeId).distinct().toList();
        List<PlannerTask> tasks = distributeTasksAcrossResources(
                routeStepService.loadTasksForRouteIds(routeIds, primaryResourceId),
                defaultResourceIds);

        return new PlannerScenario(
                workOrders.stream()
                        .map(wo -> new PlannerWorkOrder(
                                wo.workOrderCode(),
                                wo.status(),
                                wo.quantity(),
                                wo.dueAt(),
                                wo.routeId(),
                                wo.urgent(),
                                wo.parentWorkOrderCodes(),
                                wo.materialRisk()))
                        .toList(),
                tasks,
                startAt,
                draftId);
    }

    private List<PlannerTask> distributeTasksAcrossResources(List<PlannerTask> tasks, List<String> resourceIds) {
        if (resourceIds == null || resourceIds.size() <= 1 || tasks == null || tasks.isEmpty()) {
            return tasks;
        }
        String[] assigned = new String[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            PlannerTask task = tasks.get(i);
            String resourceId = resourceIds.get(i % resourceIds.size());
            if (task.dependencyTaskIds() != null && !task.dependencyTaskIds().isEmpty()) {
                String depResource = findDependencyResource(tasks, assigned, task.dependencyTaskIds());
                if (depResource != null) {
                    resourceId = depResource;
                }
            }
            assigned[i] = resourceId;
        }
        String[] finalAssigned = assigned;
        return tasks.stream()
                .map(task -> new PlannerTask(
                        task.taskId(),
                        finalAssigned[tasks.indexOf(task)],
                        task.requiredMinutes(),
                        task.dependencyTaskIds()))
                .toList();
    }

    private String findDependencyResource(List<PlannerTask> tasks, String[] assigned, List<String> dependencyTaskIds) {
        for (String depId : dependencyTaskIds) {
            for (int j = 0; j < tasks.size(); j++) {
                if (tasks.get(j).taskId().equals(depId) && assigned[j] != null) {
                    return assigned[j];
                }
            }
        }
        return null;
    }

    public record PlannerScenario(
            List<PlannerWorkOrder> workOrders,
            List<PlannerTask> tasks,
            String startAt,
            String draftId) {
    }

    public record PlannerWorkOrder(
            String workOrderCode,
            String status,
            int quantity,
            String dueAt,
            String routeId,
            boolean urgent,
            List<String> parentWorkOrderCodes,
            String materialRisk) {
    }

    public record PlannerTask(
            String taskId,
            String resourceId,
            int requiredMinutes,
            List<String> dependencyTaskIds) {
    }
}

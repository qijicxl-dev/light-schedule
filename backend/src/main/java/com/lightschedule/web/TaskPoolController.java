package com.lightschedule.web;

import com.lightschedule.domain.model.WorkOrder;
import com.lightschedule.modules.scheduling.PlannerScenarioService;
import com.lightschedule.modules.taskpool.TaskPoolService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-pool")
public class TaskPoolController {

    private final PlannerScenarioService plannerScenarioService;
    private final TaskPoolService taskPoolService;

    public TaskPoolController(PlannerScenarioService plannerScenarioService, TaskPoolService taskPoolService) {
        this.plannerScenarioService = plannerScenarioService;
        this.taskPoolService = taskPoolService;
    }

    @GetMapping
    public List<TaskPoolItemResponse> list() {
        var scenario = plannerScenarioService.loadDefaultScenario();

        // 任务池与初排读取同一份默认场景，避免 controller 各自散落样例输入。
        return taskPoolService.build(scenario.workOrders().stream()
                .map(workOrder -> new WorkOrder(
                        workOrder.workOrderCode(),
                        workOrder.status(),
                        workOrder.quantity(),
                        workOrder.dueAt(),
                        workOrder.routeId(),
                        workOrder.urgent(),
                        workOrder.parentWorkOrderCodes(),
                        workOrder.materialRisk()))
                .toList()).stream()
                .map(item -> new TaskPoolItemResponse(
                        item.workOrderCode(),
                        item.dueAt(),
                        item.urgent(),
                        item.materialRisk(),
                        item.readiness()))
                .toList();
    }
}

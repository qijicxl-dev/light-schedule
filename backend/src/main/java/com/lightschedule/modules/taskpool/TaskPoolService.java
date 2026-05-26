package com.lightschedule.modules.taskpool;

import com.lightschedule.domain.model.WorkOrder;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TaskPoolService {

    public List<TaskPoolItem> build(List<WorkOrder> workOrders) {
        Set<String> releasedCodes = workOrders.stream()
                .filter(order -> "released".equals(order.status()))
                .map(WorkOrder::workOrderCode)
                .collect(Collectors.toSet());

        return workOrders.stream()
                .filter(order -> "released".equals(order.status()))
                .map(order -> new TaskPoolItem(
                        order.workOrderCode(),
                        order.dueAt(),
                        order.urgent(),
                        order.materialRisk(),
                        order.parentWorkOrderCodes().stream().anyMatch(releasedCodes::contains)
                                ? "blocked_by_dependency"
                                : "ready"
                ))
                .toList();
    }

    public record TaskPoolItem(
            String workOrderCode,
            String dueAt,
            boolean urgent,
            String materialRisk,
            String readiness
    ) {
    }
}

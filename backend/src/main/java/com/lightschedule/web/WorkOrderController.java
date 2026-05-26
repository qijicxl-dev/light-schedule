package com.lightschedule.web;

import com.lightschedule.domain.model.WorkOrder;
import com.lightschedule.modules.taskpool.WorkOrderService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkOrderController(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @GetMapping
    public List<WorkOrder> list() {
        if (workOrderService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "work_order_persistence_not_available");
        }
        return workOrderService.loadAll();
    }

    @PostMapping
    public WorkOrder create(@RequestBody CreateWorkOrderRequest request) {
        if (workOrderService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "work_order_persistence_not_available");
        }
        WorkOrder workOrder = new WorkOrder(
                request.workOrderCode(),
                request.status(),
                request.quantity(),
                request.dueAt(),
                request.routeId(),
                request.urgent(),
                request.parentWorkOrderCodes() != null ? request.parentWorkOrderCodes() : List.of(),
                request.materialRisk());
        return workOrderService.save(workOrder);
    }

    @PutMapping("/{workOrderCode}")
    public WorkOrder update(@PathVariable("workOrderCode") String workOrderCode, @RequestBody CreateWorkOrderRequest request) {
        if (workOrderService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "work_order_persistence_not_available");
        }
        WorkOrder workOrder = new WorkOrder(
                request.workOrderCode(),
                request.status(),
                request.quantity(),
                request.dueAt(),
                request.routeId(),
                request.urgent(),
                request.parentWorkOrderCodes() != null ? request.parentWorkOrderCodes() : List.of(),
                request.materialRisk());
        try {
            return workOrderService.update(workOrderCode, workOrder);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/{workOrderCode}")
    public void delete(@PathVariable("workOrderCode") String workOrderCode) {
        if (workOrderService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "work_order_persistence_not_available");
        }
        workOrderService.delete(workOrderCode);
    }
}

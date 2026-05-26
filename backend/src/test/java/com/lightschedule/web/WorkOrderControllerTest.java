package com.lightschedule.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lightschedule.domain.model.WorkOrder;
import com.lightschedule.modules.taskpool.WorkOrderService;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WorkOrderControllerTest {

    @Test
    void shouldReturnWorkOrdersFromService() {
        WorkOrderService service = mock(WorkOrderService.class);
        when(service.loadAll()).thenReturn(List.of(
                new WorkOrder("WO-001", "released", 20, "2026-04-24T08:00:00Z", "ROUTE-01", false, List.of(), "low")));

        WorkOrderController controller = new WorkOrderController(service);
        List<WorkOrder> result = controller.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).workOrderCode()).isEqualTo("WO-001");
    }

    @Test
    void shouldCreateWorkOrderThroughService() {
        WorkOrderService service = mock(WorkOrderService.class);
        when(service.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrderController controller = new WorkOrderController(service);
        WorkOrder result = controller.create(new CreateWorkOrderRequest(
                "WO-002", "released", 10, "2026-04-25T08:00:00Z", "ROUTE-01", true, List.of(), "high"));

        assertThat(result.workOrderCode()).isEqualTo("WO-002");
        assertThat(result.quantity()).isEqualTo(10);
        assertThat(result.urgent()).isTrue();
        assertThat(result.materialRisk()).isEqualTo("high");
    }

    @Test
    void shouldUpdateWorkOrderThroughService() {
        WorkOrderService service = mock(WorkOrderService.class);
        when(service.update(eq("WO-001"), any())).thenAnswer(invocation -> invocation.getArgument(1));

        WorkOrderController controller = new WorkOrderController(service);
        WorkOrder result = controller.update("WO-001", new CreateWorkOrderRequest(
                "WO-001", "released", 30, "2026-04-26T08:00:00Z", "ROUTE-01", false, List.of(), "medium"));

        assertThat(result.workOrderCode()).isEqualTo("WO-001");
        assertThat(result.quantity()).isEqualTo(30);
        assertThat(result.materialRisk()).isEqualTo("medium");
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingNonExistentWorkOrder() {
        WorkOrderService service = mock(WorkOrderService.class);
        when(service.update(eq("WO-404"), any())).thenThrow(new NoSuchElementException("work order not found"));

        WorkOrderController controller = new WorkOrderController(service);
        org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class, () ->
                controller.update("WO-404", new CreateWorkOrderRequest(
                        "WO-404", "released", 10, "2026-04-25T08:00:00Z", "ROUTE-01", false, List.of(), "low")));
    }

    @Test
    void shouldDeleteWorkOrderThroughService() {
        WorkOrderService service = mock(WorkOrderService.class);

        WorkOrderController controller = new WorkOrderController(service);
        controller.delete("WO-001");

        verify(service).delete("WO-001");
    }
}

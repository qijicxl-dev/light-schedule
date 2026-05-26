package com.lightschedule.modules.taskpool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightschedule.domain.model.WorkOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkOrderServiceTest {

    @Test
    void shouldLoadWorkOrdersFromDatabaseWhenMapperAvailable() throws Exception {
        WorkOrderMapper mapper = mock(WorkOrderMapper.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.setWorkOrderCode("WO-001");
        entity.setStatus("released");
        entity.setQuantity(20);
        entity.setDueAt("2026-04-24T08:00:00Z");
        entity.setRouteId("ROUTE-01");
        entity.setUrgent(false);
        entity.setParentWorkOrderCodes("[]");
        entity.setMaterialRisk("low");
        when(mapper.selectList(null)).thenReturn(List.of(entity));
        when(objectMapper.readValue(eq("[]"), any(TypeReference.class))).thenReturn(List.of());

        WorkOrderService service = new WorkOrderService(mapper, objectMapper);
        List<WorkOrder> workOrders = service.loadAll();

        assertThat(workOrders).hasSize(1);
        assertThat(workOrders.get(0).workOrderCode()).isEqualTo("WO-001");
        assertThat(workOrders.get(0).status()).isEqualTo("released");
        assertThat(workOrders.get(0).quantity()).isEqualTo(20);
        assertThat(workOrders.get(0).dueAt()).isEqualTo("2026-04-24T08:00:00Z");
        assertThat(workOrders.get(0).routeId()).isEqualTo("ROUTE-01");
        assertThat(workOrders.get(0).urgent()).isFalse();
        assertThat(workOrders.get(0).parentWorkOrderCodes()).isEmpty();
        assertThat(workOrders.get(0).materialRisk()).isEqualTo("low");
    }

    @Test
    void shouldReturnEmptyListWhenMapperIsNull() {
        WorkOrderService service = new WorkOrderService(null, null);
        List<WorkOrder> workOrders = service.loadAll();

        assertThat(workOrders).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenMapperReturnsEmptyList() {
        WorkOrderMapper mapper = mock(WorkOrderMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of());

        WorkOrderService service = new WorkOrderService(mapper, null);
        List<WorkOrder> workOrders = service.loadAll();

        assertThat(workOrders).isEmpty();
    }

    @Test
    void shouldHandleInvalidParentWorkOrderCodesJson() {
        WorkOrderMapper mapper = mock(WorkOrderMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.setWorkOrderCode("WO-002");
        entity.setStatus("released");
        entity.setQuantity(10);
        entity.setDueAt("2026-04-25T08:00:00Z");
        entity.setRouteId("ROUTE-02");
        entity.setUrgent(true);
        entity.setParentWorkOrderCodes("invalid-json");
        entity.setMaterialRisk("high");
        when(mapper.selectList(null)).thenReturn(List.of(entity));

        WorkOrderService service = new WorkOrderService(mapper, objectMapper);
        List<WorkOrder> workOrders = service.loadAll();

        assertThat(workOrders).hasSize(1);
        assertThat(workOrders.get(0).workOrderCode()).isEqualTo("WO-002");
        assertThat(workOrders.get(0).parentWorkOrderCodes()).isEmpty();
        assertThat(workOrders.get(0).urgent()).isTrue();
    }
}

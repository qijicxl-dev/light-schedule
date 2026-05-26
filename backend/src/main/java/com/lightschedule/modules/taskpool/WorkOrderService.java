package com.lightschedule.modules.taskpool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightschedule.domain.model.WorkOrder;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkOrderService {

    private final WorkOrderMapper workOrderMapper;
    private final ObjectMapper objectMapper;

    public WorkOrderService(WorkOrderMapper workOrderMapper, ObjectMapper objectMapper) {
        this.workOrderMapper = workOrderMapper;
        this.objectMapper = objectMapper;
    }

    public List<WorkOrder> loadAll() {
        if (workOrderMapper == null) {
            return List.of();
        }
        List<WorkOrderEntity> entities = workOrderMapper.selectList(null);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(this::toDomain)
                .toList();
    }

    public WorkOrder save(WorkOrder workOrder) {
        if (workOrderMapper == null) {
            return workOrder;
        }
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setWorkOrderCode(workOrder.workOrderCode());
        entity.setStatus(workOrder.status());
        entity.setQuantity(workOrder.quantity());
        entity.setDueAt(workOrder.dueAt());
        entity.setRouteId(workOrder.routeId());
        entity.setUrgent(workOrder.urgent());
        try {
            entity.setParentWorkOrderCodes(objectMapper.writeValueAsString(workOrder.parentWorkOrderCodes()));
        } catch (JsonProcessingException exception) {
            entity.setParentWorkOrderCodes("[]");
        }
        entity.setMaterialRisk(workOrder.materialRisk());
        workOrderMapper.insert(entity);
        return workOrder;
    }

    public WorkOrder update(String workOrderCode, WorkOrder workOrder) {
        if (workOrderMapper == null) {
            return workOrder;
        }
        WorkOrderEntity existing = workOrderMapper.selectOne(
                new QueryWrapper<WorkOrderEntity>().eq("work_order_code", workOrderCode));
        if (existing == null) {
            throw new NoSuchElementException("work order not found: " + workOrderCode);
        }
        existing.setStatus(workOrder.status());
        existing.setQuantity(workOrder.quantity());
        existing.setDueAt(workOrder.dueAt());
        existing.setRouteId(workOrder.routeId());
        existing.setUrgent(workOrder.urgent());
        try {
            existing.setParentWorkOrderCodes(objectMapper.writeValueAsString(workOrder.parentWorkOrderCodes()));
        } catch (JsonProcessingException exception) {
            existing.setParentWorkOrderCodes("[]");
        }
        existing.setMaterialRisk(workOrder.materialRisk());
        workOrderMapper.updateById(existing);
        return workOrder;
    }

    public void delete(String workOrderCode) {
        if (workOrderMapper == null) {
            return;
        }
        workOrderMapper.delete(new QueryWrapper<WorkOrderEntity>().eq("work_order_code", workOrderCode));
    }

    private WorkOrder toDomain(WorkOrderEntity entity) {
        List<String> parentCodes;
        String parentCodesJson = entity.getParentWorkOrderCodes();
        if (parentCodesJson == null || parentCodesJson.isBlank()) {
            parentCodes = Collections.emptyList();
        } else {
            try {
                parentCodes = objectMapper.readValue(parentCodesJson, new TypeReference<List<String>>() {});
            } catch (JsonProcessingException exception) {
                parentCodes = Collections.emptyList();
            }
        }
        return new WorkOrder(
                entity.getWorkOrderCode(),
                entity.getStatus(),
                entity.getQuantity() != null ? entity.getQuantity() : 0,
                entity.getDueAt(),
                entity.getRouteId(),
                Boolean.TRUE.equals(entity.getUrgent()),
                parentCodes,
                entity.getMaterialRisk()
        );
    }
}

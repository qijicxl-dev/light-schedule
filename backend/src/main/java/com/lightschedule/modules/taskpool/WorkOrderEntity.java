package com.lightschedule.modules.taskpool;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("work_order")
public class WorkOrderEntity {

    @TableId(type = IdType.INPUT)
    private String id;
    private String workOrderCode;
    private String status;
    private Integer quantity;
    private String dueAt;
    private String routeId;
    private Boolean urgent;
    private String parentWorkOrderCodes;
    private String materialRisk;
    private LocalDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWorkOrderCode() {
        return workOrderCode;
    }

    public void setWorkOrderCode(String workOrderCode) {
        this.workOrderCode = workOrderCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getDueAt() {
        return dueAt;
    }

    public void setDueAt(String dueAt) {
        this.dueAt = dueAt;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public Boolean getUrgent() {
        return urgent;
    }

    public void setUrgent(Boolean urgent) {
        this.urgent = urgent;
    }

    public String getParentWorkOrderCodes() {
        return parentWorkOrderCodes;
    }

    public void setParentWorkOrderCodes(String parentWorkOrderCodes) {
        this.parentWorkOrderCodes = parentWorkOrderCodes;
    }

    public String getMaterialRisk() {
        return materialRisk;
    }

    public void setMaterialRisk(String materialRisk) {
        this.materialRisk = materialRisk;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

package com.lightschedule.modules.scheduling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RouteStepService {

    private final RouteStepMapper routeStepMapper;
    private final ObjectMapper objectMapper;

    public RouteStepService(RouteStepMapper routeStepMapper, ObjectMapper objectMapper) {
        this.routeStepMapper = routeStepMapper;
        this.objectMapper = objectMapper;
    }

    public List<String> listRoutes() {
        if (routeStepMapper == null) {
            return List.of();
        }
        List<RouteStepEntity> entities = routeStepMapper.selectList(null);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(RouteStepEntity::getRouteId)
                .distinct()
                .toList();
    }

    public List<RouteStepDetail> listRouteSteps(String routeId) {
        if (routeStepMapper == null || routeId == null || routeId.isEmpty()) {
            return List.of();
        }
        List<RouteStepEntity> entities = routeStepMapper.selectList(null);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .filter(e -> routeId.equals(e.getRouteId()))
                .map(e -> new RouteStepDetail(e.getStepId(), e.getRequiredMinutes() != null ? e.getRequiredMinutes() : 0, parseDependencyIds(e.getDependencyStepIds())))
                .toList();
    }

    public record RouteStepDetail(String stepId, int requiredMinutes, List<String> dependencyStepIds) {
    }

    public List<PlannerScenarioService.PlannerTask> loadTasksForRouteIds(List<String> routeIds, String defaultResourceId) {
        if (routeStepMapper == null || routeIds == null || routeIds.isEmpty()) {
            return List.of();
        }

        List<RouteStepEntity> entities = routeStepMapper.selectList(null);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }

        return entities.stream()
                .filter(e -> routeIds.contains(e.getRouteId()))
                .map(e -> toDomain(e, defaultResourceId))
                .toList();
    }

    private PlannerScenarioService.PlannerTask toDomain(RouteStepEntity entity, String defaultResourceId) {
        return new PlannerScenarioService.PlannerTask(
                entity.getStepId(),
                defaultResourceId,
                entity.getRequiredMinutes() != null ? entity.getRequiredMinutes() : 0,
                parseDependencyIds(entity.getDependencyStepIds())
        );
    }

    private List<String> parseDependencyIds(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException exception) {
            return Collections.emptyList();
        }
    }
}

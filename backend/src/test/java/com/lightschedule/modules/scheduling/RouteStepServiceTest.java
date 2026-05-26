package com.lightschedule.modules.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteStepServiceTest {

    @Test
    void shouldLoadTasksFromDatabaseWhenMapperAvailable() throws Exception {
        RouteStepMapper mapper = mock(RouteStepMapper.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        RouteStepEntity entity = new RouteStepEntity();
        entity.setId("rs-1");
        entity.setRouteId("ROUTE-01");
        entity.setStepId("TASK-001");
        entity.setRequiredMinutes(120);
        entity.setDependencyStepIds("[]");
        when(mapper.selectList(null)).thenReturn(List.of(entity));
        when(objectMapper.readValue(eq("[]"), any(TypeReference.class))).thenReturn(List.of());

        RouteStepService service = new RouteStepService(mapper, objectMapper);
        List<PlannerScenarioService.PlannerTask> tasks = service.loadTasksForRouteIds(List.of("ROUTE-01"), "LINE-A");

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).taskId()).isEqualTo("TASK-001");
        assertThat(tasks.get(0).resourceId()).isEqualTo("LINE-A");
        assertThat(tasks.get(0).requiredMinutes()).isEqualTo(120);
        assertThat(tasks.get(0).dependencyTaskIds()).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenMapperIsNull() {
        RouteStepService service = new RouteStepService(null, null);
        List<PlannerScenarioService.PlannerTask> tasks = service.loadTasksForRouteIds(List.of("ROUTE-01"), "LINE-A");

        assertThat(tasks).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenRouteIdsIsEmpty() {
        RouteStepService service = new RouteStepService(null, null);
        List<PlannerScenarioService.PlannerTask> tasks = service.loadTasksForRouteIds(List.of(), "LINE-A");

        assertThat(tasks).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenMapperReturnsEmptyList() {
        RouteStepMapper mapper = mock(RouteStepMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of());

        RouteStepService service = new RouteStepService(mapper, null);
        List<PlannerScenarioService.PlannerTask> tasks = service.loadTasksForRouteIds(List.of("ROUTE-01"), "LINE-A");

        assertThat(tasks).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenListingRoutesAndMapperIsNull() {
        RouteStepService service = new RouteStepService(null, null);
        List<String> routes = service.listRoutes();

        assertThat(routes).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenListingRoutesAndMapperReturnsEmptyList() {
        RouteStepMapper mapper = mock(RouteStepMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of());

        RouteStepService service = new RouteStepService(mapper, null);
        List<String> routes = service.listRoutes();

        assertThat(routes).isEmpty();
    }

    @Test
    void shouldFilterTasksByRouteId() {
        RouteStepMapper mapper = mock(RouteStepMapper.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        RouteStepEntity entity1 = new RouteStepEntity();
        entity1.setRouteId("ROUTE-01");
        entity1.setStepId("TASK-001");
        entity1.setRequiredMinutes(120);
        entity1.setDependencyStepIds("[]");
        RouteStepEntity entity2 = new RouteStepEntity();
        entity2.setRouteId("ROUTE-02");
        entity2.setStepId("TASK-003");
        entity2.setRequiredMinutes(60);
        entity2.setDependencyStepIds("[]");
        when(mapper.selectList(null)).thenReturn(List.of(entity1, entity2));
        try {
            when(objectMapper.readValue(eq("[]"), any(TypeReference.class))).thenReturn(List.of());
        } catch (Exception ignored) {
        }

        RouteStepService service = new RouteStepService(mapper, objectMapper);
        List<PlannerScenarioService.PlannerTask> tasks = service.loadTasksForRouteIds(List.of("ROUTE-01"), "LINE-A");

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).taskId()).isEqualTo("TASK-001");
    }

    @Test
    void shouldListRouteStepsForGivenRouteId() throws Exception {
        RouteStepMapper mapper = mock(RouteStepMapper.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        RouteStepEntity entity1 = new RouteStepEntity();
        entity1.setRouteId("ROUTE-01");
        entity1.setStepId("TASK-001");
        entity1.setRequiredMinutes(120);
        entity1.setDependencyStepIds("[]");
        RouteStepEntity entity2 = new RouteStepEntity();
        entity2.setRouteId("ROUTE-01");
        entity2.setStepId("TASK-002");
        entity2.setRequiredMinutes(90);
        entity2.setDependencyStepIds("[\"TASK-001\"]");
        RouteStepEntity entity3 = new RouteStepEntity();
        entity3.setRouteId("ROUTE-02");
        entity3.setStepId("TASK-003");
        entity3.setRequiredMinutes(60);
        entity3.setDependencyStepIds("[]");
        when(mapper.selectList(null)).thenReturn(List.of(entity1, entity2, entity3));
        when(objectMapper.readValue(eq("[]"), any(TypeReference.class))).thenReturn(List.of());
        when(objectMapper.readValue(eq("[\"TASK-001\"]"), any(TypeReference.class))).thenReturn(List.of("TASK-001"));

        RouteStepService service = new RouteStepService(mapper, objectMapper);
        List<RouteStepService.RouteStepDetail> steps = service.listRouteSteps("ROUTE-01");

        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).stepId()).isEqualTo("TASK-001");
        assertThat(steps.get(0).requiredMinutes()).isEqualTo(120);
        assertThat(steps.get(0).dependencyStepIds()).isEmpty();
        assertThat(steps.get(1).stepId()).isEqualTo("TASK-002");
        assertThat(steps.get(1).requiredMinutes()).isEqualTo(90);
        assertThat(steps.get(1).dependencyStepIds()).containsExactly("TASK-001");
    }

    @Test
    void shouldReturnEmptyListWhenListingStepsForUnknownRouteId() {
        RouteStepMapper mapper = mock(RouteStepMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of());

        RouteStepService service = new RouteStepService(mapper, null);
        List<RouteStepService.RouteStepDetail> steps = service.listRouteSteps("ROUTE-99");

        assertThat(steps).isEmpty();
    }
}

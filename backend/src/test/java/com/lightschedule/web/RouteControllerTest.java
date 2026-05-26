package com.lightschedule.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lightschedule.modules.scheduling.RouteStepService;
import com.lightschedule.modules.scheduling.RouteStepService.RouteStepDetail;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteControllerTest {

    @Test
    void shouldReturnRoutesFromService() {
        RouteStepService service = mock(RouteStepService.class);
        when(service.listRoutes()).thenReturn(List.of("ROUTE-01", "ROUTE-02"));

        RouteController controller = new RouteController(service);
        List<String> result = controller.list();

        assertThat(result).containsExactly("ROUTE-01", "ROUTE-02");
    }

    @Test
    void shouldReturnRouteStepsFromService() {
        RouteStepService service = mock(RouteStepService.class);
        when(service.listRouteSteps("ROUTE-01")).thenReturn(List.of(
                new RouteStepDetail("TASK-001", 120, List.of()),
                new RouteStepDetail("TASK-002", 90, List.of("TASK-001"))));

        RouteController controller = new RouteController(service);
        List<RouteStepResponse> result = controller.listSteps("ROUTE-01");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).stepId()).isEqualTo("TASK-001");
        assertThat(result.get(0).requiredMinutes()).isEqualTo(120);
        assertThat(result.get(0).dependencyStepIds()).isEmpty();
        assertThat(result.get(1).stepId()).isEqualTo("TASK-002");
        assertThat(result.get(1).dependencyStepIds()).containsExactly("TASK-001");
    }
}

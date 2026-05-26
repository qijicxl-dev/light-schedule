package com.lightschedule.web;

import com.lightschedule.modules.scheduling.RouteStepService;
import com.lightschedule.modules.scheduling.RouteStepService.RouteStepDetail;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final RouteStepService routeStepService;

    public RouteController(RouteStepService routeStepService) {
        this.routeStepService = routeStepService;
    }

    @GetMapping
    public List<String> list() {
        return routeStepService.listRoutes();
    }

    @GetMapping("/{routeId}/steps")
    public List<RouteStepResponse> listSteps(@PathVariable("routeId") String routeId) {
        return routeStepService.listRouteSteps(routeId).stream()
                .map(d -> new RouteStepResponse(d.stepId(), d.requiredMinutes(), d.dependencyStepIds()))
                .toList();
    }
}

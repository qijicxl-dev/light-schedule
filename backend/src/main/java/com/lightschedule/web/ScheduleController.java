package com.lightschedule.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightschedule.modules.resource.ResourceGroupService;
import com.lightschedule.modules.scheduling.InitialSchedulingService;
import com.lightschedule.modules.scheduling.PlannerScenarioService;
import com.lightschedule.modules.scheduling.ScheduleDraftService;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final PlannerScenarioService plannerScenarioService;
    private final InitialSchedulingService initialSchedulingService;
    private final ResourceGroupService resourceGroupService;
    private final ScheduleDraftService scheduleDraftService;
    private final ObjectMapper objectMapper;

    public ScheduleController(
            PlannerScenarioService plannerScenarioService,
            InitialSchedulingService initialSchedulingService,
            ResourceGroupService resourceGroupService,
            ScheduleDraftService scheduleDraftService,
            ObjectMapper objectMapper) {
        this.plannerScenarioService = plannerScenarioService;
        this.initialSchedulingService = initialSchedulingService;
        this.resourceGroupService = resourceGroupService;
        this.scheduleDraftService = scheduleDraftService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/draft")
    public DraftScheduleResponse draft() {
        String draftId = "draft-" + System.currentTimeMillis();
        var scenario = plannerScenarioService.loadDefaultScenario();
        InitialSchedulingService.ScheduleResult scheduleResult = initialSchedulingService.build(new InitialSchedulingService.ScheduleInput(
                scenario.tasks().stream()
                        .map(task -> new InitialSchedulingService.Task(
                                task.taskId(),
                                task.resourceId(),
                                task.requiredMinutes(),
                                task.dependencyTaskIds()))
                        .toList(),
                scenario.startAt()
        ));
        // 初排与任务池共享同一份默认场景，避免草稿和任务来源各自漂移。
        DraftScheduleResponse response = new DraftScheduleResponse(
                draftId,
                scheduleResult.items().stream()
                        .map(item -> new DraftScheduleItemResponse(
                                item.taskId(),
                                item.resourceId(),
                                resourceGroupService.getGroupName(item.resourceId()),
                                item.startAt(),
                                item.endAt()))
                        .toList());

        if (scheduleDraftService != null) {
            try {
                scheduleDraftService.save(response.draftId(), objectMapper.writeValueAsString(response));
            } catch (JsonProcessingException exception) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "draft_serialize_failed", exception);
            }
        }

        return response;
    }

    @GetMapping("/draft/{draftId}")
    public DraftScheduleResponse loadDraft(@PathVariable("draftId") String draftId) {
        if (scheduleDraftService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "draft_persistence_not_available");
        }

        try {
            var entity = scheduleDraftService.findById(draftId);
            return objectMapper.readValue(entity.getDraftPayload(), DraftScheduleResponse.class);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "draft_deserialize_failed", exception);
        }
    }
}

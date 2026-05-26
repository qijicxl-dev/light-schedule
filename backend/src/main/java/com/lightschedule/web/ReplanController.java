package com.lightschedule.web;

import com.lightschedule.modules.replan.UrgentReplanService;
import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/replans")
public class ReplanController {

    private final UrgentReplanService urgentReplanService;

    public ReplanController(UrgentReplanService urgentReplanService) {
        this.urgentReplanService = urgentReplanService;
    }

    @PostMapping("/urgent")
    public UrgentReplanResponse urgent(@RequestBody UrgentReplanRequest request) {
        var result = urgentReplanService.replan(
                request.urgentTaskId(),
                Stream.concat(
                                request.items().stream()
                                        .map(item -> new ScheduledItem(item.taskId(), item.resourceId(), item.startAt(), item.endAt(), item.dependencyTaskIds())),
                                Stream.of(new ScheduledItem(
                                        request.urgentTaskId(),
                                        request.urgentResourceId(),
                                        request.urgentStartAt(),
                                        request.urgentEndAt(),
                                        List.of())))
                        .toList());
        // 在这里转换建议项，保持 HTTP 响应结构由 web 层自己定义。
        return new UrgentReplanResponse(
                result.urgentTaskId(),
                result.affectedTaskIds(),
                result.suggestions().stream()
                        .map(suggestion -> new UrgentReplanSuggestionResponse(
                                suggestion.action(),
                                suggestion.reason()))
                        .toList()
        );
    }
}

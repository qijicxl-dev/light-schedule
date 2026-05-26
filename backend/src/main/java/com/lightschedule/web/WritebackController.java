package com.lightschedule.web;

import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import com.lightschedule.modules.writeback.WritebackService;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/writeback")
public class WritebackController {

    private final WritebackService writebackService;

    public WritebackController(WritebackService writebackService) {
        this.writebackService = writebackService;
    }

    @PostMapping("/publish")
    public PublishResponse publish(@RequestBody PublishRequest request) {
        try {
            var result = writebackService.publish(
                    request.draftId(),
                    request.items().stream()
                            .map(item -> new ScheduledItem(item.taskId(), item.resourceId(), item.startAt(), item.endAt(), item.dependencyTaskIds()))
                            .toList());
            return new PublishResponse(result.draftId(), result.auditId(), result.status(), result.writebackStatus());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{auditId}")
    public WritebackStatusResponse status(@PathVariable("auditId") String auditId) {
        try {
            var status = writebackService.status(auditId);
            return new WritebackStatusResponse(
                    status.auditId(),
                    status.draftId(),
                    status.status(),
                    status.writebackStatus(),
                    status.message(),
                    status.retryable(),
                    status.attemptCount(),
                    status.maxAttempts(),
                    status.nextRetryAt());
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }
}

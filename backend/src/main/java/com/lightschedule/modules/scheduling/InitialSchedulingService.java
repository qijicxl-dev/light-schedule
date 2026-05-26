package com.lightschedule.modules.scheduling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InitialSchedulingService {

    public ScheduleResult build(ScheduleInput input) {
        Instant[] cursor = {Instant.parse(input.startAt())};
        List<ScheduledItem> items = input.tasks().stream()
                .sorted(Comparator.comparingInt(task -> task.dependencyTaskIds().size()))
                .map(task -> {
                    Instant startAt = cursor[0];
                    Instant endAt = startAt.plus(task.requiredMinutes(), ChronoUnit.MINUTES);
                    cursor[0] = endAt;
                    return new ScheduledItem(task.taskId(), task.resourceId(), startAt.toString(), endAt.toString());
                })
                .toList();
        return new ScheduleResult(items);
    }

    public record ScheduleInput(List<Task> tasks, String startAt) {
    }

    public record Task(String taskId, String resourceId, int requiredMinutes, List<String> dependencyTaskIds) {
    }

    public record ScheduledItem(String taskId, String resourceId, String startAt, String endAt) {
    }

    public record ScheduleResult(List<ScheduledItem> items) {
    }
}

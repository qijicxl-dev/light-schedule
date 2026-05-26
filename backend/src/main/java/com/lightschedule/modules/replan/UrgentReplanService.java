package com.lightschedule.modules.replan;

import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import com.lightschedule.modules.scheduling.PlannerScenarioService;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UrgentReplanService {

    private final SuggestionService suggestionService;
    private final PlannerScenarioService plannerScenarioService;

    public UrgentReplanService(
            SuggestionService suggestionService,
            PlannerScenarioService plannerScenarioService) {
        this.suggestionService = suggestionService;
        this.plannerScenarioService = plannerScenarioService;
    }

    public ReplanResult replan(String urgentTaskId, List<ScheduledItem> items) {
        Map<String, List<ScheduledItem>> itemsByResource = items.stream()
                .collect(Collectors.groupingBy(ScheduledItem::resourceId));
        List<String> overloadedResourceIds = itemsByResource.entrySet().stream()
                .filter(entry -> hasOverlap(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        List<String> directlyAffectedTaskIds = itemsByResource.entrySet().stream()
                .filter(entry -> overloadedResourceIds.contains(entry.getKey()))
                .flatMap(entry -> overlappingTaskIds(entry.getValue()).stream())
                .distinct()
                .toList();
        List<String> affectedTaskIds = expandAffectedTaskIds(directlyAffectedTaskIds, items);

        // 直接重叠只是冲击起点，下游依赖任务也要纳入影响链，页面才能看到真实延期风险。
        return new ReplanResult(urgentTaskId, affectedTaskIds, suggestionService.build(overloadedResourceIds));
    }

    private boolean hasOverlap(List<ScheduledItem> items) {
        return !overlappingTaskIds(items).isEmpty();
    }

    private List<String> overlappingTaskIds(List<ScheduledItem> items) {
        List<ScheduledItem> sortedItems = items.stream()
                .sorted(java.util.Comparator.comparing(item -> Instant.parse(item.startAt())))
                .toList();
        java.util.Set<String> overlappingTaskIds = new java.util.LinkedHashSet<>();

        for (int index = 0; index < sortedItems.size(); index++) {
            ScheduledItem current = sortedItems.get(index);
            Instant currentStart = Instant.parse(current.startAt());
            Instant currentEnd = Instant.parse(current.endAt());

            for (int nextIndex = index + 1; nextIndex < sortedItems.size(); nextIndex++) {
                ScheduledItem next = sortedItems.get(nextIndex);
                Instant nextStart = Instant.parse(next.startAt());
                if (!nextStart.isBefore(currentEnd)) {
                    break;
                }
                Instant nextEnd = Instant.parse(next.endAt());
                if (currentStart.isBefore(nextEnd) && nextStart.isBefore(currentEnd)) {
                    overlappingTaskIds.add(current.taskId());
                    overlappingTaskIds.add(next.taskId());
                }
            }
        }

        return List.copyOf(overlappingTaskIds);
    }

    private List<String> expandAffectedTaskIds(List<String> directlyAffectedTaskIds, List<ScheduledItem> items) {
        Map<String, List<String>> dependentTaskIdsByDependencyId = plannerScenarioService.loadDefaultScenario().tasks().stream()
                .collect(Collectors.groupingBy(
                        task -> task.dependencyTaskIds().isEmpty() ? "" : task.dependencyTaskIds().getFirst(),
                        Collectors.mapping(PlannerScenarioService.PlannerTask::taskId, Collectors.toList())));
        java.util.Set<String> scheduledTaskIds = items.stream()
                .map(ScheduledItem::taskId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> affectedTaskIds = new LinkedHashSet<>(directlyAffectedTaskIds);
        ArrayDeque<String> pendingTaskIds = new ArrayDeque<>(directlyAffectedTaskIds);

        while (!pendingTaskIds.isEmpty()) {
            String taskId = pendingTaskIds.removeFirst();
            for (String dependentTaskId : dependentTaskIdsByDependencyId.getOrDefault(taskId, List.of())) {
                if (scheduledTaskIds.contains(dependentTaskId) && affectedTaskIds.add(dependentTaskId)) {
                    pendingTaskIds.addLast(dependentTaskId);
                }
            }
        }

        return List.copyOf(affectedTaskIds);
    }

    public record ReplanResult(String urgentTaskId, List<String> affectedTaskIds, List<SuggestionService.Suggestion> suggestions) {
    }
}

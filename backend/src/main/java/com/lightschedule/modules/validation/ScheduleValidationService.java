package com.lightschedule.modules.validation;

import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ScheduleValidationService {

    public List<String> validateHardRules(List<ScheduledItem> items) {
        List<String> issues = new ArrayList<>();
        List<ScheduledItem> previousItems = new ArrayList<>();

        for (ScheduledItem item : items) {
            issues.addAll(validateHardRules(item, previousItems));
            previousItems.add(item);
        }

        // 去重后保留出现顺序，避免重复把同一阻塞项抛给回写流程。
        return new ArrayList<>(new LinkedHashSet<>(issues));
    }

    public List<String> validateHardRules(ScheduledItem current, List<ScheduledItem> previousItems) {
        boolean overlap = previousItems.stream()
                .anyMatch(item -> item.resourceId().equals(current.resourceId()) && item.endAt().compareTo(current.startAt()) > 0);
        return overlap ? List.of("resource_conflict") : List.of();
    }
}

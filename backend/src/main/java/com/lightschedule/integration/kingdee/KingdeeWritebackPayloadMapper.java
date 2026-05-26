package com.lightschedule.integration.kingdee;

import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class KingdeeWritebackPayloadMapper {

    public KingdeeWritebackPayload map(String draftId, List<ScheduledItem> items) {
        AtomicInteger sequence = new AtomicInteger(1);
        return new KingdeeWritebackPayload(
                draftId,
                items.stream()
                        .map(item -> new KingdeeWritebackItemPayload(
                                item.taskId(),
                                item.resourceId(),
                                item.startAt(),
                                item.endAt(),
                                String.valueOf(sequence.getAndIncrement())))
                        .toList());
    }
}

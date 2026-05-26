package com.lightschedule.integration.kingdee;

import static org.assertj.core.api.Assertions.assertThat;

import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import java.util.List;
import org.junit.jupiter.api.Test;

class KingdeeWritebackPayloadMapperTest {

    @Test
    void shouldMapScheduledItemsToKingdeePayload() {
        KingdeeWritebackPayload payload = new KingdeeWritebackPayloadMapper().map(
                "draft-1",
                List.of(new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z", List.of())));

        assertThat(payload.draftId()).isEqualTo("draft-1");
        assertThat(payload.items()).hasSize(1);
        assertThat(payload.items().getFirst().workOrderCode()).isEqualTo("TASK-001");
        assertThat(payload.items().getFirst().resourceId()).isEqualTo("LINE-A");
        assertThat(payload.items().getFirst().plannedStartAt()).isEqualTo("2026-04-24T08:00:00Z");
        assertThat(payload.items().getFirst().plannedEndAt()).isEqualTo("2026-04-24T10:00:00Z");
        assertThat(payload.items().getFirst().sequenceNo()).isEqualTo("1");
    }
}

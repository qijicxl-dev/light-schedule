package com.lightschedule.modules.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InitialSchedulingServiceTest {

    @Test
    void shouldScheduleParentBeforeChildWithoutOverlap() {
        InitialSchedulingService service = new InitialSchedulingService();

        var result = service.build(new InitialSchedulingService.ScheduleInput(
                List.of(
                        new InitialSchedulingService.Task("T1", "LINE-A", 120, List.of()),
                        new InitialSchedulingService.Task("T2", "LINE-A", 60, List.of("T1"))
                ),
                "2026-04-24T08:00:00Z"
        ));

        assertThat(result.items().get(0).endAt()).isLessThanOrEqualTo(result.items().get(1).startAt());
    }
}

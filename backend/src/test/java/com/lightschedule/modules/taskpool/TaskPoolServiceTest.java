package com.lightschedule.modules.taskpool;

import static org.assertj.core.api.Assertions.assertThat;

import com.lightschedule.domain.model.WorkOrder;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskPoolServiceTest {

    @Test
    void shouldMarkChildOrderBlockedAndKeepMaterialRisk() {
        TaskPoolService service = new TaskPoolService();

        var items = service.build(List.of(
                new WorkOrder("MO-1", "released", 100, "2026-04-25T00:00:00Z", "R1", false, List.of(), "ok"),
                new WorkOrder("MO-2", "released", 50, "2026-04-26T00:00:00Z", "R2", true, List.of("MO-1"), "missing")
        ));

        assertThat(items.get(1).readiness()).isEqualTo("blocked_by_dependency");
        assertThat(items.get(1).materialRisk()).isEqualTo("missing");
    }
}

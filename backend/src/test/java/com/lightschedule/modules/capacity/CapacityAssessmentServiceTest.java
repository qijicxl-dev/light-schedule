package com.lightschedule.modules.capacity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CapacityAssessmentServiceTest {

    @Test
    void shouldReturnOverloadedForCoarseCapacity() {
        CapacityAssessmentService service = new CapacityAssessmentService();
        assertThat(service.assessCoarse(960, 480).status()).isEqualTo("overloaded");
    }

    @Test
    void shouldReturnHighLoadForFineCapacity() {
        CapacityAssessmentService service = new CapacityAssessmentService();
        assertThat(service.assessFine(60, 120, 70, 0.8).status()).isEqualTo("placeable_high_load");
    }
}

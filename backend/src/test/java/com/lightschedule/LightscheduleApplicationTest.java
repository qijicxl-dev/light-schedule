package com.lightschedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

class LightscheduleApplicationTest {

    @Test
    void shouldStartWithoutDatabaseWhenFlywayIsNotExplicitlyEnabled() {
        assertThatCode(() -> {
                    try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(LightscheduleApplication.class)
                            .web(WebApplicationType.NONE)
                            .properties(
                                    "spring.datasource.url=jdbc:sqlserver://127.0.0.1:1;databaseName=light_schedule;encrypt=true;trustServerCertificate=true",
                                    "spring.datasource.username=sa",
                                    "spring.datasource.password=wrong-password",
                                    "spring.task.scheduling.enabled=false"
                            )
                            .run()) {
                    }
                })
                .doesNotThrowAnyException();
    }

    @Test
    void shouldNotRegisterScheduledProcessorWhenSchedulingIsDisabled() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(LightscheduleApplication.class)
                .web(WebApplicationType.NONE)
                .properties("spring.task.scheduling.enabled=false")
                .run()) {
            assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        }
    }
}

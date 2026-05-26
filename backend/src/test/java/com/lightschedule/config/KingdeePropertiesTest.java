package com.lightschedule.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class KingdeePropertiesTest {

    @Test
    void shouldRejectBlankWritebackPath() {
        assertThatThrownBy(() -> new KingdeeProperties(
                "https://kingdee.example.com",
                "demo-app",
                "demo-secret",
                "/",
                "/k3cloud/schedule/writeback/status",
                new KingdeeProperties.RetryProperties(3, 30),
                new KingdeeProperties.ExecutorProperties(4, 8, 200)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kingdee.writeback-path");
    }

    @Test
    void shouldRejectBlankWritebackStatusPath() {
        assertThatThrownBy(() -> new KingdeeProperties(
                "https://kingdee.example.com",
                "demo-app",
                "demo-secret",
                "/k3cloud/schedule/writeback",
                "/",
                new KingdeeProperties.RetryProperties(3, 30),
                new KingdeeProperties.ExecutorProperties(4, 8, 200)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kingdee.writeback-status-path");
    }

    @Test
    void shouldExposeRetrySettings() {
        KingdeeProperties properties = new KingdeeProperties(
                "https://kingdee.example.com",
                "demo-app",
                "demo-secret",
                "/k3cloud/schedule/writeback",
                "/k3cloud/schedule/writeback/status",
                new KingdeeProperties.RetryProperties(3, 30),
                new KingdeeProperties.ExecutorProperties(4, 8, 200));

        assertThat(properties.retry().maxAttempts()).isEqualTo(3);
        assertThat(properties.retry().baseDelaySeconds()).isEqualTo(30);
    }
}

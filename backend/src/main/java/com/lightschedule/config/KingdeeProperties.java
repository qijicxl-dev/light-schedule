package com.lightschedule.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kingdee")
public record KingdeeProperties(
        String baseUrl,
        String appId,
        String appSecret,
        String writebackPath,
        String writebackStatusPath,
        RetryProperties retry,
        ExecutorProperties executor) {

    public KingdeeProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("kingdee.base-url must not be blank");
        }
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("kingdee.app-id must not be blank");
        }
        if (appSecret == null || appSecret.isBlank()) {
            throw new IllegalArgumentException("kingdee.app-secret must not be blank");
        }
        if (writebackPath == null || writebackPath.isBlank() || "/".equals(writebackPath)) {
            throw new IllegalArgumentException("kingdee.writeback-path must not be blank");
        }
        if (writebackStatusPath == null || writebackStatusPath.isBlank() || "/".equals(writebackStatusPath)) {
            throw new IllegalArgumentException("kingdee.writeback-status-path must not be blank");
        }
        if (retry == null) {
            throw new IllegalArgumentException("kingdee.retry must not be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("kingdee.executor must not be null");
        }
    }

    public record RetryProperties(int maxAttempts, int baseDelaySeconds) {
    }

    public record ExecutorProperties(int corePoolSize, int maxPoolSize, int queueCapacity) {
    }
}

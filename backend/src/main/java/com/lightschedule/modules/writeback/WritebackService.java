package com.lightschedule.modules.writeback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightschedule.config.KingdeeProperties;
import com.lightschedule.integration.kingdee.KingdeeWritebackClient;
import com.lightschedule.integration.kingdee.KingdeeWritebackPayload;
import com.lightschedule.integration.kingdee.KingdeeWritebackPayloadMapper;
import com.lightschedule.integration.kingdee.KingdeeWritebackResult;
import com.lightschedule.integration.kingdee.KingdeeWritebackStatusResult;
import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import com.lightschedule.modules.validation.ScheduleValidationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WritebackService {

    private final ScheduleValidationService scheduleValidationService;
    private final WritebackAuditService writebackAuditService;
    private final KingdeeWritebackClient kingdeeWritebackClient;
    private final KingdeeWritebackPayloadMapper kingdeeWritebackPayloadMapper;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    @Autowired
    public WritebackService(
            ScheduleValidationService scheduleValidationService,
            WritebackAuditService writebackAuditService,
            KingdeeWritebackClient kingdeeWritebackClient,
            KingdeeWritebackPayloadMapper kingdeeWritebackPayloadMapper,
            ObjectMapper objectMapper,
            KingdeeProperties kingdeeProperties) {
        this(
                scheduleValidationService,
                writebackAuditService,
                kingdeeWritebackClient,
                kingdeeWritebackPayloadMapper,
                objectMapper,
                kingdeeProperties.retry().maxAttempts());
    }

    public WritebackService(
            ScheduleValidationService scheduleValidationService,
            WritebackAuditService writebackAuditService,
            KingdeeWritebackClient kingdeeWritebackClient,
            KingdeeWritebackPayloadMapper kingdeeWritebackPayloadMapper,
            ObjectMapper objectMapper,
            int maxAttempts) {
        this.scheduleValidationService = scheduleValidationService;
        this.writebackAuditService = writebackAuditService;
        this.kingdeeWritebackClient = kingdeeWritebackClient;
        this.kingdeeWritebackPayloadMapper = kingdeeWritebackPayloadMapper;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    public PublishResult publish(String draftId, List<ScheduledItem> items) {
        List<String> blockingIssues = scheduleValidationService.validateHardRules(items);
        if (!blockingIssues.isEmpty()) {
            throw new IllegalStateException(String.join(",", blockingIssues));
        }

        var payload = kingdeeWritebackPayloadMapper.map(draftId, items);
        WritebackAuditEntity audit;
        try {
            audit = writebackAuditService.createQueuedAudit(draftId, toJson(payload), maxAttempts);
        } catch (RuntimeException exception) {
            return new PublishResult(draftId, null, "validated", "pending");
        }
        KingdeeWritebackResult result;
        try {
            result = kingdeeWritebackClient.writeback(payload);
        } catch (RuntimeException exception) {
            result = new KingdeeWritebackResult(false, null, exception.getMessage());
        }
        if (result.success() && hasExternalRequestId(result)) {
            writebackAuditService.recordImmediateAttempt(audit.getId(), result);
            return new PublishResult(draftId, audit.getId(), "validated", "submitted");
        }
        WritebackAttemptDecision decision;
        if (result.success()) {
            result = new KingdeeWritebackResult(false, null, "writeback_missing_external_request_id");
            decision = WritebackAttemptDecision.terminalFailure();
        } else {
            decision = decideRetry(audit, result);
        }
        writebackAuditService.updateRetryAttempt(audit, decision, result, nextRetryAt(decision));
        return new PublishResult(draftId, audit.getId(), "validated", decision.nextStatus());
    }

    public void retryAudit(WritebackAuditEntity audit) {
        KingdeeWritebackPayload payload;
        try {
            payload = readPayload(audit.getPayloadJson());
        } catch (IllegalStateException exception) {
            var result = new KingdeeWritebackResult(false, null, exception.getMessage());
            writebackAuditService.updateRetryAttempt(audit, WritebackAttemptDecision.terminalFailure(), result, null);
            return;
        }
        KingdeeWritebackResult result;
        try {
            result = kingdeeWritebackClient.writeback(payload);
        } catch (RuntimeException exception) {
            result = new KingdeeWritebackResult(false, null, exception.getMessage());
        }
        WritebackAttemptDecision decision;
        if (result.success() && hasExternalRequestId(result)) {
            decision = new WritebackAttemptDecision(false, WritebackAuditStatus.SUBMITTED.name());
        } else if (result.success()) {
            result = new KingdeeWritebackResult(false, null, "writeback_missing_external_request_id");
            decision = WritebackAttemptDecision.terminalFailure();
        } else {
            decision = decideRetry(audit, result);
        }
        writebackAuditService.updateRetryAttempt(audit, decision, result, nextRetryAt(decision));
    }

    public WritebackStatus status(String auditId) {
        WritebackAuditEntity audit = writebackAuditService.getById(auditId);
        if (audit == null) {
            throw new NoSuchElementException("writeback audit not found");
        }
        if (isSubmittedAuditMissingExternalRequestId(audit)) {
            writebackAuditService.refreshSubmittedAudit(audit, WritebackAuditStatus.TERMINAL_FAILED, "writeback_missing_external_request_id");
            audit.setStatus(WritebackAuditStatus.TERMINAL_FAILED.name());
            audit.setMessage("writeback_missing_external_request_id");
            audit.setRetryable(false);
            audit.setNextRetryAt(null);
        } else if (shouldRefreshSubmittedStatus(audit)) {
            try {
                KingdeeWritebackStatusResult result = kingdeeWritebackClient.queryStatus(audit.getExternalRequestId());
                if (result.completed()) {
                    WritebackAuditStatus refreshedStatus = result.success() ? WritebackAuditStatus.SUCCEEDED : WritebackAuditStatus.TERMINAL_FAILED;
                    writebackAuditService.refreshSubmittedAudit(audit, refreshedStatus, result.message());
                    audit.setStatus(refreshedStatus.name());
                    audit.setMessage(result.message());
                    audit.setRetryable(false);
                    audit.setNextRetryAt(null);
                }
            } catch (RuntimeException exception) {
            }
        }
        return new WritebackStatus(
                audit.getId(),
                audit.getDraftId(),
                "validated",
                mapWritebackStatus(audit.getStatus()),
                audit.getMessage(),
                Boolean.TRUE.equals(audit.getRetryable()),
                audit.getAttemptCount() == null ? 0 : audit.getAttemptCount(),
                audit.getMaxAttempts() == null ? maxAttempts : audit.getMaxAttempts(),
                audit.getNextRetryAt() == null ? null : audit.getNextRetryAt().toString());
    }

    private WritebackAttemptDecision decideRetry(WritebackAuditEntity audit, KingdeeWritebackResult result) {
        if (result.success()) {
            return WritebackAttemptDecision.succeeded();
        }
        if (audit.getAttemptCount() + 1 < audit.getMaxAttempts()) {
            return WritebackAttemptDecision.retryLater();
        }
        return WritebackAttemptDecision.terminalFailure();
    }

    private LocalDateTime nextRetryAt(WritebackAttemptDecision decision) {
        if (!decision.retryable()) {
            return null;
        }
        return LocalDateTime.now().plusSeconds(30);
    }

    private boolean hasExternalRequestId(KingdeeWritebackResult result) {
        return result != null
                && result.externalRequestId() != null
                && !result.externalRequestId().isBlank();
    }

    private boolean isSubmittedAuditMissingExternalRequestId(WritebackAuditEntity audit) {
        return audit != null
                && WritebackAuditStatus.SUBMITTED.name().equals(audit.getStatus())
                && (audit.getExternalRequestId() == null || audit.getExternalRequestId().isBlank());
    }

    private boolean shouldRefreshSubmittedStatus(WritebackAuditEntity audit) {
        return audit != null
                && WritebackAuditStatus.SUBMITTED.name().equals(audit.getStatus())
                && kingdeeWritebackClient != null
                && audit.getExternalRequestId() != null
                && !audit.getExternalRequestId().isBlank();
    }

    private String mapWritebackStatus(String auditStatus) {
        if (WritebackAuditStatus.QUEUED.name().equals(auditStatus)) {
            return "pending";
        }
        if (WritebackAuditStatus.SUBMITTED.name().equals(auditStatus)) {
            return "submitted";
        }
        return auditStatus;
    }

    private KingdeeWritebackPayload readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new IllegalStateException("writeback_payload_deserialize_failed");
        }
        try {
            return objectMapper.readValue(payloadJson, KingdeeWritebackPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("writeback_payload_deserialize_failed", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("writeback_payload_serialize_failed", exception);
        }
    }

    public record PublishResult(String draftId, String auditId, String status, String writebackStatus) {
    }

    public record WritebackStatus(
            String auditId,
            String draftId,
            String status,
            String writebackStatus,
            String message,
            boolean retryable,
            int attemptCount,
            int maxAttempts,
            String nextRetryAt) {
    }
}

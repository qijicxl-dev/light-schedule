package com.lightschedule.modules.writeback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lightschedule.integration.kingdee.KingdeeWritebackClient;
import com.lightschedule.integration.kingdee.KingdeeWritebackPayload;
import com.lightschedule.integration.kingdee.KingdeeWritebackPayloadMapper;
import com.lightschedule.integration.kingdee.KingdeeWritebackResult;
import com.lightschedule.integration.kingdee.KingdeeWritebackStatusResult;
import com.lightschedule.modules.scheduling.InitialSchedulingService.ScheduledItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightschedule.modules.validation.ScheduleValidationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class WritebackServiceTest {

    @Test
    void shouldBlockPublishWhenScheduledItemsContainResourceConflict() {
        WritebackService service = new WritebackService(new ScheduleValidationService(), mock(WritebackAuditService.class), null, null, new ObjectMapper(), 1);

        assertThatThrownBy(() -> service.publish("draft-1", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z"),
                new ScheduledItem("TASK-002", "LINE-A", "2026-04-24T09:30:00Z", "2026-04-24T11:00:00Z")
        )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resource_conflict");
    }

    @Test
    void shouldFallbackToPendingPublishWhenAuditPersistenceFails() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        when(auditService.createQueuedAudit(eq("draft-1"), any(), anyInt()))
                .thenThrow(new IllegalStateException("db unavailable"));

        WritebackService service = new WritebackService(new ScheduleValidationService(), auditService, null, new KingdeeWritebackPayloadMapper(), new ObjectMapper(), 3);

        WritebackService.PublishResult result = service.publish("draft-1", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z")
        ));

        assertThat(result.draftId()).isEqualTo("draft-1");
        assertThat(result.auditId()).isNull();
        assertThat(result.status()).isEqualTo("validated");
        assertThat(result.writebackStatus()).isEqualTo("pending");
    }

    @Test
    void shouldDispatchMappedPayloadAndReturnSubmittedStatus() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity queuedAudit = new WritebackAuditEntity();
        queuedAudit.setId("audit-1");
        when(auditService.createQueuedAudit(eq("draft-1"), any(String.class), anyInt())).thenReturn(queuedAudit);

        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.writeback(any(KingdeeWritebackPayload.class)))
                .thenReturn(new KingdeeWritebackResult(true, "REQ-1", "submitted"));

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        WritebackService.PublishResult result = service.publish("draft-1", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z")
        ));

        verify(writebackClient).writeback(any(KingdeeWritebackPayload.class));
        assertThat(result.draftId()).isEqualTo("draft-1");
        assertThat(result.status()).isEqualTo("validated");
        assertThat(result.writebackStatus()).isEqualTo("submitted");
    }

    @Test
    void shouldFallbackToPendingPublishWhenRealWritebackAuditPersistenceFails() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        when(auditService.createQueuedAudit(eq("draft-1"), any(String.class), anyInt()))
                .thenThrow(new IllegalStateException("db unavailable"));

        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        WritebackService.PublishResult result = service.publish("draft-1", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z")
        ));

        verify(writebackClient, never()).writeback(any(KingdeeWritebackPayload.class));
        assertThat(result.draftId()).isEqualTo("draft-1");
        assertThat(result.auditId()).isNull();
        assertThat(result.status()).isEqualTo("validated");
        assertThat(result.writebackStatus()).isEqualTo("pending");
    }

    @Test
    void shouldTreatSuccessfulImmediateWritebackWithoutExternalRequestIdAsTerminalFailure() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity queuedAudit = new WritebackAuditEntity();
        queuedAudit.setId("audit-1");
        queuedAudit.setAttemptCount(0);
        queuedAudit.setMaxAttempts(3);
        when(auditService.createQueuedAudit(eq("draft-1"), any(String.class), anyInt())).thenReturn(queuedAudit);

        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.writeback(any(KingdeeWritebackPayload.class)))
                .thenReturn(new KingdeeWritebackResult(true, null, "submitted"));

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        WritebackService.PublishResult result = service.publish("draft-1", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z")
        ));

        assertThat(result.writebackStatus()).isEqualTo("TERMINAL_FAILED");
        verify(auditService).updateRetryAttempt(
                eq(queuedAudit),
                eq(new WritebackAttemptDecision(false, "TERMINAL_FAILED")),
                eq(new KingdeeWritebackResult(false, null, "writeback_missing_external_request_id")),
                eq(null));
        verify(auditService, never()).recordImmediateAttempt(any(), any());
    }

    @Test
    void shouldReturnRetryableFailedWhenImmediateWritebackFailsBeforeMaxAttempts() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity queuedAudit = new WritebackAuditEntity();
        queuedAudit.setId("audit-1");
        queuedAudit.setAttemptCount(0);
        queuedAudit.setMaxAttempts(3);
        when(auditService.createQueuedAudit(eq("draft-1"), any(String.class), anyInt())).thenReturn(queuedAudit);

        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.writeback(any(KingdeeWritebackPayload.class)))
                .thenReturn(new KingdeeWritebackResult(false, "REQ-1", "temporary failure"));

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        WritebackService.PublishResult result = service.publish("draft-1", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z")
        ));

        assertThat(result.writebackStatus()).isEqualTo("RETRYABLE_FAILED");
    }

    @Test
    void shouldReturnRetryableFailedWhenImmediateWritebackThrowsBeforeMaxAttempts() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity queuedAudit = new WritebackAuditEntity();
        queuedAudit.setId("audit-1");
        queuedAudit.setAttemptCount(0);
        queuedAudit.setMaxAttempts(3);
        when(auditService.createQueuedAudit(eq("draft-1"), any(String.class), anyInt())).thenReturn(queuedAudit);

        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.writeback(any(KingdeeWritebackPayload.class))).thenThrow(new IllegalStateException("kingdee timeout"));

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        WritebackService.PublishResult result = service.publish("draft-1", List.of(
                new ScheduledItem("TASK-001", "LINE-A", "2026-04-24T08:00:00Z", "2026-04-24T10:00:00Z")
        ));

        assertThat(result.writebackStatus()).isEqualTo("RETRYABLE_FAILED");
        verify(auditService).updateRetryAttempt(
                eq(queuedAudit),
                eq(new WritebackAttemptDecision(true, "RETRYABLE_FAILED")),
                eq(new KingdeeWritebackResult(false, null, "kingdee timeout")),
                any());
    }

    @Test
    void shouldRetryAuditWithPersistedPayloadAndUpdateAttempt() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.writeback(any(KingdeeWritebackPayload.class)))
                .thenReturn(new KingdeeWritebackResult(false, "REQ-2", "temporary failure"));

        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-1");
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setPayloadJson("""
                {"draftId":"draft-1","items":[{"workOrderCode":"TASK-001","resourceId":"LINE-A","plannedStartAt":"2026-04-24T08:00:00Z","plannedEndAt":"2026-04-24T10:00:00Z","sequenceNo":"1"}]}
                """);

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        service.retryAudit(audit);

        verify(writebackClient).writeback(any(KingdeeWritebackPayload.class));
        verify(auditService).updateRetryAttempt(eq(audit), any(WritebackAttemptDecision.class), eq(new KingdeeWritebackResult(false, "REQ-2", "temporary failure")), any());
    }

    @Test
    void shouldMarkRetrySuccessAsSubmittedUntilExternalStatusConfirmsCompletion() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.writeback(any(KingdeeWritebackPayload.class)))
                .thenReturn(new KingdeeWritebackResult(true, "REQ-3", "accepted by kingdee"));

        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-1");
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setPayloadJson("""
                {"draftId":"draft-1","items":[{"workOrderCode":"TASK-001","resourceId":"LINE-A","plannedStartAt":"2026-04-24T08:00:00Z","plannedEndAt":"2026-04-24T10:00:00Z","sequenceNo":"1"}]}
                """);

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        service.retryAudit(audit);

        verify(auditService).updateRetryAttempt(eq(audit), eq(new WritebackAttemptDecision(false, "SUBMITTED")), eq(new KingdeeWritebackResult(true, "REQ-3", "accepted by kingdee")), eq(null));
    }

    @Test
    void shouldMarkRetrySuccessWithoutExternalRequestIdAsTerminalFailure() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.writeback(any(KingdeeWritebackPayload.class)))
                .thenReturn(new KingdeeWritebackResult(true, null, "accepted by kingdee"));

        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-1");
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setPayloadJson("""
                {"draftId":"draft-1","items":[{"workOrderCode":"TASK-001","resourceId":"LINE-A","plannedStartAt":"2026-04-24T08:00:00Z","plannedEndAt":"2026-04-24T10:00:00Z","sequenceNo":"1"}]}
                """);

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        service.retryAudit(audit);

        verify(auditService).updateRetryAttempt(
                eq(audit),
                eq(new WritebackAttemptDecision(false, "TERMINAL_FAILED")),
                eq(new KingdeeWritebackResult(false, null, "writeback_missing_external_request_id")),
                eq(null));
    }

    @Test
    void shouldMarkRetryAsTerminalFailureWhenPersistedPayloadCannotBeRead() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);

        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-1");
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setPayloadJson("not-json");

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        service.retryAudit(audit);

        verify(writebackClient, never()).writeback(any(KingdeeWritebackPayload.class));
        verify(auditService).updateRetryAttempt(
                eq(audit),
                eq(new WritebackAttemptDecision(false, "TERMINAL_FAILED")),
                eq(new KingdeeWritebackResult(false, null, "writeback_payload_deserialize_failed")),
                eq(null));
    }

    @Test
    void shouldRetryLaterWhenRetryWritebackThrowsBeforeMaxAttempts() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.writeback(any(KingdeeWritebackPayload.class))).thenThrow(new IllegalStateException("kingdee timeout"));

        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-1");
        audit.setAttemptCount(1);
        audit.setMaxAttempts(4);
        audit.setPayloadJson("""
                {"draftId":"draft-1","items":[{"workOrderCode":"TASK-001","resourceId":"LINE-A","plannedStartAt":"2026-04-24T08:00:00Z","plannedEndAt":"2026-04-24T10:00:00Z","sequenceNo":"1"}]}
                """);

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                4);

        service.retryAudit(audit);

        verify(auditService).updateRetryAttempt(
                eq(audit),
                eq(new WritebackAttemptDecision(true, "RETRYABLE_FAILED")),
                eq(new KingdeeWritebackResult(false, null, "kingdee timeout")),
                any());
    }

    @Test
    void shouldMarkRetryAsTerminalFailureWhenPersistedPayloadIsMissing() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);

        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-1");
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setPayloadJson(null);

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        service.retryAudit(audit);

        verify(writebackClient, never()).writeback(any(KingdeeWritebackPayload.class));
        verify(auditService).updateRetryAttempt(
                eq(audit),
                eq(new WritebackAttemptDecision(false, "TERMINAL_FAILED")),
                eq(new KingdeeWritebackResult(false, null, "writeback_payload_deserialize_failed")),
                eq(null));
    }

    @Test
    void shouldMapQueuedAuditStatusToPendingWhenQueryingWritebackStatus() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-1");
        audit.setDraftId("draft-1");
        audit.setStatus(WritebackAuditStatus.QUEUED.name());
        audit.setMessage("queued");
        audit.setRetryable(false);
        audit.setAttemptCount(0);
        audit.setMaxAttempts(3);
        when(auditService.getById("audit-1")).thenReturn(audit);

        WritebackService service = new WritebackService(new ScheduleValidationService(), auditService, null, new KingdeeWritebackPayloadMapper(), new ObjectMapper(), 3);

        WritebackService.WritebackStatus status = service.status("audit-1");

        assertThat(status.writebackStatus()).isEqualTo("pending");
        assertThat(status.message()).isEqualTo("queued");
        assertThat(status.attemptCount()).isEqualTo(0);
    }

    @Test
    void shouldMapSubmittedAuditStatusToSubmittedWhenQueryingWritebackStatus() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-1");
        audit.setDraftId("draft-1");
        audit.setStatus("SUBMITTED");
        audit.setMessage("accepted by kingdee");
        audit.setRetryable(false);
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setExternalRequestId("REQ-1");
        when(auditService.getById("audit-1")).thenReturn(audit);

        WritebackService service = new WritebackService(new ScheduleValidationService(), auditService, null, new KingdeeWritebackPayloadMapper(), new ObjectMapper(), 3);

        WritebackService.WritebackStatus status = service.status("audit-1");

        assertThat(status.writebackStatus()).isEqualTo("submitted");
        assertThat(status.attemptCount()).isEqualTo(1);
        assertThat(status.retryable()).isFalse();
    }

    @Test
    void shouldTreatSubmittedAuditWithoutExternalRequestIdAsTerminalFailure() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-1");
        audit.setDraftId("draft-1");
        audit.setStatus(WritebackAuditStatus.SUBMITTED.name());
        audit.setMessage("accepted by kingdee");
        audit.setRetryable(false);
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setExternalRequestId(null);
        when(auditService.getById("audit-1")).thenReturn(audit);

        WritebackService service = new WritebackService(new ScheduleValidationService(), auditService, null, new KingdeeWritebackPayloadMapper(), new ObjectMapper(), 3);

        WritebackService.WritebackStatus status = service.status("audit-1");

        verify(auditService).refreshSubmittedAudit(audit, WritebackAuditStatus.TERMINAL_FAILED, "writeback_missing_external_request_id");
        assertThat(status.writebackStatus()).isEqualTo("TERMINAL_FAILED");
        assertThat(status.message()).isEqualTo("writeback_missing_external_request_id");
        assertThat(status.retryable()).isFalse();
    }

    @Test
    void shouldRefreshSubmittedAuditToSucceededWhenExternalStatusShowsCompletion() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-2");
        audit.setDraftId("draft-2");
        audit.setStatus(WritebackAuditStatus.SUBMITTED.name());
        audit.setMessage("accepted by kingdee");
        audit.setRetryable(false);
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setExternalRequestId("REQ-2");
        when(auditService.getById("audit-2")).thenReturn(audit);

        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.queryStatus("REQ-2"))
                .thenReturn(new KingdeeWritebackStatusResult(true, true, "writeback completed"));

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        WritebackService.WritebackStatus status = service.status("audit-2");

        verify(auditService).refreshSubmittedAudit(audit, WritebackAuditStatus.SUCCEEDED, "writeback completed");
        assertThat(status.writebackStatus()).isEqualTo("SUCCEEDED");
        assertThat(status.message()).isEqualTo("writeback completed");
    }

    @Test
    void shouldKeepSubmittedAuditStatusWhenExternalStatusIsNotCompleted() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-2");
        audit.setDraftId("draft-2");
        audit.setStatus(WritebackAuditStatus.SUBMITTED.name());
        audit.setMessage("accepted by kingdee");
        audit.setRetryable(false);
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setExternalRequestId("REQ-2");
        when(auditService.getById("audit-2")).thenReturn(audit);

        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.queryStatus("REQ-2"))
                .thenReturn(new KingdeeWritebackStatusResult(false, false, "processing"));

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        WritebackService.WritebackStatus status = service.status("audit-2");

        assertThat(status.writebackStatus()).isEqualTo("submitted");
        assertThat(status.message()).isEqualTo("accepted by kingdee");
        verify(auditService, never()).refreshSubmittedAudit(any(), any(), any());
    }

    @Test
    void shouldKeepSubmittedAuditStatusWhenExternalStatusQueryFails() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-2");
        audit.setDraftId("draft-2");
        audit.setStatus(WritebackAuditStatus.SUBMITTED.name());
        audit.setMessage("accepted by kingdee");
        audit.setRetryable(false);
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setExternalRequestId("REQ-2");
        when(auditService.getById("audit-2")).thenReturn(audit);

        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.queryStatus("REQ-2")).thenThrow(new IllegalStateException("kingdee timeout"));

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        WritebackService.WritebackStatus status = service.status("audit-2");

        assertThat(status.writebackStatus()).isEqualTo("submitted");
        assertThat(status.message()).isEqualTo("accepted by kingdee");
        assertThat(status.retryable()).isFalse();
        verify(auditService, never()).refreshSubmittedAudit(any(), any(), any());
    }

    @Test
    void shouldRefreshSubmittedAuditToTerminalFailedWhenExternalStatusShowsRejection() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-2");
        audit.setDraftId("draft-2");
        audit.setStatus(WritebackAuditStatus.SUBMITTED.name());
        audit.setMessage("accepted by kingdee");
        audit.setRetryable(false);
        audit.setAttemptCount(1);
        audit.setMaxAttempts(3);
        audit.setExternalRequestId("REQ-2");
        when(auditService.getById("audit-2")).thenReturn(audit);

        KingdeeWritebackClient writebackClient = mock(KingdeeWritebackClient.class);
        when(writebackClient.queryStatus("REQ-2"))
                .thenReturn(new KingdeeWritebackStatusResult(true, false, "Kingdee rejected payload"));

        WritebackService service = new WritebackService(
                new ScheduleValidationService(),
                auditService,
                writebackClient,
                new KingdeeWritebackPayloadMapper(),
                new ObjectMapper(),
                3);

        WritebackService.WritebackStatus status = service.status("audit-2");

        verify(auditService).refreshSubmittedAudit(audit, WritebackAuditStatus.TERMINAL_FAILED, "Kingdee rejected payload");
        assertThat(status.writebackStatus()).isEqualTo("TERMINAL_FAILED");
        assertThat(status.message()).isEqualTo("Kingdee rejected payload");
    }

    @Test
    void shouldKeepSucceededAuditStatusWhenQueryingWritebackStatus() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-2");
        audit.setDraftId("draft-2");
        audit.setStatus(WritebackAuditStatus.SUCCEEDED.name());
        audit.setMessage("writeback completed");
        audit.setRetryable(false);
        audit.setAttemptCount(2);
        audit.setMaxAttempts(3);
        when(auditService.getById("audit-2")).thenReturn(audit);

        WritebackService service = new WritebackService(new ScheduleValidationService(), auditService, null, new KingdeeWritebackPayloadMapper(), new ObjectMapper(), 3);

        WritebackService.WritebackStatus status = service.status("audit-2");

        assertThat(status.writebackStatus()).isEqualTo("SUCCEEDED");
        assertThat(status.message()).isEqualTo("writeback completed");
    }

    @Test
    void shouldThrowNotFoundWhenWritebackAuditDoesNotExist() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        when(auditService.getById("missing-audit")).thenReturn(null);

        WritebackService service = new WritebackService(new ScheduleValidationService(), auditService, null, new KingdeeWritebackPayloadMapper(), new ObjectMapper(), 3);

        assertThatThrownBy(() -> service.status("missing-audit"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("writeback audit not found");
    }

    @Test
    void shouldReturnAuditStatusTruthWhenQueryingWritebackStatus() {
        WritebackAuditService auditService = mock(WritebackAuditService.class);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-1");
        audit.setDraftId("draft-9");
        audit.setStatus(WritebackAuditStatus.TERMINAL_FAILED.name());
        audit.setMessage("Kingdee rejected payload");
        audit.setRetryable(false);
        audit.setAttemptCount(3);
        audit.setMaxAttempts(3);
        audit.setNextRetryAt(LocalDateTime.parse("2026-04-24T10:30:00"));
        when(auditService.getById("audit-1")).thenReturn(audit);

        WritebackService service = new WritebackService(new ScheduleValidationService(), auditService, null, new KingdeeWritebackPayloadMapper(), new ObjectMapper(), 3);

        WritebackService.WritebackStatus status = service.status("audit-1");

        assertThat(status.auditId()).isEqualTo("audit-1");
        assertThat(status.draftId()).isEqualTo("draft-9");
        assertThat(status.status()).isEqualTo("validated");
        assertThat(status.writebackStatus()).isEqualTo("TERMINAL_FAILED");
        assertThat(status.message()).isEqualTo("Kingdee rejected payload");
        assertThat(status.retryable()).isFalse();
        assertThat(status.attemptCount()).isEqualTo(3);
        assertThat(status.maxAttempts()).isEqualTo(3);
        assertThat(status.nextRetryAt()).isEqualTo("2026-04-24T10:30");
    }
}

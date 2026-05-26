package com.lightschedule.modules.writeback;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WritebackRetrySchedulerTest {

    @Test
    void shouldRetryDueAudits() {
        WritebackAuditService auditService = Mockito.mock(WritebackAuditService.class);
        WritebackService writebackService = Mockito.mock(WritebackService.class);
        WritebackRetryScheduler scheduler = new WritebackRetryScheduler(auditService, writebackService);
        WritebackAuditEntity entity = new WritebackAuditEntity();
        entity.setId("audit-1");
        when(auditService.findDueRetries(Mockito.any(LocalDateTime.class))).thenReturn(List.of(entity));

        scheduler.retryDueAudits();

        verify(writebackService).retryAudit(entity);
    }

    @Test
    void shouldContinueRetryingRemainingAuditsWhenOneRetryFails() {
        WritebackAuditService auditService = Mockito.mock(WritebackAuditService.class);
        WritebackService writebackService = Mockito.mock(WritebackService.class);
        WritebackRetryScheduler scheduler = new WritebackRetryScheduler(auditService, writebackService);
        WritebackAuditEntity first = new WritebackAuditEntity();
        first.setId("audit-1");
        WritebackAuditEntity second = new WritebackAuditEntity();
        second.setId("audit-2");
        when(auditService.findDueRetries(Mockito.any(LocalDateTime.class))).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("retry failed")).when(writebackService).retryAudit(first);

        scheduler.retryDueAudits();

        verify(writebackService).retryAudit(first);
        verify(writebackService).retryAudit(second);
    }
}

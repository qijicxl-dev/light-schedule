package com.lightschedule.modules.writeback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.lightschedule.integration.kingdee.KingdeeWritebackResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WritebackAuditServiceTest {

    @Test
    void shouldPersistSubmittedStatusWhenImmediateAttemptSucceeds() {
        WritebackAuditMapper mapper = mock(WritebackAuditMapper.class);
        WritebackAuditService service = new WritebackAuditService(mapper);

        service.recordImmediateAttempt("audit-1", new KingdeeWritebackResult(true, "REQ-1", "accepted by kingdee"));

        ArgumentCaptor<WritebackAuditEntity> captor = ArgumentCaptor.forClass(WritebackAuditEntity.class);
        verify(mapper).updateById(captor.capture());
        WritebackAuditEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo("audit-1");
        assertThat(entity.getStatus()).isEqualTo("SUBMITTED");
        assertThat(entity.getExternalRequestId()).isEqualTo("REQ-1");
        assertThat(entity.getMessage()).isEqualTo("accepted by kingdee");
        assertThat(entity.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void shouldPersistSubmittedStatusWhenRetryAttemptSucceeds() {
        WritebackAuditMapper mapper = mock(WritebackAuditMapper.class);
        WritebackAuditService service = new WritebackAuditService(mapper);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-2");
        audit.setAttemptCount(1);

        service.updateRetryAttempt(
                audit,
                new WritebackAttemptDecision(false, "SUBMITTED"),
                new KingdeeWritebackResult(true, "REQ-2", "accepted by kingdee"),
                null);

        ArgumentCaptor<WritebackAuditEntity> captor = ArgumentCaptor.forClass(WritebackAuditEntity.class);
        verify(mapper).updateById(captor.capture());
        WritebackAuditEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo("audit-2");
        assertThat(entity.getStatus()).isEqualTo("SUBMITTED");
        assertThat(entity.getAttemptCount()).isEqualTo(2);
        assertThat(entity.getExternalRequestId()).isEqualTo("REQ-2");
        assertThat(entity.getMessage()).isEqualTo("accepted by kingdee");
        assertThat(entity.getResponseJson()).isEqualTo("accepted by kingdee");
        assertThat(entity.getRetryable()).isFalse();
        assertThat(entity.getNextRetryAt()).isNull();
    }

    @Test
    void shouldPersistResponseJsonWhenRefreshingSubmittedAudit() {
        WritebackAuditMapper mapper = mock(WritebackAuditMapper.class);
        WritebackAuditService service = new WritebackAuditService(mapper);
        WritebackAuditEntity audit = new WritebackAuditEntity();
        audit.setId("audit-2");

        service.refreshSubmittedAudit(audit, WritebackAuditStatus.TERMINAL_FAILED, "Kingdee rejected payload");

        ArgumentCaptor<WritebackAuditEntity> captor = ArgumentCaptor.forClass(WritebackAuditEntity.class);
        verify(mapper).updateById(captor.capture());
        WritebackAuditEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo("audit-2");
        assertThat(entity.getStatus()).isEqualTo("TERMINAL_FAILED");
        assertThat(entity.getMessage()).isEqualTo("Kingdee rejected payload");
        assertThat(entity.getResponseJson()).isEqualTo("Kingdee rejected payload");
    }
}

package com.lightschedule.modules.writeback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightschedule.integration.kingdee.KingdeeWritebackResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WritebackAuditService {

    private final WritebackAuditMapper mapper;

    public WritebackAuditService(WritebackAuditMapper mapper) {
        this.mapper = mapper;
    }

    public WritebackAuditEntity createQueuedAudit(String draftId, String payloadJson, int maxAttempts) {
        WritebackAuditEntity entity = new WritebackAuditEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setDraftId(draftId);
        entity.setStatus(WritebackAuditStatus.QUEUED.name());
        entity.setMessage("queued");
        entity.setAttemptCount(0);
        entity.setMaxAttempts(maxAttempts);
        entity.setRetryable(false);
        entity.setPayloadJson(payloadJson);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return entity;
    }

    public WritebackAuditEntity getById(String auditId) {
        return mapper.selectById(auditId);
    }

    public void recordImmediateAttempt(String auditId, KingdeeWritebackResult result) {
        WritebackAuditEntity entity = new WritebackAuditEntity();
        entity.setId(auditId);
        entity.setStatus(result.success() ? WritebackAuditStatus.SUBMITTED.name() : WritebackAuditStatus.TERMINAL_FAILED.name());
        entity.setMessage(result.message());
        entity.setExternalRequestId(result.externalRequestId());
        entity.setResponseJson(result.message());
        entity.setAttemptCount(1);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    public List<WritebackAuditEntity> findDueRetries(LocalDateTime now) {
        return mapper.selectList(new LambdaQueryWrapper<WritebackAuditEntity>()
                .eq(WritebackAuditEntity::getStatus, WritebackAuditStatus.RETRYABLE_FAILED.name())
                .eq(WritebackAuditEntity::getRetryable, true)
                .le(WritebackAuditEntity::getNextRetryAt, now));
    }

    public void updateRetryAttempt(
            WritebackAuditEntity audit,
            WritebackAttemptDecision decision,
            KingdeeWritebackResult result,
            LocalDateTime nextRetryAt) {
        WritebackAuditEntity entity = new WritebackAuditEntity();
        entity.setId(audit.getId());
        entity.setStatus(decision.nextStatus());
        entity.setRetryable(decision.retryable());
        entity.setAttemptCount(audit.getAttemptCount() + 1);
        entity.setMessage(result.message());
        entity.setExternalRequestId(result.externalRequestId());
        entity.setResponseJson(result.message());
        entity.setNextRetryAt(nextRetryAt);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    public void refreshSubmittedAudit(WritebackAuditEntity audit, WritebackAuditStatus status, String message) {
        WritebackAuditEntity entity = new WritebackAuditEntity();
        entity.setId(audit.getId());
        entity.setStatus(status.name());
        entity.setRetryable(false);
        entity.setMessage(message);
        entity.setResponseJson(message);
        entity.setNextRetryAt(null);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }
}

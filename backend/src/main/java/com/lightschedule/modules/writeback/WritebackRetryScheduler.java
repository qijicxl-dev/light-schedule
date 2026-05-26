package com.lightschedule.modules.writeback;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WritebackRetryScheduler {

    private final WritebackAuditService writebackAuditService;
    private final WritebackService writebackService;

    public WritebackRetryScheduler(WritebackAuditService writebackAuditService, WritebackService writebackService) {
        this.writebackAuditService = writebackAuditService;
        this.writebackService = writebackService;
    }

    @Scheduled(fixedDelay = 30000)
    public void retryDueAudits() {
        List<WritebackAuditEntity> dueAudits = writebackAuditService.findDueRetries(LocalDateTime.now());
        dueAudits.forEach(audit -> {
            try {
                writebackService.retryAudit(audit);
            } catch (RuntimeException exception) {
            }
        });
    }
}

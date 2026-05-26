package com.lightschedule.integration.kingdee;

public interface KingdeeWritebackClient {
    KingdeeWritebackResult writeback(KingdeeWritebackPayload payload);

    KingdeeWritebackStatusResult queryStatus(String externalRequestId);
}

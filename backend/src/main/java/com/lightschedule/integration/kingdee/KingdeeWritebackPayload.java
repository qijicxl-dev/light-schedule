package com.lightschedule.integration.kingdee;

import java.util.List;

public record KingdeeWritebackPayload(String draftId, List<KingdeeWritebackItemPayload> items) {
}

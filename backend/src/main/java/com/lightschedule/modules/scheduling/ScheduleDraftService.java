package com.lightschedule.modules.scheduling;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class ScheduleDraftService {

    private final ScheduleDraftMapper mapper;

    public ScheduleDraftService(ScheduleDraftMapper mapper) {
        this.mapper = mapper;
    }

    public ScheduleDraftEntity save(String draftId, String draftPayload) {
        ScheduleDraftEntity existing = mapper.selectById(draftId);
        if (existing != null) {
            existing.setDraftPayload(draftPayload);
            existing.setVersionNo(existing.getVersionNo() + 1);
            existing.setStatus("active");
            mapper.updateById(existing);
            return existing;
        }

        ScheduleDraftEntity entity = new ScheduleDraftEntity();
        entity.setId(draftId);
        entity.setVersionNo(1);
        entity.setStatus("active");
        entity.setDraftPayload(draftPayload);
        entity.setCreatedAt(LocalDateTime.now());
        mapper.insert(entity);
        return entity;
    }

    public ScheduleDraftEntity findById(String draftId) {
        ScheduleDraftEntity entity = mapper.selectById(draftId);
        if (entity == null) {
            throw new NoSuchElementException("schedule draft not found: " + draftId);
        }
        return entity;
    }
}

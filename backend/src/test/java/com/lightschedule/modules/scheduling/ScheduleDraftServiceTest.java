package com.lightschedule.modules.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScheduleDraftServiceTest {

    @Test
    void shouldPersistDraftWithActiveStatusAndVersionOne() {
        ScheduleDraftMapper mapper = mock(ScheduleDraftMapper.class);
        ScheduleDraftService service = new ScheduleDraftService(mapper);

        service.save("draft-1", "{\"draftId\":\"draft-1\"}");

        ArgumentCaptor<ScheduleDraftEntity> captor = ArgumentCaptor.forClass(ScheduleDraftEntity.class);
        verify(mapper).insert(captor.capture());
        ScheduleDraftEntity entity = captor.getValue();
        assertThat(entity.getId()).isEqualTo("draft-1");
        assertThat(entity.getVersionNo()).isEqualTo(1);
        assertThat(entity.getStatus()).isEqualTo("active");
        assertThat(entity.getDraftPayload()).isEqualTo("{\"draftId\":\"draft-1\"}");
        assertThat(entity.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldReturnPersistedDraftWhenFoundById() {
        ScheduleDraftMapper mapper = mock(ScheduleDraftMapper.class);
        ScheduleDraftService service = new ScheduleDraftService(mapper);
        ScheduleDraftEntity persisted = new ScheduleDraftEntity();
        persisted.setId("draft-1");
        persisted.setVersionNo(1);
        persisted.setStatus("active");
        persisted.setDraftPayload("{\"draftId\":\"draft-1\"}");
        org.mockito.Mockito.when(mapper.selectById("draft-1")).thenReturn(persisted);

        ScheduleDraftEntity result = service.findById("draft-1");

        assertThat(result.getId()).isEqualTo("draft-1");
        assertThat(result.getDraftPayload()).isEqualTo("{\"draftId\":\"draft-1\"}");
    }

    @Test
    void shouldThrowWhenDraftNotFoundById() {
        ScheduleDraftMapper mapper = mock(ScheduleDraftMapper.class);
        ScheduleDraftService service = new ScheduleDraftService(mapper);
        org.mockito.Mockito.when(mapper.selectById("draft-missing")).thenReturn(null);

        assertThatThrownBy(() -> service.findById("draft-missing"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("schedule draft not found");
    }
}

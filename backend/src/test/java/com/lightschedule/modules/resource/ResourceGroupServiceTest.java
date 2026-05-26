package com.lightschedule.modules.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceGroupServiceTest {

    @Test
    void shouldResolveGroupNameFromSharedResourceCatalog() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        ResourceEntity entityA = new ResourceEntity();
        entityA.setResourceId("LINE-A");
        entityA.setGroupName("冲压组");
        entityA.setDefaultPlanner(true);
        ResourceEntity entityC = new ResourceEntity();
        entityC.setResourceId("LINE-C");
        entityC.setGroupName("装配组");
        entityC.setDefaultPlanner(false);
        when(mapper.selectList(null)).thenReturn(List.of(entityA, entityC));

        ResourceGroupService service = new ResourceGroupService(new ResourceCatalogService(mapper));

        assertThat(service.getGroupName("LINE-A")).isEqualTo("冲压组");
        assertThat(service.getGroupName("LINE-C")).isEqualTo("装配组");
        assertThat(service.getGroupName("LINE-UNKNOWN")).isEqualTo("同组资源");
    }
}

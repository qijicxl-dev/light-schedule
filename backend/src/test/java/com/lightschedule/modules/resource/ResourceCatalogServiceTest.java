package com.lightschedule.modules.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceCatalogServiceTest {

    @Test
    void shouldLoadResourcesFromDatabaseWhenMapperAvailable() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        ResourceEntity entity = new ResourceEntity();
        entity.setId("res-1");
        entity.setResourceId("LINE-A");
        entity.setGroupName("冲压组");
        entity.setDefaultPlanner(true);
        when(mapper.selectList(null)).thenReturn(List.of(entity));

        ResourceCatalogService service = new ResourceCatalogService(mapper);
        List<ResourceCatalogService.ResourceDefinition> resources = service.list();

        assertThat(resources).hasSize(1);
        assertThat(resources.get(0).resourceId()).isEqualTo("LINE-A");
        assertThat(resources.get(0).groupName()).isEqualTo("冲压组");
        assertThat(resources.get(0).defaultPlannerResource()).isTrue();
    }

    @Test
    void shouldReturnEmptyListWhenMapperReturnsEmptyList() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of());

        ResourceCatalogService service = new ResourceCatalogService(mapper);
        List<ResourceCatalogService.ResourceDefinition> resources = service.list();

        assertThat(resources).isEmpty();
    }

    @Test
    void shouldReturnDefaultPlannerResourceIdsFromDatabase() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        ResourceEntity entity1 = new ResourceEntity();
        entity1.setResourceId("LINE-A");
        entity1.setDefaultPlanner(true);
        ResourceEntity entity2 = new ResourceEntity();
        entity2.setResourceId("LINE-C");
        entity2.setDefaultPlanner(false);
        when(mapper.selectList(null)).thenReturn(List.of(entity1, entity2));

        ResourceCatalogService service = new ResourceCatalogService(mapper);
        List<String> ids = service.listDefaultPlannerResourceIds();

        assertThat(ids).containsExactly("LINE-A");
    }

    @Test
    void shouldReturnGroupNameFromDatabase() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        ResourceEntity entity = new ResourceEntity();
        entity.setResourceId("LINE-A");
        entity.setGroupName("冲压组");
        when(mapper.selectList(null)).thenReturn(List.of(entity));

        ResourceCatalogService service = new ResourceCatalogService(mapper);
        String groupName = service.getGroupName("LINE-A");

        assertThat(groupName).isEqualTo("冲压组");
    }

    @Test
    void shouldReturnFallbackGroupNameWhenResourceNotFound() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        when(mapper.selectList(null)).thenReturn(List.of());

        ResourceCatalogService service = new ResourceCatalogService(mapper);
        String groupName = service.getGroupName("UNKNOWN");

        assertThat(groupName).isEqualTo("同组资源");
    }

    @Test
    void shouldSaveResourceToDatabase() {
        ResourceMapper mapper = mock(ResourceMapper.class);

        ResourceCatalogService service = new ResourceCatalogService(mapper);
        ResourceCatalogService.ResourceDefinition result = service.save("LINE-D", "焊接组", false);

        assertThat(result.resourceId()).isEqualTo("LINE-D");
        assertThat(result.groupName()).isEqualTo("焊接组");
        assertThat(result.defaultPlannerResource()).isFalse();
        verify(mapper).insert(any(ResourceEntity.class));
    }

    @Test
    void shouldUpdateExistingResource() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        ResourceEntity existing = new ResourceEntity();
        existing.setId("res-1");
        existing.setResourceId("LINE-A");
        existing.setGroupName("冲压组");
        existing.setDefaultPlanner(true);
        when(mapper.selectOne(any())).thenReturn(existing);

        ResourceCatalogService service = new ResourceCatalogService(mapper);
        ResourceCatalogService.ResourceDefinition result = service.update("LINE-A", "冲压组-新", false);

        assertThat(result.groupName()).isEqualTo("冲压组-新");
        assertThat(result.defaultPlannerResource()).isFalse();
        verify(mapper).updateById(existing);
    }

    @Test
    void shouldDeleteResourceByResourceId() {
        ResourceMapper mapper = mock(ResourceMapper.class);

        ResourceCatalogService service = new ResourceCatalogService(mapper);
        service.delete("LINE-A");

        verify(mapper).delete(any());
    }
}

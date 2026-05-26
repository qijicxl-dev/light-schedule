package com.lightschedule.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lightschedule.modules.resource.ResourceCatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceControllerTest {

    @Test
    void shouldReturnResourcesFromService() {
        ResourceCatalogService service = mock(ResourceCatalogService.class);
        when(service.list()).thenReturn(List.of(
                new ResourceCatalogService.ResourceDefinition("LINE-A", "冲压组", true),
                new ResourceCatalogService.ResourceDefinition("LINE-B", "冲压组", true),
                new ResourceCatalogService.ResourceDefinition("LINE-C", "装配组", false)));

        ResourceController controller = new ResourceController(service);
        List<ResourceCatalogService.ResourceDefinition> result = controller.list();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).resourceId()).isEqualTo("LINE-A");
        assertThat(result.get(0).groupName()).isEqualTo("冲压组");
        assertThat(result.get(0).defaultPlannerResource()).isTrue();
        assertThat(result.get(2).resourceId()).isEqualTo("LINE-C");
        assertThat(result.get(2).defaultPlannerResource()).isFalse();
    }

    @Test
    void shouldCreateResource() {
        ResourceCatalogService service = mock(ResourceCatalogService.class);
        when(service.save(eq("LINE-D"), eq("焊接组"), anyBoolean()))
                .thenReturn(new ResourceCatalogService.ResourceDefinition("LINE-D", "焊接组", false));

        ResourceController controller = new ResourceController(service);
        ResourceCatalogService.ResourceDefinition result = controller.create(
                new ResourceSaveRequest("LINE-D", "焊接组", false));

        assertThat(result.resourceId()).isEqualTo("LINE-D");
        assertThat(result.groupName()).isEqualTo("焊接组");
        assertThat(result.defaultPlannerResource()).isFalse();
    }

    @Test
    void shouldUpdateResource() {
        ResourceCatalogService service = mock(ResourceCatalogService.class);
        when(service.update(eq("LINE-A"), eq("冲压组-新"), eq(false)))
                .thenReturn(new ResourceCatalogService.ResourceDefinition("LINE-A", "冲压组-新", false));

        ResourceController controller = new ResourceController(service);
        ResourceCatalogService.ResourceDefinition result = controller.update("LINE-A",
                new ResourceSaveRequest("LINE-A", "冲压组-新", false));

        assertThat(result.groupName()).isEqualTo("冲压组-新");
        assertThat(result.defaultPlannerResource()).isFalse();
    }

    @Test
    void shouldDeleteResource() {
        ResourceCatalogService service = mock(ResourceCatalogService.class);

        ResourceController controller = new ResourceController(service);
        controller.delete("LINE-A");

        verify(service).delete("LINE-A");
    }
}

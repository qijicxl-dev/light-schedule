package com.lightschedule.modules.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lightschedule.modules.resource.ResourceCatalogService;
import com.lightschedule.modules.resource.ResourceEntity;
import com.lightschedule.modules.resource.ResourceGroupService;
import com.lightschedule.modules.resource.ResourceMapper;
import com.lightschedule.modules.scheduling.InitialSchedulingService;
import com.lightschedule.modules.scheduling.PlannerScenarioService;
import java.util.List;
import org.junit.jupiter.api.Test;

class CapacityAnalysisOverviewServiceTest {

    private static ResourceCatalogService catalogWithMockData() {
        ResourceMapper mapper = mock(ResourceMapper.class);
        ResourceEntity entityA = new ResourceEntity();
        entityA.setResourceId("LINE-A");
        entityA.setGroupName("冲压组");
        entityA.setDefaultPlanner(true);
        ResourceEntity entityB = new ResourceEntity();
        entityB.setResourceId("LINE-B");
        entityB.setGroupName("冲压组");
        entityB.setDefaultPlanner(true);
        when(mapper.selectList(null)).thenReturn(List.of(entityA, entityB));
        return new ResourceCatalogService(mapper);
    }

    @Test
    void shouldBuildOverviewFromDefaultPlannerScenario() {
        ResourceCatalogService resourceCatalogService = catalogWithMockData();
        CapacityAnalysisOverviewService service =
                new CapacityAnalysisOverviewService(
                        new CapacityAssessmentService(),
                        new CapacityBucketService(),
                        new ResourceGroupService(resourceCatalogService),
                        resourceCatalogService,
                        new PlannerScenarioService(resourceCatalogService, null, null),
                        new InitialSchedulingService());

        CapacityAnalysisOverviewService.CapacityAnalysisOverview overview = service.buildOverview();

        assertThat(overview.trends()).containsExactly(new CapacityAnalysisOverviewService.CapacityTrend(
                "LINE-A",
                "2026-04-24 08:00",
                "feasible",
                0.44));
        assertThat(overview.groupDiffs()).containsExactly(new CapacityAnalysisOverviewService.CapacityGroupDiff(
                "冲压组",
                0.44));
        assertThat(overview.peakPeriods()).containsExactly(new CapacityAnalysisOverviewService.CapacityPeakPeriod(
                "2026-04-24 08:00",
                "tight",
                1.0));
    }
}

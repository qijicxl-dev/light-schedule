package com.lightschedule.modules.capacity;

import com.lightschedule.modules.resource.ResourceCatalogService;
import com.lightschedule.modules.resource.ResourceGroupService;
import com.lightschedule.modules.scheduling.InitialSchedulingService;
import com.lightschedule.modules.scheduling.PlannerScenarioService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CapacityAnalysisOverviewService {

    private final CapacityAssessmentService capacityAssessmentService;
    private final CapacityBucketService capacityBucketService;
    private final ResourceGroupService resourceGroupService;
    private final ResourceCatalogService resourceCatalogService;
    private final PlannerScenarioService plannerScenarioService;
    private final InitialSchedulingService initialSchedulingService;

    public CapacityAnalysisOverviewService(
            CapacityAssessmentService capacityAssessmentService,
            CapacityBucketService capacityBucketService,
            ResourceGroupService resourceGroupService,
            ResourceCatalogService resourceCatalogService,
            PlannerScenarioService plannerScenarioService,
            InitialSchedulingService initialSchedulingService) {
        this.capacityAssessmentService = capacityAssessmentService;
        this.capacityBucketService = capacityBucketService;
        this.resourceGroupService = resourceGroupService;
        this.resourceCatalogService = resourceCatalogService;
        this.plannerScenarioService = plannerScenarioService;
        this.initialSchedulingService = initialSchedulingService;
    }

    public CapacityAnalysisOverview buildOverview() {
        var scenario = plannerScenarioService.loadDefaultScenario();
        var scheduleResult = initialSchedulingService.build(new InitialSchedulingService.ScheduleInput(
                scenario.tasks().stream()
                        .map(task -> new InitialSchedulingService.Task(
                                task.taskId(),
                                task.resourceId(),
                                task.requiredMinutes(),
                                task.dependencyTaskIds()))
                        .toList(),
                scenario.startAt()));
        String trendResourceId = resourceCatalogService.listDefaultPlannerResourceIds().getFirst();
        int trendRequiredMinutes = scenario.tasks().stream()
                .filter(task -> task.resourceId().equals(trendResourceId))
                .mapToInt(PlannerScenarioService.PlannerTask::requiredMinutes)
                .sum();
        CapacityBucketService.BucketSummary trendBucket = capacityBucketService.summarize(trendRequiredMinutes, 480);
        CapacityAssessmentService.AssessmentResult trendAssessment =
                capacityAssessmentService.assessCoarse(trendBucket.requiredMinutes(), trendBucket.availableMinutes());

        List<String> sameGroupResourceIds = resourceCatalogService.listDefaultPlannerResourceIds().stream()
                .filter(resourceId -> resourceGroupService.getGroupName(resourceId)
                        .equals(resourceGroupService.getGroupName(trendResourceId)))
                .toList();
        int maxGroupRequiredMinutes = sameGroupResourceIds.stream()
                .mapToInt(resourceId -> scenario.tasks().stream()
                        .filter(task -> task.resourceId().equals(resourceId))
                        .mapToInt(PlannerScenarioService.PlannerTask::requiredMinutes)
                        .sum())
                .max()
                .orElse(0);
        int minGroupRequiredMinutes = sameGroupResourceIds.stream()
                .mapToInt(resourceId -> scenario.tasks().stream()
                        .filter(task -> task.resourceId().equals(resourceId))
                        .mapToInt(PlannerScenarioService.PlannerTask::requiredMinutes)
                        .sum())
                .min()
                .orElse(0);
        double groupGapRate = roundToTwoDecimals((double) (maxGroupRequiredMinutes - minGroupRequiredMinutes) / 480);

        var peakItem = scheduleResult.items().getFirst();
        int peakRequiredMinutes = scenario.tasks().stream()
                .filter(task -> task.taskId().equals(peakItem.taskId()))
                .mapToInt(PlannerScenarioService.PlannerTask::requiredMinutes)
                .findFirst()
                .orElse(0);
        CapacityBucketService.BucketSummary peakBucket = capacityBucketService.summarize(peakRequiredMinutes, 120);
        CapacityAssessmentService.AssessmentResult peakAssessment =
                capacityAssessmentService.assessCoarse(peakBucket.requiredMinutes(), peakBucket.availableMinutes());
        String bucketLabel = toBucketLabel(peakItem.startAt());
        String groupName = resourceGroupService.getGroupName(trendResourceId);

        // 能力分析先复用默认草稿的真实任务分钟数，避免和工作台/驾驶舱继续维护不同口径的样例桶。
        return new CapacityAnalysisOverview(
                List.of(new CapacityTrend(
                        trendResourceId,
                        bucketLabel,
                        trendAssessment.status(),
                        roundToTwoDecimals(trendAssessment.loadRate()))),
                List.of(new CapacityGroupDiff(groupName, groupGapRate)),
                List.of(new CapacityPeakPeriod(
                        bucketLabel,
                        peakAssessment.status(),
                        roundToTwoDecimals(peakAssessment.loadRate()))));
    }

    private String toBucketLabel(String startAt) {
        return startAt.replace('T', ' ').substring(0, 16);
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record CapacityAnalysisOverview(
            List<CapacityTrend> trends,
            List<CapacityGroupDiff> groupDiffs,
            List<CapacityPeakPeriod> peakPeriods) {
    }

    public record CapacityTrend(
            String resourceId,
            String bucketLabel,
            String status,
            double loadRate) {
    }

    public record CapacityGroupDiff(
            String groupName,
            double gapRate) {
    }

    public record CapacityPeakPeriod(
            String bucketLabel,
            String status,
            double loadRate) {
    }
}

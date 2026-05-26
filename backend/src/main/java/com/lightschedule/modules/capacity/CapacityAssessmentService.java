package com.lightschedule.modules.capacity;

import org.springframework.stereotype.Service;

@Service
public class CapacityAssessmentService {

    public AssessmentResult assessCoarse(int requiredMinutes, int availableMinutes) {
        double loadRate = (double) requiredMinutes / availableMinutes;
        if (loadRate > 1) {
            return new AssessmentResult("overloaded", loadRate);
        }
        if (loadRate >= 0.85) {
            return new AssessmentResult("tight", loadRate);
        }
        return new AssessmentResult("feasible", loadRate);
    }

    public AssessmentResult assessFine(int requiredMinutes, int availableMinutes, int usedMinutes, double warningLoadRate) {
        double projectedLoadRate = (double) (requiredMinutes + usedMinutes) / availableMinutes;
        if (projectedLoadRate >= warningLoadRate) {
            return new AssessmentResult("placeable_high_load", projectedLoadRate);
        }
        return new AssessmentResult("placeable", projectedLoadRate);
    }

    public record AssessmentResult(String status, double loadRate) {
    }
}

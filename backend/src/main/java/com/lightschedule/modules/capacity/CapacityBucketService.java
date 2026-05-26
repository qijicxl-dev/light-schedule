package com.lightschedule.modules.capacity;

import org.springframework.stereotype.Service;

@Service
public class CapacityBucketService {

    public BucketSummary summarize(int requiredMinutes, int availableMinutes) {
        return new BucketSummary(requiredMinutes, availableMinutes);
    }

    public record BucketSummary(int requiredMinutes, int availableMinutes) {
    }
}

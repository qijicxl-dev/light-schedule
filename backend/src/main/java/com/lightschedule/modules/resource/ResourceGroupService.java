package com.lightschedule.modules.resource;

import org.springframework.stereotype.Service;

@Service
public class ResourceGroupService {

    private final ResourceCatalogService resourceCatalogService;

    public ResourceGroupService(ResourceCatalogService resourceCatalogService) {
        this.resourceCatalogService = resourceCatalogService;
    }

    public String getGroupName(String resourceId) {
        return resourceCatalogService.getGroupName(resourceId);
    }
}

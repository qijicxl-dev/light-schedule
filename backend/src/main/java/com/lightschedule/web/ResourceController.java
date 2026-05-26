package com.lightschedule.web;

import com.lightschedule.modules.resource.ResourceCatalogService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceCatalogService resourceCatalogService;

    public ResourceController(ResourceCatalogService resourceCatalogService) {
        this.resourceCatalogService = resourceCatalogService;
    }

    @GetMapping
    public List<ResourceCatalogService.ResourceDefinition> list() {
        return resourceCatalogService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceCatalogService.ResourceDefinition create(@RequestBody ResourceSaveRequest request) {
        return resourceCatalogService.save(request.resourceId(), request.groupName(), request.defaultPlanner());
    }

    @PutMapping("/{resourceId}")
    public ResourceCatalogService.ResourceDefinition update(@PathVariable("resourceId") String resourceId,
            @RequestBody ResourceSaveRequest request) {
        return resourceCatalogService.update(resourceId, request.groupName(), request.defaultPlanner());
    }

    @DeleteMapping("/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("resourceId") String resourceId) {
        resourceCatalogService.delete(resourceId);
    }
}

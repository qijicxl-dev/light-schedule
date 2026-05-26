package com.lightschedule.modules.resource;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ResourceCatalogService {

    private final ResourceMapper resourceMapper;

    public ResourceCatalogService(ResourceMapper resourceMapper) {
        this.resourceMapper = resourceMapper;
    }

    public List<ResourceDefinition> list() {
        List<ResourceEntity> entities = resourceMapper.selectList(null);
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(entity -> new ResourceDefinition(
                        entity.getResourceId(),
                        entity.getGroupName(),
                        Boolean.TRUE.equals(entity.getDefaultPlanner())))
                .toList();
    }

    public List<String> listDefaultPlannerResourceIds() {
        return list().stream()
                .filter(ResourceDefinition::defaultPlannerResource)
                .map(ResourceDefinition::resourceId)
                .toList();
    }

    public String getGroupName(String resourceId) {
        return list().stream()
                .filter(resource -> resource.resourceId().equals(resourceId))
                .map(ResourceDefinition::groupName)
                .findFirst()
                .orElse("同组资源");
    }

    public ResourceDefinition save(String resourceId, String groupName, boolean defaultPlanner) {
        ResourceEntity entity = new ResourceEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setResourceId(resourceId);
        entity.setGroupName(groupName);
        entity.setDefaultPlanner(defaultPlanner);
        resourceMapper.insert(entity);
        return new ResourceDefinition(resourceId, groupName, defaultPlanner);
    }

    public ResourceDefinition update(String resourceId, String groupName, boolean defaultPlanner) {
        ResourceEntity existing = resourceMapper.selectOne(
                new QueryWrapper<ResourceEntity>().eq("resource_id", resourceId));
        if (existing == null) {
            throw new NoSuchElementException("resource not found: " + resourceId);
        }
        existing.setGroupName(groupName);
        existing.setDefaultPlanner(defaultPlanner);
        resourceMapper.updateById(existing);
        return new ResourceDefinition(resourceId, groupName, defaultPlanner);
    }

    public void delete(String resourceId) {
        resourceMapper.delete(new QueryWrapper<ResourceEntity>().eq("resource_id", resourceId));
    }

    public record ResourceDefinition(String resourceId, String groupName, boolean defaultPlannerResource) {
    }
}

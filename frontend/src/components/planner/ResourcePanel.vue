<template>
  <div class="resource-panel panel-card">
    <div class="resource-panel__header">
      <div>
        <h2 class="panel-title">资源目录</h2>
        <p class="panel-subtitle">查看与管理当前系统资源定义与分组。</p>
      </div>
      <span class="badge badge--info">{{ resources.length }} 条资源</span>
    </div>

    <div v-if="resources.length > 0 || isAdding" class="resource-panel__table" role="table" aria-label="资源目录表">
      <div class="resource-panel__table-header" role="row">
        <span role="columnheader">资源编号</span>
        <span role="columnheader">资源组</span>
        <span role="columnheader">默认排程</span>
        <span role="columnheader">操作</span>
      </div>
      <ul class="resource-panel__list">
        <li
          v-for="(resource, index) in resources"
          :key="resource.resourceId"
          class="resource-panel__item"
          role="row"
          :class="{ 'resource-panel__item--editing': editingIndex === index }"
        >
          <template v-if="editingIndex === index">
            <span class="resource-panel__code">{{ editForm.resourceId }}</span>
            <input
              v-model="editForm.groupName"
              class="resource-panel__input"
              type="text"
              placeholder="资源组"
              data-testid="edit-group-name"
            />
            <label class="resource-panel__checkbox">
              <input v-model="editForm.defaultPlannerResource" type="checkbox" />
              <span>{{ editForm.defaultPlannerResource ? '是' : '否' }}</span>
            </label>
            <div class="resource-panel__actions">
              <button class="button button--primary button--sm" type="button" data-testid="save-edit" @click="saveEdit">保存</button>
              <button class="button button--secondary button--sm" type="button" @click="cancelEdit">取消</button>
            </div>
          </template>
          <template v-else>
            <span class="resource-panel__code">{{ resource.resourceId }}</span>
            <span class="resource-panel__meta">{{ resource.groupName }}</span>
            <span>
              <span class="badge" :class="resource.defaultPlannerResource ? 'badge--success' : 'badge--secondary'">
                {{ resource.defaultPlannerResource ? '是' : '否' }}
              </span>
            </span>
            <div class="resource-panel__actions">
              <button class="button button--secondary button--sm" type="button" data-testid="edit-resource" @click="startEdit(index)">编辑</button>
              <button class="button button--danger button--sm" type="button" data-testid="delete-resource" @click="confirmDelete(resource.resourceId)">删除</button>
            </div>
          </template>
        </li>
        <li v-if="isAdding" class="resource-panel__item resource-panel__item--editing" role="row">
          <input
            v-model="addForm.resourceId"
            class="resource-panel__input"
            type="text"
            placeholder="资源编号"
            data-testid="add-resource-id"
          />
          <input
            v-model="addForm.groupName"
            class="resource-panel__input"
            type="text"
            placeholder="资源组"
            data-testid="add-group-name"
          />
          <label class="resource-panel__checkbox">
            <input v-model="addForm.defaultPlannerResource" type="checkbox" />
            <span>{{ addForm.defaultPlannerResource ? '是' : '否' }}</span>
          </label>
          <div class="resource-panel__actions">
            <button class="button button--primary button--sm" type="button" data-testid="save-add" @click="saveAdd">保存</button>
            <button class="button button--secondary button--sm" type="button" @click="cancelAdd">取消</button>
          </div>
        </li>
      </ul>
    </div>
    <div v-else class="resource-panel__empty">暂无资源数据</div>

    <div class="resource-panel__footer">
      <button
        v-if="!isAdding && editingIndex === null"
        class="button button--primary"
        type="button"
        data-testid="add-resource"
        @click="startAdd"
      >
        新增资源
      </button>
    </div>
  </div>
</template>

<style scoped>
.resource-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.resource-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.resource-panel__table {
  border: 1px solid rgba(203, 213, 225, 0.9);
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
}

.resource-panel__table-header,
.resource-panel__item {
  display: grid;
  grid-template-columns: 1fr 1fr 120px 160px;
  gap: 12px;
  align-items: center;
}

.resource-panel__table-header {
  padding: 10px 12px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 1), rgba(241, 245, 249, 1));
  border-bottom: 1px solid rgba(203, 213, 225, 0.9);
  color: var(--text-muted);
  font-size: 0.76rem;
  font-weight: 700;
}

.resource-panel__list {
  display: grid;
}

.resource-panel__item {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  min-height: 52px;
}

.resource-panel__item:last-child {
  border-bottom: 0;
}

.resource-panel__item--editing {
  background: rgba(59, 130, 246, 0.04);
}

.resource-panel__code {
  font-size: 0.9rem;
  font-weight: 700;
}

.resource-panel__meta {
  color: var(--text-secondary);
  line-height: 1.4;
  font-size: 0.84rem;
}

.resource-panel__input {
  padding: 6px 10px;
  border: 1px solid rgba(203, 213, 225, 0.9);
  border-radius: 8px;
  font-size: 0.84rem;
  background: #fff;
  min-width: 0;
}

.resource-panel__input:focus {
  outline: none;
  border-color: var(--color-primary-strong);
}

.resource-panel__checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.84rem;
  cursor: pointer;
}

.resource-panel__checkbox input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.resource-panel__actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.resource-panel__empty {
  padding: 20px;
  border-radius: 16px;
  border: 1px dashed var(--border-strong);
  text-align: center;
  color: var(--text-muted);
}

.resource-panel__footer {
  display: flex;
  justify-content: flex-end;
}

.button--sm {
  padding: 4px 10px;
  font-size: 0.78rem;
  min-height: 28px;
}

.button--danger {
  background: #ef4444;
  color: #fff;
  border-color: #ef4444;
}

.button--danger:hover {
  background: #dc2626;
}

@media (max-width: 720px) {
  .resource-panel__table-header {
    display: none;
  }

  .resource-panel__item {
    grid-template-columns: 1fr;
    align-items: flex-start;
    gap: 8px;
  }

  .resource-panel__actions {
    width: 100%;
  }
}
</style>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { ResourceDefinition } from '@/api/planner'

const props = defineProps<{
  resources: ResourceDefinition[]
}>()

const emit = defineEmits<{
  (e: 'update-resource', resourceId: string, data: { groupName: string; defaultPlannerResource: boolean }): void
  (e: 'create-resource', data: { resourceId: string; groupName: string; defaultPlannerResource: boolean }): void
  (e: 'delete-resource', resourceId: string): void
}>()

const editingIndex = ref<number | null>(null)
const isAdding = ref(false)

const editForm = reactive({
  resourceId: '',
  groupName: '',
  defaultPlannerResource: false
})

const addForm = reactive({
  resourceId: '',
  groupName: '',
  defaultPlannerResource: false
})

function startEdit(index: number) {
  const resource = props.resources[index]
  editingIndex.value = index
  editForm.resourceId = resource.resourceId
  editForm.groupName = resource.groupName
  editForm.defaultPlannerResource = resource.defaultPlannerResource
}

function cancelEdit() {
  editingIndex.value = null
}

function saveEdit() {
  if (!editForm.groupName.trim()) {
    alert('资源组不能为空')
    return
  }
  emit('update-resource', editForm.resourceId, {
    groupName: editForm.groupName.trim(),
    defaultPlannerResource: editForm.defaultPlannerResource
  })
  editingIndex.value = null
}

function startAdd() {
  isAdding.value = true
  addForm.resourceId = ''
  addForm.groupName = ''
  addForm.defaultPlannerResource = false
}

function cancelAdd() {
  isAdding.value = false
}

function saveAdd() {
  if (!addForm.resourceId.trim()) {
    alert('资源编号不能为空')
    return
  }
  if (!addForm.groupName.trim()) {
    alert('资源组不能为空')
    return
  }
  emit('create-resource', {
    resourceId: addForm.resourceId.trim(),
    groupName: addForm.groupName.trim(),
    defaultPlannerResource: addForm.defaultPlannerResource
  })
  isAdding.value = false
}

function confirmDelete(resourceId: string) {
  if (!confirm(`确定要删除资源 ${resourceId} 吗？`)) {
    return
  }
  emit('delete-resource', resourceId)
}
</script>

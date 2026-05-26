<template>
  <aside class="task-pool panel-card">
    <div class="task-pool__header">
      <div>
        <h2 class="panel-title">待排任务池</h2>
        <p class="panel-subtitle">优先展示当前待排工单、交期与物料风险。</p>
      </div>
      <div class="task-pool__header-actions">
        <span class="badge badge--info">{{ items.length }} 条任务</span>
        <button
          class="button button--primary task-pool__add-btn"
          type="button"
          data-testid="create-work-order"
          @click="emit('create-work-order')"
        >
          新增工单
        </button>
      </div>
    </div>

    <div v-if="items.length > 0" class="task-pool__table" role="table" aria-label="待排任务表">
      <div class="task-pool__table-header" role="row">
        <span role="columnheader">工单</span>
        <span role="columnheader">类型</span>
        <span role="columnheader">交期</span>
        <span role="columnheader">物料风险</span>
        <span role="columnheader">齐套状态</span>
        <span role="columnheader">操作</span>
      </div>
      <ul class="task-pool__list">
        <li v-for="item in items" :key="item.workOrderCode" class="task-pool__item" role="row">
          <span class="task-pool__code">{{ item.workOrderCode }}</span>
          <span>
            <span class="badge" :class="item.urgent ? 'badge--danger' : 'badge--info'">{{ item.urgent ? '急单' : '常规' }}</span>
          </span>
          <span class="task-pool__meta">{{ formatDateTime(item.dueAt) }}</span>
          <span>
            <span class="badge badge--warning">{{ item.materialRisk }}</span>
          </span>
          <span>
            <span class="badge badge--success">{{ item.readiness }}</span>
          </span>
          <div class="task-pool__actions">
            <button
              class="button button--ghost"
              type="button"
              :data-testid="`edit-work-order-${item.workOrderCode}`"
              @click="emit('edit-work-order', item)"
            >
              编辑
            </button>
            <button
              class="button button--ghost button--danger"
              type="button"
              :data-testid="`delete-work-order-${item.workOrderCode}`"
              @click="emit('delete-work-order', item.workOrderCode)"
            >
              删除
            </button>
            <button
              class="button button--secondary task-pool__action"
              type="button"
              :data-testid="`add-to-schedule-${item.workOrderCode}`"
              @click="emit('add-to-schedule', item)"
            >
              加入排程
            </button>
          </div>
        </li>
      </ul>
    </div>
    <div v-else class="task-pool__empty">暂无待排任务</div>
  </aside>
</template>

<style scoped>
.task-pool {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-pool__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.task-pool__table {
  border: 1px solid rgba(203, 213, 225, 0.9);
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
}

.task-pool__table-header,
.task-pool__item {
  display: grid;
  grid-template-columns: minmax(140px, 1.2fr) 88px minmax(160px, 1fr) 120px 120px 120px;
  gap: 12px;
  align-items: center;
}

.task-pool__table-header {
  padding: 10px 12px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 1), rgba(241, 245, 249, 1));
  border-bottom: 1px solid rgba(203, 213, 225, 0.9);
  color: var(--text-muted);
  font-size: 0.76rem;
  font-weight: 700;
}

.task-pool__list {
  display: grid;
}

.task-pool__item {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  min-height: 52px;
}

.task-pool__item:last-child {
  border-bottom: 0;
}

.task-pool__code {
  font-size: 0.9rem;
  font-weight: 700;
}

.task-pool__meta {
  color: var(--text-secondary);
  line-height: 1.4;
  font-size: 0.84rem;
}

.task-pool__action {
  justify-self: start;
}

.task-pool__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.task-pool__header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.task-pool__add-btn {
  padding: 6px 14px;
  font-size: 0.8rem;
  border-radius: 10px;
}

.task-pool__empty {
  padding: 20px;
  border-radius: 16px;
  border: 1px dashed var(--border-strong);
  text-align: center;
  color: var(--text-muted);
}

@media (max-width: 960px) {
  .task-pool__table-header {
    display: none;
  }

  .task-pool__item {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }
}
</style>

<script setup lang="ts">
import type { TaskPoolItem } from '@/stores/scheduleDraft'

const emit = defineEmits<{
  'add-to-schedule': [item: TaskPoolItem]
  'create-work-order': []
  'edit-work-order': [item: TaskPoolItem]
  'delete-work-order': [workOrderCode: string]
}>()

defineProps<{
  items: TaskPoolItem[]
}>()

function formatDateTime(value: string) {
  const date = new Date(value)
  const y = date.getUTCFullYear()
  const m = String(date.getUTCMonth() + 1).padStart(2, '0')
  const d = String(date.getUTCDate()).padStart(2, '0')
  const h = String(date.getUTCHours()).padStart(2, '0')
  const min = String(date.getUTCMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}`
}
</script>

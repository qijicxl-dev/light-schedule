<template>
  <aside class="risk-panel panel-card">
    <div class="risk-panel__header">
      <h2 class="panel-title">风险与建议</h2>
      <p class="panel-subtitle">展示排程建议与急单插入后受影响的任务范围。</p>
    </div>

    <section class="risk-panel__section">
      <div class="risk-panel__section-title">排程建议</div>
      <div v-if="suggestions.length > 0" class="risk-panel__table" role="table" aria-label="排程建议表">
        <div class="risk-panel__table-header" role="row">
          <span role="columnheader">建议动作</span>
          <span role="columnheader">原因说明</span>
          <span role="columnheader">操作</span>
        </div>
        <ul class="risk-panel__list">
          <li v-for="(suggestion, index) in suggestions" :key="`${suggestion.action}-${suggestion.reason}`" class="risk-panel__item" role="row">
            <span><span class="badge badge--warning">{{ suggestion.action }}</span></span>
            <span class="risk-panel__reason">{{ suggestion.reason }}</span>
            <button
              class="button button--secondary risk-panel__action"
              type="button"
              :data-testid="`apply-suggestion-${index}`"
              @click="emit('apply-suggestion', suggestion)"
            >
              应用到画板
            </button>
          </li>
        </ul>
      </div>
      <div v-else class="risk-panel__empty">暂无风险建议</div>
    </section>

    <section class="risk-panel__section">
      <div class="risk-panel__section-title">受影响任务</div>
      <div v-if="affectedItems.length > 0 || affectedTaskIds.length > 0" class="risk-panel__table" role="table" aria-label="受影响任务表">
        <div class="risk-panel__table-header" role="row">
          <span role="columnheader">任务</span>
          <span role="columnheader">资源组</span>
        </div>
        <ul class="risk-panel__list">
          <li v-for="item in affectedItems" :key="item.taskId" class="risk-panel__item risk-panel__item--compact" role="row">
            <span class="risk-panel__task">{{ item.taskId }}</span>
            <span class="risk-panel__reason">{{ item.resourceGroupName }}</span>
          </li>
          <li v-for="taskId in remainingAffectedTaskIds" :key="taskId" class="risk-panel__item risk-panel__item--compact" role="row">
            <span class="risk-panel__task">{{ taskId }}</span>
            <span class="risk-panel__reason">待映射资源组</span>
          </li>
        </ul>
      </div>
      <div v-else class="risk-panel__empty">暂无受影响任务</div>
    </section>
  </aside>
</template>

<style scoped>
.risk-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.risk-panel__header,
.risk-panel__section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.risk-panel__section-title {
  color: var(--text-muted);
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.risk-panel__table {
  border: 1px solid rgba(203, 213, 225, 0.9);
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
}

.risk-panel__table-header,
.risk-panel__item {
  display: grid;
  gap: 12px;
  align-items: center;
}

.risk-panel__table-header {
  padding: 10px 12px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 1), rgba(241, 245, 249, 1));
  border-bottom: 1px solid rgba(203, 213, 225, 0.9);
  color: var(--text-muted);
  font-size: 0.76rem;
  font-weight: 700;
}

.risk-panel__section:first-of-type .risk-panel__table-header,
.risk-panel__section:first-of-type .risk-panel__item {
  grid-template-columns: 140px minmax(240px, 1fr) 140px;
}

.risk-panel__section:last-of-type .risk-panel__table-header,
.risk-panel__section:last-of-type .risk-panel__item {
  grid-template-columns: minmax(160px, 1fr) minmax(180px, 1fr);
}

.risk-panel__list {
  display: grid;
}

.risk-panel__item {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  min-height: 52px;
}

.risk-panel__item:last-child {
  border-bottom: 0;
}

.risk-panel__item--compact {
  min-height: 44px;
}

.risk-panel__task {
  font-weight: 700;
  font-size: 0.9rem;
}

.risk-panel__reason {
  color: var(--text-secondary);
  line-height: 1.4;
  font-size: 0.84rem;
}

.risk-panel__action {
  justify-self: start;
}

.risk-panel__empty {
  padding: 18px;
  border-radius: 16px;
  border: 1px dashed var(--border-strong);
  text-align: center;
  color: var(--text-muted);
}

@media (max-width: 960px) {
  .risk-panel__table-header {
    display: none;
  }

  .risk-panel__item,
  .risk-panel__section:first-of-type .risk-panel__item,
  .risk-panel__section:last-of-type .risk-panel__item {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import type { ScheduledItem, Suggestion } from '@/stores/scheduleDraft'

const props = defineProps<{
  suggestions: Suggestion[]
  affectedItems: ScheduledItem[]
  affectedTaskIds: string[]
}>()

const emit = defineEmits<{
  'apply-suggestion': [suggestion: Suggestion]
}>()

const remainingAffectedTaskIds = computed(() => {
  const mappedTaskIds = new Set(props.affectedItems.map((item) => item.taskId))
  return props.affectedTaskIds.filter((taskId) => !mappedTaskIds.has(taskId))
})
</script>


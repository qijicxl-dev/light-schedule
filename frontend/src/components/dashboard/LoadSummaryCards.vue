<template>
  <section class="summary-card panel-card">
    <div class="summary-card__header">
      <div>
        <h2 class="panel-title">资源负荷总览</h2>
        <p class="panel-subtitle">快速查看当前产线整体负荷率与资源紧张程度。</p>
      </div>
      <span v-if="summary" class="badge" :class="statusBadgeClass">{{ summary.status }}</span>
    </div>

    <div v-if="summary" class="summary-card__content">
      <div>
        <div class="metric-label">当前负荷率</div>
        <div class="metric-value">{{ loadRateText }}</div>
      </div>
      <div class="summary-card__hint">适合作为老板驾驶舱首页的演示摘要。</div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CapacitySummary } from '@/stores/dashboard'

const props = defineProps<{
  summary: CapacitySummary | null
}>()

const loadRateText = computed(() => {
  if (!props.summary) {
    return ''
  }

  return `${Math.round(props.summary.loadRate * 100)}%`
})

const statusBadgeClass = computed(() => {
  switch (props.summary?.status) {
    case 'feasible':
      return 'badge--success'
    case 'tight':
      return 'badge--warning'
    case 'overloaded':
      return 'badge--danger'
    default:
      return 'badge--info'
  }
})
</script>

<style scoped>
.summary-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.summary-card__header,
.summary-card__content {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.summary-card__content {
  padding: 18px 20px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.95), rgba(255, 255, 255, 0.98));
  border: 1px solid rgba(191, 219, 254, 0.8);
}

.summary-card__hint {
  max-width: 260px;
  color: var(--text-secondary);
  line-height: 1.6;
}

@media (max-width: 720px) {
  .summary-card__header,
  .summary-card__content {
    flex-direction: column;
  }
}
</style>

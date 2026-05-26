<template>
  <section class="stats-card panel-card">
    <div class="stats-card__header">
      <div>
        <h2 class="panel-title">工单统计</h2>
        <p class="panel-subtitle">当前待排工单数量与风险分布。</p>
      </div>
      <span v-if="stats" class="badge badge--info">{{ stats.total }} 条</span>
    </div>

    <div v-if="stats" class="stats-card__content">
      <div class="stats-card__row">
        <div class="stats-card__metric">
          <div class="metric-label">工单总数</div>
          <div class="metric-value">{{ stats.total }}</div>
        </div>
        <div class="stats-card__metric">
          <div class="metric-label">急单</div>
          <div class="metric-value" :class="{ 'metric-value--danger': stats.urgentCount > 0 }">{{ stats.urgentCount }}</div>
        </div>
      </div>
      <div v-if="riskItems.length > 0" class="stats-card__distribution">
        <div v-for="item in riskItems" :key="item.label" class="stats-card__distribution-item">
          <span class="stats-card__distribution-label">{{ item.label }}</span>
          <span class="stats-card__distribution-value">{{ item.count }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  stats: { total: number; urgentCount: number; riskDistribution: Record<string, number> } | null
}>()

const riskLabelMap: Record<string, string> = {
  low: '低风险',
  medium: '中风险',
  high: '高风险'
}

const riskItems = computed(() => {
  if (!props.stats) return []
  return Object.entries(props.stats.riskDistribution).map(([key, count]) => ({
    label: riskLabelMap[key] || key,
    count
  }))
})
</script>

<style scoped>
.stats-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.stats-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.stats-card__content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.stats-card__row {
  display: flex;
  gap: 24px;
}

.stats-card__metric {
  flex: 1;
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.95), rgba(255, 255, 255, 0.98));
  border: 1px solid rgba(191, 219, 254, 0.8);
}

.metric-label {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.metric-value {
  font-size: 1.6rem;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1.2;
}

.metric-value--danger {
  color: #dc2626;
}

.stats-card__distribution {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.stats-card__distribution-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 10px;
  background: rgba(241, 245, 249, 0.9);
  font-size: 0.82rem;
}

.stats-card__distribution-label {
  color: var(--text-secondary);
}

.stats-card__distribution-value {
  font-weight: 700;
  color: var(--text-primary);
}

@media (max-width: 720px) {
  .stats-card__row {
    flex-direction: column;
  }
}
</style>

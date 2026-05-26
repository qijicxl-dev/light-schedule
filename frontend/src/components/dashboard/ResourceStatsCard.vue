<template>
  <section class="stats-card panel-card">
    <div class="stats-card__header">
      <div>
        <h2 class="panel-title">资源概览</h2>
        <p class="panel-subtitle">当前可用资源与默认排程资源数量。</p>
      </div>
    </div>

    <div v-if="stats" class="stats-card__content">
      <div class="stats-card__row">
        <div class="stats-card__metric">
          <div class="metric-label">资源总数</div>
          <div class="metric-value">{{ stats.total }}</div>
        </div>
        <div class="stats-card__metric">
          <div class="metric-label">默认排程资源</div>
          <div class="metric-value metric-value--success">{{ stats.defaultPlannerCount }}</div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
const props = defineProps<{
  stats: { total: number; defaultPlannerCount: number } | null
}>()
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

.metric-value--success {
  color: #16a34a;
}

@media (max-width: 720px) {
  .stats-card__row {
    flex-direction: column;
  }
}
</style>

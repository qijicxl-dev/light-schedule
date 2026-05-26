<template>
  <section class="page-shell capacity-page">
    <header class="page-hero capacity-page__hero">
      <span class="page-hero__eyebrow">Capacity Insights</span>
      <h1 class="page-hero__title">能力分析</h1>
      <p class="page-hero__description">围绕资源负荷趋势、同组差异与高负荷时段，快速展示当前排程能力画像。</p>
    </header>

    <section v-if="capacityAnalysisState.loading" class="status-block status-block--loading">加载中</section>
    <section v-else-if="capacityAnalysisState.error" class="status-block status-block--error">{{ capacityAnalysisState.error }}</section>
    <template v-else-if="capacityAnalysisState.overview">
      <section class="capacity-page__grid">
        <section class="panel-card capacity-page__panel">
          <div class="capacity-page__panel-header">
            <h2 class="panel-title">资源负荷趋势</h2>
            <p class="panel-subtitle">按资源与时间桶查看负荷走势。</p>
          </div>
          <ul class="capacity-page__list">
            <li v-for="trend in capacityAnalysisState.overview.trends" :key="`${trend.resourceId}-${trend.bucketLabel}`" class="capacity-page__item">
              <div>
                <div class="capacity-page__item-title">{{ trend.resourceId }}</div>
                <div class="capacity-page__item-meta">{{ trend.bucketLabel }}</div>
              </div>
              <div class="capacity-page__item-side">
                <span class="badge" :class="statusBadgeClass(trend.status)">{{ trend.status }}</span>
                <span class="capacity-page__metric">{{ toPercent(trend.loadRate) }}</span>
              </div>
            </li>
          </ul>
        </section>

        <section class="panel-card capacity-page__panel">
          <div class="capacity-page__panel-header">
            <h2 class="panel-title">同组差异</h2>
            <p class="panel-subtitle">快速识别同一资源组的负荷落差。</p>
          </div>
          <ul class="capacity-page__list">
            <li v-for="groupDiff in capacityAnalysisState.overview.groupDiffs" :key="groupDiff.groupName" class="capacity-page__item">
              <div class="capacity-page__item-title">{{ groupDiff.groupName }}</div>
              <span class="capacity-page__metric">{{ toPercent(groupDiff.gapRate) }}</span>
            </li>
          </ul>
        </section>

        <section class="panel-card capacity-page__panel">
          <div class="capacity-page__panel-header">
            <h2 class="panel-title">高负荷时段</h2>
            <p class="panel-subtitle">优先定位当前最紧张的时间窗口。</p>
          </div>
          <ul class="capacity-page__list">
            <li v-for="peakPeriod in capacityAnalysisState.overview.peakPeriods" :key="peakPeriod.bucketLabel" class="capacity-page__item">
              <div>
                <div class="capacity-page__item-title">{{ peakPeriod.bucketLabel }}</div>
                <div class="capacity-page__item-meta">{{ peakPeriod.status }}</div>
              </div>
              <div class="capacity-page__item-side">
                <span class="badge" :class="statusBadgeClass(peakPeriod.status)">{{ peakPeriod.status }}</span>
                <span class="capacity-page__metric">{{ toPercent(peakPeriod.loadRate) }}</span>
              </div>
            </li>
          </ul>
        </section>
      </section>
    </template>
    <section v-else class="status-block">暂无能力分析数据</section>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { capacityAnalysisState, loadCapacityAnalysisOverview } from '@/stores/capacityAnalysis'

function toPercent(value: number) {
  return `${Math.round(value * 100)}%`
}

function statusBadgeClass(status: string) {
  switch (status) {
    case 'feasible':
      return 'badge--success'
    case 'tight':
      return 'badge--warning'
    case 'overloaded':
      return 'badge--danger'
    default:
      return 'badge--info'
  }
}

onMounted(() => {
  void loadCapacityAnalysisOverview()
})
</script>

<style scoped>
.capacity-page {
  gap: 24px;
}

.capacity-page__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.capacity-page__panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.capacity-page__panel-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.capacity-page__list {
  display: grid;
  gap: 12px;
}

.capacity-page__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-radius: 18px;
  background: var(--bg-card-muted);
  border: 1px solid rgba(219, 227, 240, 0.85);
}

.capacity-page__item-title {
  font-weight: 700;
}

.capacity-page__item-meta {
  margin-top: 6px;
  color: var(--text-secondary);
}

.capacity-page__item-side {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.capacity-page__metric {
  font-size: 1.1rem;
  font-weight: 700;
}

@media (max-width: 1120px) {
  .capacity-page__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .capacity-page__item {
    flex-direction: column;
    align-items: flex-start;
  }

  .capacity-page__item-side {
    justify-content: flex-start;
  }
}
</style>

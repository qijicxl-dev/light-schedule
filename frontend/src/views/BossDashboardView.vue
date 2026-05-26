<template>
  <section class="page-shell dashboard-page">
    <header class="page-hero dashboard-page__hero">
      <span class="page-hero__eyebrow">Executive Summary</span>
      <h1 class="page-hero__title">老板驾驶舱</h1>
      <p class="page-hero__description">用一张摘要卡快速展示当前资源负荷，为演示中的管理视角提供收口页面。</p>
    </header>

    <section v-if="dashboardState.loading" class="status-block status-block--loading">加载中</section>
    <section v-else-if="dashboardState.error" class="status-block status-block--error">{{ dashboardState.error }}</section>
    <section v-else-if="!dashboardState.overview" class="status-block">暂无驾驶舱摘要</section>
    <template v-else>
      <section class="dashboard-page__grid">
        <LoadSummaryCards :summary="dashboardState.overview.capacitySummary" />
        <WorkOrderStatsCard :stats="dashboardState.overview.workOrderStats" />
        <ResourceStatsCard :stats="dashboardState.overview.resourceStats" />
      </section>
    </template>
  </section>
</template>

<style scoped>
.dashboard-page {
  gap: 24px;
}

.dashboard-page__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 20px;
}
</style>

<script setup lang="ts">
import { onMounted } from 'vue'
import LoadSummaryCards from '@/components/dashboard/LoadSummaryCards.vue'
import WorkOrderStatsCard from '@/components/dashboard/WorkOrderStatsCard.vue'
import ResourceStatsCard from '@/components/dashboard/ResourceStatsCard.vue'
import { dashboardState, loadDashboardOverview } from '@/stores/dashboard'

onMounted(() => {
  void loadDashboardOverview()
})
</script>

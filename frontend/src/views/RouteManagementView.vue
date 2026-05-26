<template>
  <section class="page-shell route-page">
    <header class="page-hero route-page__hero">
      <span class="page-hero__eyebrow">Route Management</span>
      <h1 class="page-hero__title">工艺路线管理</h1>
      <p class="page-hero__description">查看所有工艺路线及其步骤详情，了解工序顺序与依赖关系。</p>
    </header>

    <section v-if="loading" class="status-block status-block--loading">加载中</section>
    <section v-else-if="error" class="status-block status-block--error">{{ error }}</section>
    <template v-else>
      <section class="route-page__layout">
        <section class="panel-card route-page__sidebar" aria-label="工艺路线列表">
          <div class="route-page__panel-header">
            <h2 class="panel-title">工艺路线</h2>
            <p class="panel-subtitle">{{ routes.length }} 条工艺路线</p>
          </div>
          <ul v-if="routes.length > 0" class="route-page__route-list">
            <li
              v-for="route in routes"
              :key="route"
              class="route-page__route-item"
              :class="{ 'route-page__route-item--active': selectedRoute === route }"
              role="button"
              tabindex="0"
              @click="selectRoute(route)"
              @keydown.enter.space.prevent="selectRoute(route)"
            >
              <span class="route-page__route-id">{{ route }}</span>
              <span class="route-page__route-arrow" aria-hidden="true">›</span>
            </li>
          </ul>
          <p v-else class="status-block">暂无工艺路线</p>
        </section>

        <section class="panel-card route-page__detail" aria-label="工艺路线步骤详情">
          <div class="route-page__panel-header">
            <h2 class="panel-title">步骤详情</h2>
            <p class="panel-subtitle">
              {{ selectedRoute ? `工艺路线 ${selectedRoute}` : '请选择工艺路线' }}
            </p>
          </div>

          <template v-if="selectedRoute">
            <section v-if="stepsLoading" class="status-block status-block--loading">加载步骤中</section>
            <section v-else-if="stepsError" class="status-block status-block--error">{{ stepsError }}</section>
            <table v-else-if="steps.length > 0" class="route-page__table">
              <thead>
                <tr>
                  <th scope="col">步骤编号</th>
                  <th scope="col">所需分钟</th>
                  <th scope="col">前置步骤</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(step, index) in steps" :key="step.stepId">
                  <td>
                    <span class="route-page__step-index">{{ index + 1 }}</span>
                    {{ step.stepId }}
                  </td>
                  <td>{{ step.requiredMinutes }}</td>
                  <td>
                    <span v-if="step.dependencyStepIds.length === 0" class="route-page__empty-deps">—</span>
                    <span v-else class="route-page__deps">{{ step.dependencyStepIds.join(', ') }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <p v-else class="status-block">该工艺路线暂无步骤</p>
          </template>
          <p v-else class="status-block">从左侧选择一条工艺路线查看步骤</p>
        </section>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { plannerApi } from '@/api/planner'
import type { RouteStep } from '@/api/planner'

const routes = ref<string[]>([])
const selectedRoute = ref<string>('')
const steps = ref<RouteStep[]>([])
const loading = ref(false)
const error = ref('')
const stepsLoading = ref(false)
const stepsError = ref('')

async function loadRoutes() {
  loading.value = true
  error.value = ''
  try {
    routes.value = await plannerApi.listRoutes()
    if (routes.value.length > 0 && !selectedRoute.value) {
      await selectRoute(routes.value[0])
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载工艺路线失败'
  } finally {
    loading.value = false
  }
}

async function selectRoute(routeId: string) {
  selectedRoute.value = routeId
  stepsLoading.value = true
  stepsError.value = ''
  try {
    steps.value = await plannerApi.listRouteSteps(routeId)
  } catch (e) {
    stepsError.value = e instanceof Error ? e.message : '加载步骤失败'
    steps.value = []
  } finally {
    stepsLoading.value = false
  }
}

onMounted(() => {
  void loadRoutes()
})
</script>

<style scoped>
.route-page {
  gap: 24px;
}

.route-page__layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
}

.route-page__sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: 600px;
  overflow: auto;
}

.route-page__detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.route-page__panel-header {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.route-page__route-list {
  display: grid;
  gap: 6px;
}

.route-page__route-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.15s ease;
  border: 1px solid transparent;
}

.route-page__route-item:hover {
  background: var(--bg-card-muted);
}

.route-page__route-item--active {
  background: rgba(59, 130, 246, 0.08);
  border-color: rgba(59, 130, 246, 0.25);
}

.route-page__route-id {
  font-weight: 600;
  font-size: 0.88rem;
}

.route-page__route-arrow {
  color: var(--text-muted);
  font-size: 1.1rem;
}

.route-page__route-item--active .route-page__route-arrow {
  color: var(--color-primary-strong);
}

.route-page__table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 0.86rem;
}

.route-page__table th {
  text-align: left;
  padding: 10px 12px;
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid rgba(203, 213, 225, 0.8);
  background: var(--bg-card-muted);
}

.route-page__table th:first-child {
  border-radius: 10px 0 0 0;
}

.route-page__table th:last-child {
  border-radius: 0 10px 0 0;
}

.route-page__table td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.7);
}

.route-page__step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: var(--color-primary-strong);
  color: white;
  font-size: 0.72rem;
  font-weight: 700;
  margin-right: 8px;
}

.route-page__empty-deps {
  color: var(--text-muted);
}

.route-page__deps {
  color: var(--text-secondary);
}

@media (max-width: 1120px) {
  .route-page__layout {
    grid-template-columns: 1fr;
  }

  .route-page__sidebar {
    max-height: none;
  }
}
</style>

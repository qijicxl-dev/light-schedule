<template>
  <section class="page-shell planner-page">
    <header class="page-hero planner-page__hero">
      <span class="page-hero__eyebrow">Planner Workspace</span>
      <h1 class="page-hero__title">排程工作台</h1>
      <p class="page-hero__description">
        在同一页里查看待排任务、排程结果与风险建议，并继续演示急单插入与回写确认的主流程。
      </p>
    </header>

    <section v-if="scheduleDraftState.loading" class="status-block status-block--loading">加载中</section>
    <section v-else-if="scheduleDraftState.error" class="status-block status-block--error">{{ scheduleDraftState.error }}</section>
    <section v-else-if="isEmpty" class="status-block">暂无排程数据</section>
    <template v-else>
      <section class="planner-ribbon panel-card" aria-label="工作台工具带">
        <div class="planner-ribbon__menubar" aria-label="工作台菜单栏">
          <button
            v-for="menu in ribbonMenus"
            :key="menu.key"
            class="planner-ribbon__menu-item"
            :class="{ 'planner-ribbon__menu-item--active': activeRibbonMenu === menu.key }"
            type="button"
            @click="handleRibbonMenuClick(menu.key)"
          >
            {{ menu.label }}
          </button>
        </div>
        <div class="planner-ribbon__toolbar">
          <div>
            <p class="planner-ribbon__eyebrow">PLANNER CONSOLE</p>
            <h2 class="planner-ribbon__title">派工工作表</h2>
          </div>
          <div class="planner-ribbon__actions">
            <button class="button button--secondary" type="button" data-testid="open-urgent-order" @click="openUrgentOrderDialog">插入急单</button>
            <button class="button button--primary" type="button" data-testid="open-publish-dialog" @click="openPublishDialog">{{ publishActionLabel }}</button>
          </div>
        </div>
        <div class="planner-ribbon__summary">
          <p class="planner-ribbon__description">把当前视图、任务数量和常驻动作集中到菜单栏与工具栏里，页面会更接近企业排产台。</p>
          <div class="planner-ribbon__metrics" aria-label="工作台摘要">
            <article class="planner-ribbon__metric">
              <span class="planner-ribbon__metric-label">当前面板</span>
              <strong class="planner-ribbon__metric-value">{{ activeTabLabel }}</strong>
            </article>
            <article class="planner-ribbon__metric">
              <span class="planner-ribbon__metric-label">待排任务</span>
              <strong class="planner-ribbon__metric-value">{{ scheduleDraftState.taskPoolItems.length }}</strong>
            </article>
            <article class="planner-ribbon__metric">
              <span class="planner-ribbon__metric-label">画板工单</span>
              <strong class="planner-ribbon__metric-value">{{ scheduleDraftState.scheduledItems.length }}</strong>
            </article>
            <article class="planner-ribbon__metric">
              <span class="planner-ribbon__metric-label">风险建议</span>
              <strong class="planner-ribbon__metric-value">{{ scheduleDraftState.suggestions.length }}</strong>
            </article>
          </div>
        </div>
        <div v-if="resourceUtilizations.length > 0" class="planner-ribbon__resource-load" aria-label="资源负载概览">
          <div v-for="res in resourceUtilizations" :key="res.resourceId" class="planner-ribbon__load-item">
            <span class="planner-ribbon__load-name">{{ res.resourceId }}</span>
            <div class="planner-ribbon__load-bar-bg">
              <div
                class="planner-ribbon__load-bar-fill"
                :class="{
                  'planner-ribbon__load-bar-fill--high': res.rate >= 80,
                  'planner-ribbon__load-bar-fill--medium': res.rate >= 50 && res.rate < 80
                }"
                :style="{ width: `${res.rate}%` }"
              />
            </div>
            <span class="planner-ribbon__load-value" :class="{ 'planner-ribbon__load-value--high': res.rate >= 80 }">{{ res.rate }}%</span>
          </div>
        </div>
      </section>

      <section v-if="activeRibbonMenu === 'schedule'" class="planner-sheet panel-card" aria-label="派工工作表容器">
        <div class="planner-sheet__toolbar">
          <div>
            <p class="planner-sheet__eyebrow">工作区切换</p>
            <h2 class="planner-sheet__title">按页签切换单一主面板</h2>
          </div>
          <p class="planner-sheet__description">只保留一个激活面板，让待排列表、排程画板和风险建议更像在同一张工作表内切页查看。</p>
        </div>

        <section class="planner-tabs" aria-label="工作台页签">
          <div class="planner-tabs__list" role="tablist" aria-label="工作台面板切换">
            <button
              v-for="tab in plannerTabs"
              :id="`planner-tab-${tab.key}`"
              :key="tab.key"
              class="planner-tabs__tab"
              :class="{ 'planner-tabs__tab--active': activeTab === tab.key }"
              type="button"
              role="tab"
              :tabindex="activeTab === tab.key ? 0 : -1"
              :aria-selected="activeTab === tab.key"
              :aria-controls="`planner-panel-${tab.key}`"
              @click="setActiveTab(tab.key)"
            >
              <span>{{ tab.label }}</span>
              <span class="planner-tabs__count">{{ tab.count }}</span>
            </button>
          </div>
        </section>

        <section class="planner-workbench">
          <section
            v-if="activeTab === 'taskPool'"
            id="planner-panel-taskPool"
            class="planner-workbench__panel"
            role="tabpanel"
            aria-labelledby="planner-tab-taskPool"
          >
            <TaskPoolPanel :items="scheduleDraftState.taskPoolItems" @add-to-schedule="handleAddToSchedule" @create-work-order="openCreateWorkOrderDialog" @edit-work-order="openEditWorkOrderDialog" @delete-work-order="handleDeleteWorkOrder" />
          </section>
          <section
            v-else-if="activeTab === 'scheduleBoard'"
            id="planner-panel-scheduleBoard"
            class="planner-workbench__panel"
            role="tabpanel"
            aria-labelledby="planner-tab-scheduleBoard"
          >
            <ScheduleBoard :items="scheduleDraftState.scheduledItems" @update-task-time="handleUpdateTaskTime" />
          </section>
          <section
            v-else
            id="planner-panel-risk"
            class="planner-workbench__panel"
            role="tabpanel"
            aria-labelledby="planner-tab-risk"
          >
            <RiskSidePanel
              :suggestions="scheduleDraftState.suggestions"
              :affected-items="affectedScheduledItems"
              :affected-task-ids="scheduleDraftState.affectedTaskIds"
              @apply-suggestion="handleApplySuggestion"
            />
          </section>
        </section>
      </section>

      <section v-else-if="activeRibbonMenu === 'resource'" class="planner-sheet panel-card" aria-label="资源目录容器">
        <ResourcePanel
          :resources="resources"
          @update-resource="handleUpdateResource"
          @create-resource="handleCreateResource"
          @delete-resource="handleDeleteResource"
        />
      </section>

      <UrgentOrderDialog
        v-model="showUrgentOrder"
        :resource-options="resourceOptions"
        :default-urgent-resource-id="defaultUrgentResourceId"
        :default-urgent-start-at="defaultUrgentStartAt"
        :default-urgent-end-at="defaultUrgentEndAt"
        @submit="submitUrgentOrder"
        @apply-suggestion="handleApplySuggestion"
      />
      <PublishDialog v-model="showPublishDialog" @confirm="confirmPublishDraft" />
      <CreateWorkOrderDialog
        v-model="showCreateWorkOrderDialog"
        :mode="workOrderDialogMode"
        :initial-data="workOrderDialogInitialData"
        @created="handleWorkOrderCreated"
        @updated="handleWorkOrderUpdated"
      />
    </template>
  </section>
</template>

<style scoped>
.planner-page {
  gap: 12px;
}

.planner-ribbon {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background:
    linear-gradient(180deg, rgba(241, 245, 249, 0.98), rgba(226, 232, 240, 0.96)),
    #e2e8f0;
  border: 1px solid rgba(148, 163, 184, 0.32);
}

.planner-ribbon__menubar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
}

.planner-ribbon__menu-item {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 8px 8px 0 0;
  color: var(--text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
}

.planner-ribbon__menu-item--active {
  color: var(--color-primary-strong);
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(203, 213, 225, 0.92);
  border-bottom-color: transparent;
}

.planner-ribbon__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.planner-ribbon__summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.planner-ribbon__eyebrow,
.planner-sheet__eyebrow {
  margin-bottom: 4px;
  color: var(--text-muted);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.planner-ribbon__title,
.planner-sheet__title {
  font-size: 1.02rem;
  font-weight: 700;
}

.planner-ribbon__description,
.planner-sheet__description {
  max-width: 520px;
  color: var(--text-secondary);
  line-height: 1.45;
  font-size: 0.84rem;
}

.planner-ribbon__metrics {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.planner-ribbon__metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(203, 213, 225, 0.92);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.85);
}

.planner-ribbon__metric-label {
  color: var(--text-muted);
  font-size: 0.74rem;
}

.planner-ribbon__metric-value {
  font-size: 0.92rem;
  font-weight: 700;
}

.planner-ribbon__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.planner-sheet {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 0;
  overflow: hidden;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
  border: 1px solid rgba(203, 213, 225, 0.95);
}

.planner-sheet__toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px 0;
}

.planner-workbench {
  display: block;
  padding: 0 14px 14px;
}

.planner-workbench__panel {
  min-width: 0;
}

@media (max-width: 1120px) {
  .planner-ribbon__toolbar,
  .planner-ribbon__summary,
  .planner-sheet__toolbar {
    flex-direction: column;
  }

  .planner-ribbon__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.planner-ribbon__resource-load {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  padding-top: 10px;
  border-top: 1px solid rgba(148, 163, 184, 0.22);
}

.planner-ribbon__load-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 180px;
  max-width: 320px;
}

.planner-ribbon__load-name {
  font-size: 0.76rem;
  font-weight: 600;
  color: #334155;
  white-space: nowrap;
  flex: 0 0 auto;
}

.planner-ribbon__load-bar-bg {
  flex: 1;
  height: 8px;
  border-radius: 4px;
  background: rgba(203, 213, 225, 0.5);
  overflow: hidden;
}

.planner-ribbon__load-bar-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #22c55e, #16a34a);
  transition: width 0.4s ease;
}

.planner-ribbon__load-bar-fill--medium {
  background: linear-gradient(90deg, #f59e0b, #d97706);
}

.planner-ribbon__load-bar-fill--high {
  background: linear-gradient(90deg, #ef4444, #dc2626);
}

.planner-ribbon__load-value {
  font-size: 0.74rem;
  font-weight: 700;
  color: #475569;
  flex: 0 0 auto;
  min-width: 36px;
  text-align: right;
}

.planner-ribbon__load-value--high {
  color: #dc2626;
}

@media (max-width: 720px) {
  .planner-ribbon,
  .planner-sheet__toolbar,
  .planner-workbench {
    padding-left: 12px;
    padding-right: 12px;
  }

  .planner-ribbon__metrics {
    grid-template-columns: 1fr;
  }

  .planner-ribbon__actions {
    width: 100%;
  }

  .planner-ribbon__actions .button {
    flex: 1;
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import CreateWorkOrderDialog from '@/components/planner/CreateWorkOrderDialog.vue'
import TaskPoolPanel from '@/components/planner/TaskPoolPanel.vue'
import ScheduleBoard from '@/components/planner/ScheduleBoard.vue'
import RiskSidePanel from '@/components/planner/RiskSidePanel.vue'
import ResourcePanel from '@/components/planner/ResourcePanel.vue'
import UrgentOrderDialog from '@/components/planner/UrgentOrderDialog.vue'
import PublishDialog from '@/components/planner/PublishDialog.vue'
import { plannerApi } from '@/api/planner'
import type { ResourceDefinition } from '@/api/planner'
import type { ScheduledItem, TaskPoolItem } from '@/stores/scheduleDraft'
import {
  addTaskToSchedule,
  applySuggestionToSchedule,
  loadPlannerData,
  loadUrgentSuggestions,
  publishPlannerDraft,
  refreshPublishStatus,
  resetPublishResult,
  resetUrgentReplanResult,
  scheduleDraftState
} from '@/stores/scheduleDraft'

type PlannerTab = 'taskPool' | 'scheduleBoard' | 'risk'
type RibbonMenu = 'schedule' | 'resource' | 'risk' | 'writeback'

const showUrgentOrder = ref(false)
const showPublishDialog = ref(false)
const showCreateWorkOrderDialog = ref(false)
const workOrderDialogMode = ref<'create' | 'edit'>('create')
const workOrderDialogInitialData = ref<TaskPoolItem | undefined>(undefined)
const activeTab = ref<PlannerTab>('taskPool')
const activeRibbonMenu = ref<RibbonMenu>('schedule')
const resources = ref<ResourceDefinition[]>([])
const isEmpty = computed(
  () =>
    scheduleDraftState.taskPoolItems.length === 0 &&
    scheduleDraftState.scheduledItems.length === 0 &&
    scheduleDraftState.suggestions.length === 0
)
const plannerTabs = computed(() => [
  { key: 'taskPool', label: '待排任务', count: scheduleDraftState.taskPoolItems.length },
  { key: 'scheduleBoard', label: '排程画板', count: scheduleDraftState.scheduledItems.length },
  { key: 'risk', label: '风险与建议', count: scheduleDraftState.suggestions.length }
] as const)
const activeTabLabel = computed(() => plannerTabs.value.find((tab) => tab.key === activeTab.value)?.label ?? '')

const ribbonMenus = computed(() => [
  { key: 'schedule' as const, label: '排程' },
  { key: 'resource' as const, label: '资源' },
  { key: 'risk' as const, label: '风险' },
  { key: 'writeback' as const, label: '回写' }
])

const resourceOptions = computed(() =>
  scheduleDraftState.scheduledItems
    .map((item) => ({
      resourceId: item.resourceId,
      resourceGroupName: item.resourceGroupName
    }))
    .filter(
      (item, index, items) => items.findIndex((candidate) => candidate.resourceId === item.resourceId) === index
    )
)

const defaultUrgentResourceId = computed(() => scheduleDraftState.scheduledItems[0]?.resourceId ?? '')
const defaultUrgentStartAt = computed(() => scheduleDraftState.scheduledItems[0]?.startAt ?? '')
const defaultUrgentEndAt = computed(() => toUrgentEndAt(defaultUrgentStartAt.value))
const publishActionLabel = computed(() =>
  scheduleDraftState.publishResult || scheduleDraftState.publishLoading || scheduleDraftState.publishError
    ? '查看回写状态'
    : '确认回写'
)

// 资源利用率：每个资源的任务总时长 / 时间窗口总时长
const resourceUtilizations = computed(() => {
  const items = scheduleDraftState.scheduledItems
  if (items.length === 0) return []

  const starts = items.map((i) => new Date(i.startAt).getTime())
  const ends = items.map((i) => new Date(i.endAt).getTime())
  const windowStart = Math.min(...starts)
  const windowEnd = Math.max(...ends)
  const windowDuration = windowEnd - windowStart
  if (windowDuration <= 0) return []

  const byResource = new Map<string, { resourceId: string; resourceGroupName: string; occupiedMs: number }>()
  items.forEach((item) => {
    const existing = byResource.get(item.resourceId)
    const duration = new Date(item.endAt).getTime() - new Date(item.startAt).getTime()
    if (existing) {
      existing.occupiedMs += duration
    } else {
      byResource.set(item.resourceId, {
        resourceId: item.resourceId,
        resourceGroupName: item.resourceGroupName,
        occupiedMs: duration
      })
    }
  })

  return Array.from(byResource.values()).map((r) => ({
    ...r,
    rate: Math.min(Math.round((r.occupiedMs / windowDuration) * 100), 100)
  }))
})

const highestUtilization = computed(() => {
  if (resourceUtilizations.value.length === 0) return null
  return resourceUtilizations.value.reduce((max, cur) => (cur.rate > max.rate ? cur : max))
})

// 风险侧栏直接复用急单受影响任务映射结果，避免建议文本和资源组上下文分离。
const affectedScheduledItems = computed(() =>
  scheduleDraftState.affectedTaskIds
    .map((taskId) => scheduleDraftState.scheduledItems.find((item) => item.taskId === taskId))
    .filter((item): item is (typeof scheduleDraftState.scheduledItems)[number] => Boolean(item))
)

function setActiveTab(tab: PlannerTab) {
  activeTab.value = tab
}

async function loadResources() {
  try {
    resources.value = await plannerApi.listResources()
  } catch (e) {
    // 资源加载失败时保持空列表，不阻断主流程
    resources.value = []
  }
}

async function handleCreateResource(data: { resourceId: string; groupName: string; defaultPlannerResource: boolean }) {
  try {
    await plannerApi.createResource({
      resourceId: data.resourceId,
      groupName: data.groupName,
      defaultPlanner: data.defaultPlannerResource
    })
    await loadResources()
  } catch (e) {
    alert(e instanceof Error ? e.message : '创建资源失败')
  }
}

async function handleUpdateResource(
  resourceId: string,
  data: { groupName: string; defaultPlannerResource: boolean }
) {
  try {
    await plannerApi.updateResource(resourceId, {
      resourceId,
      groupName: data.groupName,
      defaultPlanner: data.defaultPlannerResource
    })
    await loadResources()
  } catch (e) {
    alert(e instanceof Error ? e.message : '更新资源失败')
  }
}

async function handleDeleteResource(resourceId: string) {
  try {
    await plannerApi.deleteResource(resourceId)
    await loadResources()
  } catch (e) {
    alert(e instanceof Error ? e.message : '删除资源失败')
  }
}

function handleRibbonMenuClick(menu: RibbonMenu) {
  if (menu === 'writeback') {
    openPublishDialog()
    return
  }
  if (menu === 'risk') {
    activeRibbonMenu.value = 'schedule'
    setActiveTab('risk')
    return
  }
  activeRibbonMenu.value = menu
  if (menu === 'resource') {
    void loadResources()
  }
}

watch(
  () => scheduleDraftState.loading,
  (loading) => {
    if (loading) {
      showUrgentOrder.value = false
      showPublishDialog.value = false
    }
  }
)

function openUrgentOrderDialog() {
  resetUrgentReplanResult()
  showUrgentOrder.value = true
}

function openCreateWorkOrderDialog() {
  workOrderDialogMode.value = 'create'
  workOrderDialogInitialData.value = undefined
  showCreateWorkOrderDialog.value = true
}

function openEditWorkOrderDialog(item: TaskPoolItem) {
  workOrderDialogMode.value = 'edit'
  workOrderDialogInitialData.value = item
  showCreateWorkOrderDialog.value = true
}

async function handleWorkOrderCreated() {
  await loadPlannerData()
}

async function handleWorkOrderUpdated() {
  await loadPlannerData()
}

async function handleDeleteWorkOrder(workOrderCode: string) {
  if (!confirm(`确定要删除工单 ${workOrderCode} 吗？`)) {
    return
  }
  try {
    await plannerApi.deleteWorkOrder(workOrderCode)
    await loadPlannerData()
  } catch (e) {
    alert(e instanceof Error ? e.message : '删除工单失败')
  }
}

function handleAddToSchedule(task: TaskPoolItem) {
  addTaskToSchedule(task)
}

function handleUpdateTaskTime(payload: { taskId: string; newStartAt: string; newEndAt: string }) {
  const index = scheduleDraftState.scheduledItems.findIndex((item) => item.taskId === payload.taskId)
  if (index === -1) return

  scheduleDraftState.scheduledItems[index] = {
    ...scheduleDraftState.scheduledItems[index],
    startAt: payload.newStartAt,
    endAt: payload.newEndAt
  }
}

function handleApplySuggestion(suggestion: { action: string; reason: string }) {
  applySuggestionToSchedule(suggestion)
}

async function submitUrgentOrder(payload: { resourceId: string; startAt: string; endAt: string }) {
  await loadUrgentSuggestions({
    urgentTaskId: scheduleDraftState.taskPoolItems[0]?.workOrderCode ?? '',
    urgentResourceId: payload.resourceId,
    urgentStartAt: payload.startAt,
    urgentEndAt: payload.endAt,
    items: scheduleDraftState.scheduledItems
  })
}

async function openPublishDialog() {
  if (!scheduleDraftState.publishResult && !scheduleDraftState.publishLoading && !scheduleDraftState.publishError) {
    resetPublishResult()
  }
  showPublishDialog.value = true
  if (
    scheduleDraftState.publishResult?.auditId &&
    scheduleDraftState.publishResult.writebackStatus !== 'TERMINAL_FAILED'
  ) {
    await refreshPublishStatus()
  }
}

async function confirmPublishDraft() {
  await publishPlannerDraft()
}

function toUrgentEndAt(startAt: string) {
  if (!startAt) {
    return ''
  }
  return new Date(new Date(startAt).getTime() + 30 * 60 * 1000).toISOString()
}

onMounted(() => {
  void loadPlannerData()
})
</script>

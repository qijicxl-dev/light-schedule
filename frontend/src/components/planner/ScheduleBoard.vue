<template>
  <main class="schedule-board panel-card">
    <div class="schedule-board__header">
      <div>
        <h2 class="panel-title">排程画板</h2>
        <p class="panel-subtitle">聚焦展示任务与资源的分配结果，作为排程主视觉区域。</p>
      </div>
      <div class="schedule-board__header-actions">
        <div class="schedule-board__view-toggle" role="group" aria-label="视图切换">
          <button
            class="schedule-board__view-btn"
            :class="{ 'schedule-board__view-btn--active': viewMode === 'table' }"
            type="button"
            data-testid="view-mode-table"
            @click="viewMode = 'table'"
          >
            表格
          </button>
          <button
            class="schedule-board__view-btn"
            :class="{ 'schedule-board__view-btn--active': viewMode === 'gantt' }"
            type="button"
            data-testid="view-mode-gantt"
            @click="viewMode = 'gantt'"
          >
            甘特图
          </button>
        </div>
        <button
          v-if="sortKey"
          class="button button--secondary button--sm"
          type="button"
          data-testid="clear-sort"
          @click="clearSort"
        >
          清除排序
        </button>
        <button
          v-if="items.length > 0"
          class="button button--secondary button--sm"
          type="button"
          data-testid="export-schedule"
          @click="exportSchedule"
        >
          导出
        </button>
        <button
          v-if="items.length > 0"
          class="button button--secondary button--sm schedule-board__print-btn"
          type="button"
          data-testid="print-schedule"
          @click="printSchedule"
        >
          打印
        </button>
        <span class="badge badge--info">{{ filteredItems.length }} 条排程</span>
      </div>
    </div>

    <div v-if="items.length > 0" class="schedule-board__content" aria-label="排程数据展示">
      <!-- Search / Filter -->
      <div class="schedule-board__filter" aria-label="排程筛选">
        <input
          v-model="filterText"
          type="text"
          class="schedule-board__filter-input"
          placeholder="搜索任务编号或资源..."
          data-testid="schedule-filter-input"
          aria-label="搜索任务编号或资源"
        />
        <span v-if="filterText" class="schedule-board__filter-clear" data-testid="schedule-filter-clear" @click="filterText = ''">✕</span>
      </div>

      <!-- Gantt Detail Panel -->
      <!-- Gantt Detail Panel -->
      <div
        v-if="selectedGanttTask && viewMode === 'gantt'"
        class="schedule-board__gantt-detail"
        data-testid="gantt-detail-panel"
      >
        <div class="schedule-board__gantt-detail-header">
          <strong class="schedule-board__gantt-detail-title">{{ selectedGanttTask.taskId }}</strong>
          <button
            class="schedule-board__gantt-detail-close"
            type="button"
            data-testid="gantt-detail-close"
            @click="selectedGanttTask = null"
          >
            ✕
          </button>
        </div>
        <div class="schedule-board__gantt-detail-body">
          <div class="schedule-board__gantt-detail-row">
            <span class="schedule-board__gantt-detail-label">资源</span>
            <span class="schedule-board__gantt-detail-value">{{ selectedGanttTask.resourceId }}（{{ selectedGanttTask.resourceGroupName }}）</span>
          </div>
          <div class="schedule-board__gantt-detail-row">
            <span class="schedule-board__gantt-detail-label">开始时间</span>
            <span class="schedule-board__gantt-detail-value">{{ formatDateTime(selectedGanttTask.startAt) }}</span>
          </div>
          <div class="schedule-board__gantt-detail-row">
            <span class="schedule-board__gantt-detail-label">结束时间</span>
            <span class="schedule-board__gantt-detail-value">{{ formatDateTime(selectedGanttTask.endAt) }}</span>
          </div>
          <div class="schedule-board__gantt-detail-row">
            <span class="schedule-board__gantt-detail-label">持续时间</span>
            <span class="schedule-board__gantt-detail-value">{{ formatDuration(selectedGanttTask.startAt, selectedGanttTask.endAt) }}</span>
          </div>
        </div>
      </div>

      <!-- Table View -->
      <template v-if="viewMode === 'table'">
        <div class="schedule-board__table-wrap" aria-label="排程数据表格">
          <table class="schedule-board__table">
            <thead>
              <tr>
                <th
                  v-for="col in columns"
                  :key="col.key"
                  :class="{ 'schedule-board__th--sorted': sortKey === col.key }"
                  :data-testid="`sort-header-${col.key}`"
                  @click="toggleSort(col.key)"
                >
                  <span class="schedule-board__th-content">
                    {{ col.label }}
                    <span class="schedule-board__sort-icon" :class="sortIconClass(col.key)">{{ sortIcon(col.key) }}</span>
                  </span>
                </th>
              </tr>
            </thead>
            <tbody>
              <template v-if="!sortKey">
                <template v-for="lane in resourceLanes" :key="lane.resourceId">
                  <tr class="schedule-board__group-row">
                    <td colspan="6">
                      <span class="schedule-board__group-name">{{ lane.resourceId }}</span>
                      <span class="schedule-board__group-meta">{{ lane.resourceGroupName }} · {{ lane.items.length }} 个任务</span>
                    </td>
                  </tr>
                  <tr
                    v-for="item in lane.items"
                    :key="`${item.taskId}-${item.resourceId}-${item.startAt}`"
                    class="schedule-board__data-row"
                    :data-testid="`schedule-row-${item.taskId}`"
                  >
                    <td class="schedule-board__cell--task">{{ item.taskId }}</td>
                    <td>{{ item.resourceId }}</td>
                    <td>{{ item.resourceGroupName }}</td>
                    <td>{{ formatDateTime(item.startAt) }}</td>
                    <td>{{ formatDateTime(item.endAt) }}</td>
                    <td>{{ formatDuration(item.startAt, item.endAt) }}</td>
                  </tr>
                </template>
              </template>
              <template v-else>
                <tr
                  v-for="item in sortedItems"
                  :key="`${item.taskId}-${item.resourceId}-${item.startAt}`"
                  class="schedule-board__data-row"
                  :data-testid="`schedule-row-${item.taskId}`"
                >
                  <td class="schedule-board__cell--task">{{ item.taskId }}</td>
                  <td>{{ item.resourceId }}</td>
                  <td>{{ item.resourceGroupName }}</td>
                  <td>{{ formatDateTime(item.startAt) }}</td>
                  <td>{{ formatDateTime(item.endAt) }}</td>
                  <td>{{ formatDuration(item.startAt, item.endAt) }}</td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </template>

      <!-- Gantt View -->
      <template v-else>
        <div class="schedule-board__gantt" aria-label="甘特图">
          <div class="schedule-board__gantt-header">
            <div class="schedule-board__gantt-resource-col">资源</div>
            <div class="schedule-board__gantt-timeline">
              <div class="schedule-board__gantt-ticks">
                <span
                  v-for="tick in ganttTicks"
                  :key="tick.label"
                  class="schedule-board__gantt-tick"
                  :style="{ left: `${tick.position}%` }"
                >
                  {{ tick.label }}
                </span>
              </div>
            </div>
          </div>
          <div class="schedule-board__gantt-body">
            <template v-for="lane in ganttLanes" :key="lane.resourceId">
              <div class="schedule-board__gantt-row" :data-testid="`gantt-row-${lane.resourceId}`">
                <div class="schedule-board__gantt-resource-col">
                  <div class="schedule-board__gantt-resource-name">{{ lane.resourceId }}</div>
                  <div class="schedule-board__gantt-resource-meta">{{ lane.resourceGroupName }} · {{ lane.items.length }} 个任务</div>
                </div>
                <div class="schedule-board__gantt-timeline">
                  <svg class="schedule-board__gantt-svg" viewBox="0 0 100 100" preserveAspectRatio="none">
                    <path
                      v-for="(link, index) in ganttDependencyLinks"
                      :key="index"
                      :d="link.path"
                      class="schedule-board__gantt-dependency"
                      marker-end="url(#arrowhead)"
                    />
                    <defs>
                      <marker id="arrowhead" markerWidth="6" markerHeight="4" refX="5" refY="2" orient="auto">
                        <polygon points="0 0, 6 2, 0 4" fill="#94a3b8" />
                      </marker>
                    </defs>
                  </svg>
                  <div
                    v-for="item in lane.items"
                    :key="item.taskId"
                    class="schedule-board__gantt-bar"
                    :class="[
                      ganttBarClass(item.taskId),
                      { 'schedule-board__gantt-bar--active': selectedGanttTask?.taskId === item.taskId },
                      { 'schedule-board__gantt-bar--dragging': draggingTaskId === item.taskId }
                    ]"
                    :style="ganttBarStyle(item)"
                    :data-testid="`gantt-bar-${item.taskId}`"
                    @click="selectedGanttTask = item"
                    @mousedown="startDrag($event, item)"
                  >
                    <span class="schedule-board__gantt-bar-label">{{ item.taskId }}</span>
                    <span class="schedule-board__gantt-bar-time">{{ formatDuration(item.startAt, item.endAt) }}</span>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </template>
    </div>
    <div v-else class="schedule-board__empty">暂无排程结果</div>
  </main>
</template>

<style scoped>
.schedule-board {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.schedule-board__filter {
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
}

.schedule-board__filter-input {
  flex: 1;
  min-height: 32px;
  padding: 0 28px 0 12px;
  border: 1px solid rgba(203, 213, 225, 0.92);
  border-radius: 8px;
  font-size: 0.84rem;
  color: #334155;
  background: #fff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.schedule-board__filter-input:focus {
  outline: none;
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.schedule-board__filter-clear {
  position: absolute;
  right: 10px;
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(203, 213, 225, 0.6);
  color: #64748b;
  font-size: 0.7rem;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.schedule-board__filter-clear:hover {
  background: rgba(148, 163, 184, 0.5);
  color: #1e293b;
}

.schedule-board__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.schedule-board__header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.schedule-board__view-toggle {
  display: inline-flex;
  align-items: center;
  border: 1px solid rgba(203, 213, 225, 0.92);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.schedule-board__view-btn {
  min-height: 28px;
  padding: 0 12px;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-secondary);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.schedule-board__view-btn:hover {
  background: rgba(241, 245, 249, 0.8);
}

.schedule-board__view-btn--active {
  background: var(--color-primary);
  color: #fff;
}

.schedule-board__view-btn--active:hover {
  background: var(--color-primary-strong);
}

.schedule-board__table-wrap {
  border: 1px solid rgba(191, 219, 254, 0.9);
  border-radius: 12px;
  overflow: auto;
  background: #fff;
}

.schedule-board__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.86rem;
}

.schedule-board__table thead th {
  position: sticky;
  top: 0;
  padding: 10px 12px;
  background: rgba(219, 234, 254, 0.85);
  color: #1e3a8a;
  font-weight: 700;
  font-size: 0.8rem;
  text-align: left;
  border-bottom: 1px solid rgba(191, 219, 254, 0.9);
  white-space: nowrap;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s ease;
}

.schedule-board__table thead th:hover {
  background: rgba(191, 219, 254, 0.7);
}

.schedule-board__table thead th:first-child {
  border-top-left-radius: 12px;
}

.schedule-board__table thead th:last-child {
  border-top-right-radius: 12px;
}

.schedule-board__th--sorted {
  background: rgba(147, 197, 253, 0.6) !important;
}

.schedule-board__th-content {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.schedule-board__sort-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  font-size: 0.72rem;
  color: #94a3b8;
  transition: color 0.15s ease;
}

.schedule-board__sort-icon--active {
  color: #1e3a8a;
  font-weight: 700;
}

.schedule-board__group-row td {
  padding: 8px 12px;
  background: rgba(241, 245, 249, 0.9);
  border-bottom: 1px solid rgba(203, 213, 225, 0.7);
  font-weight: 600;
}

.schedule-board__group-name {
  color: #1e3a8a;
  font-size: 0.84rem;
  font-weight: 700;
}

.schedule-board__group-meta {
  margin-left: 8px;
  color: var(--text-secondary);
  font-size: 0.78rem;
  font-weight: 500;
}

.schedule-board__data-row:nth-child(even of .schedule-board__data-row) {
  background: rgba(248, 250, 252, 0.7);
}

.schedule-board__data-row:hover {
  background: rgba(219, 234, 254, 0.35);
}

.schedule-board__data-row td {
  padding: 10px 12px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.7);
  color: #334155;
  white-space: nowrap;
}

.schedule-board__cell--task {
  font-weight: 700;
  color: #1e293b;
}

.schedule-board__empty {
  padding: 24px;
  border-radius: 12px;
  border: 1px dashed var(--border-strong);
  text-align: center;
  color: var(--text-muted);
  font-size: 0.88rem;
}

/* Gantt View */
.schedule-board__gantt {
  border: 1px solid rgba(191, 219, 254, 0.9);
  border-radius: 12px;
  overflow: auto;
  background: #fff;
}

.schedule-board__gantt-header {
  display: flex;
  align-items: center;
  position: sticky;
  top: 0;
  z-index: 2;
  background: rgba(219, 234, 254, 0.85);
  border-bottom: 1px solid rgba(191, 219, 254, 0.9);
  min-height: 40px;
}

.schedule-board__gantt-resource-col {
  flex: 0 0 160px;
  padding: 8px 12px;
  font-weight: 700;
  font-size: 0.8rem;
  color: #1e3a8a;
  border-right: 1px solid rgba(191, 219, 254, 0.6);
}

.schedule-board__gantt-timeline {
  flex: 1;
  position: relative;
  min-width: 400px;
  padding: 0 12px;
}

.schedule-board__gantt-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.schedule-board__gantt-dependency {
  fill: none;
  stroke: #94a3b8;
  stroke-width: 0.3;
  opacity: 0.75;
}

.schedule-board__gantt-ticks {
  position: relative;
  height: 24px;
}

.schedule-board__gantt-tick {
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  font-size: 0.72rem;
  color: #64748b;
  white-space: nowrap;
}

.schedule-board__gantt-body {
  display: flex;
  flex-direction: column;
}

.schedule-board__gantt-row {
  display: flex;
  align-items: center;
  min-height: 56px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.7);
}

.schedule-board__gantt-row:last-child {
  border-bottom: 0;
}

.schedule-board__gantt-row:nth-child(even) {
  background: rgba(248, 250, 252, 0.5);
}

.schedule-board__gantt-resource-name {
  font-weight: 700;
  font-size: 0.84rem;
  color: #1e293b;
}

.schedule-board__gantt-resource-meta {
  margin-top: 2px;
  font-size: 0.72rem;
  color: var(--text-secondary);
}

.schedule-board__gantt-bar {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  height: 28px;
  border-radius: 6px;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.76rem;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  cursor: pointer;
  transition: filter 0.15s ease, box-shadow 0.15s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
}

.schedule-board__gantt-bar:hover {
  filter: brightness(1.08);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}

.schedule-board__gantt-bar--primary {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
}

.schedule-board__gantt-bar--secondary {
  background: linear-gradient(135deg, #10b981, #059669);
}

.schedule-board__gantt-bar--accent {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.schedule-board__gantt-bar--danger {
  background: linear-gradient(135deg, #ef4444, #dc2626);
}

.schedule-board__gantt-bar-label {
  overflow: hidden;
  text-overflow: ellipsis;
}

.schedule-board__gantt-bar-time {
  opacity: 0.88;
  font-weight: 500;
  font-size: 0.7rem;
}

.schedule-board__gantt-bar--active {
  outline: 2px solid #1e293b;
  outline-offset: 2px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.schedule-board__gantt-bar--dragging {
  cursor: grabbing;
  z-index: 10;
  filter: brightness(1.12);
}

/* Gantt Detail Panel */
.schedule-board__gantt-detail {
  margin-bottom: 12px;
  border: 1px solid rgba(191, 219, 254, 0.9);
  border-radius: 12px;
  background: #fff;
  overflow: hidden;
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.schedule-board__gantt-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: rgba(219, 234, 254, 0.6);
  border-bottom: 1px solid rgba(191, 219, 254, 0.6);
}

.schedule-board__gantt-detail-title {
  font-size: 0.9rem;
  font-weight: 700;
  color: #1e3a8a;
}

.schedule-board__gantt-detail-close {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  font-size: 0.84rem;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.schedule-board__gantt-detail-close:hover {
  background: rgba(226, 232, 240, 0.8);
  color: #1e293b;
}

.schedule-board__gantt-detail-body {
  padding: 12px 14px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
}

.schedule-board__gantt-detail-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.schedule-board__gantt-detail-label {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.schedule-board__gantt-detail-value {
  font-size: 0.84rem;
  font-weight: 500;
  color: #1e293b;
}

.button--sm {
  min-height: 24px;
  padding: 0 8px;
  font-size: 0.76rem;
}

@media (max-width: 720px) {
  .schedule-board__table-wrap {
    border-radius: 10px;
  }

  .schedule-board__table {
    font-size: 0.8rem;
  }

  .schedule-board__table thead th,
  .schedule-board__group-row td,
  .schedule-board__data-row td {
    padding: 8px;
  }

  .schedule-board__gantt-resource-col {
    flex: 0 0 120px;
    padding: 6px 8px;
  }

  .schedule-board__gantt-timeline {
    min-width: 300px;
    padding: 0 8px;
  }

  .schedule-board__gantt-bar {
    height: 24px;
    padding: 0 6px;
    font-size: 0.7rem;
  }
}

@media print {
  .schedule-board__header-actions,
  .schedule-board__view-toggle,
  .schedule-board__filter,
  .schedule-board__gantt-detail,
  .schedule-board__gantt-detail-close,
  .schedule-board__print-btn,
  .schedule-board__sort-icon,
  .schedule-board__th-content {
    display: none !important;
  }

  .schedule-board {
    gap: 8px;
    border: none;
    box-shadow: none;
  }

  .schedule-board__table-wrap,
  .schedule-board__gantt {
    border: 1px solid #cbd5e1;
    border-radius: 0;
  }

  .schedule-board__table thead th {
    background: #e2e8f0 !important;
    color: #1e293b !important;
    border-bottom: 1px solid #94a3b8;
  }

  .schedule-board__group-row td {
    background: #f1f5f9 !important;
  }

  .schedule-board__data-row:hover {
    background: transparent !important;
  }

  .schedule-board__gantt-bar {
    box-shadow: none !important;
    border: 1px solid rgba(0, 0, 0, 0.15);
  }

  .schedule-board__gantt-header {
    background: #e2e8f0 !important;
  }

  .schedule-board__gantt-row:nth-child(even) {
    background: transparent !important;
  }

  .schedule-board__empty {
    border: 1px solid #cbd5e1;
  }
}
</style>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ScheduledItem } from '@/stores/scheduleDraft'

type ViewMode = 'table' | 'gantt'
type SortKey = 'taskId' | 'resourceId' | 'resourceGroupName' | 'startAt' | 'endAt' | 'duration'
type SortOrder = 'asc' | 'desc'

const props = defineProps<{
  items: ScheduledItem[]
}>()

const emit = defineEmits<{
  (e: 'update-task-time', payload: { taskId: string; newStartAt: string; newEndAt: string }): void
}>()

const viewMode = ref<ViewMode>('table')
const selectedGanttTask = ref<ScheduledItem | null>(null)
const filterText = ref('')
const sortKey = ref<SortKey | null>(null)
const sortOrder = ref<SortOrder>('asc')
const draggingTaskId = ref<string | null>(null)
const dragStartX = ref(0)
const dragItem = ref<ScheduledItem | null>(null)

const columns: { key: SortKey; label: string }[] = [
  { key: 'taskId', label: '任务编号' },
  { key: 'resourceId', label: '资源编号' },
  { key: 'resourceGroupName', label: '资源组' },
  { key: 'startAt', label: '开始时间' },
  { key: 'endAt', label: '结束时间' },
  { key: 'duration', label: '持续时间' }
]

function toggleSort(key: SortKey) {
  if (sortKey.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortOrder.value = 'asc'
  }
}

function clearSort() {
  sortKey.value = null
  sortOrder.value = 'asc'
}

function sortIcon(key: SortKey) {
  if (sortKey.value !== key) {
    return '↕'
  }
  return sortOrder.value === 'asc' ? '↑' : '↓'
}

function sortIconClass(key: SortKey) {
  return sortKey.value === key ? 'schedule-board__sort-icon--active' : ''
}

function getDurationMinutes(item: ScheduledItem): number {
  return Math.max(new Date(item.endAt).getTime() - new Date(item.startAt).getTime(), 0) / (1000 * 60)
}

const filteredItems = computed(() => {
  const text = filterText.value.trim().toLowerCase()
  if (!text) return props.items
  return props.items.filter(
    (item) =>
      item.taskId.toLowerCase().includes(text) ||
      item.resourceId.toLowerCase().includes(text) ||
      item.resourceGroupName.toLowerCase().includes(text)
  )
})

const sortedItems = computed(() => {
  if (!sortKey.value) {
    return filteredItems.value
  }

  const key = sortKey.value
  const order = sortOrder.value === 'asc' ? 1 : -1

  return [...filteredItems.value].sort((left, right) => {
    if (key === 'duration') {
      const leftVal = getDurationMinutes(left)
      const rightVal = getDurationMinutes(right)
      return (leftVal - rightVal) * order
    }

    const leftVal = left[key]
    const rightVal = right[key]

    if (leftVal < rightVal) {
      return -1 * order
    }
    if (leftVal > rightVal) {
      return 1 * order
    }
    return 0
  })
})

const resourceLanes = computed(() => {
  const lanes = new Map<string, { resourceId: string; resourceGroupName: string; items: ScheduledItem[] }>()

  filteredItems.value.forEach((item) => {
    const lane = lanes.get(item.resourceId)
    if (lane) {
      lane.items.push(item)
      return
    }

    lanes.set(item.resourceId, {
      resourceId: item.resourceId,
      resourceGroupName: item.resourceGroupName,
      items: [item]
    })
  })

  return Array.from(lanes.values()).map((lane) => ({
    ...lane,
    items: [...lane.items].sort((left, right) => new Date(left.startAt).getTime() - new Date(right.startAt).getTime())
  }))
})

/* Gantt computed */
const ganttTimeRange = computed(() => {
  if (filteredItems.value.length === 0) {
    return { min: 0, max: 0, totalMs: 1 }
  }
  const times = filteredItems.value.map((item) => ({
    start: new Date(item.startAt).getTime(),
    end: new Date(item.endAt).getTime()
  }))
  const min = Math.min(...times.map((t) => t.start))
  const max = Math.max(...times.map((t) => t.end))
  // Add 10% padding on each side for visual breathing room
  const padding = (max - min) * 0.1
  return { min: min - padding, max: max + padding, totalMs: max - min + padding * 2 }
})

const ganttLanes = computed(() => resourceLanes.value)

const ganttTicks = computed(() => {
  const { min, max } = ganttTimeRange.value
  const ticks: { label: string; position: number }[] = []
  const duration = max - min
  if (duration <= 0) return ticks

  // Determine tick interval based on total duration
  const hourMs = 60 * 60 * 1000
  const dayMs = 24 * hourMs
  let interval: number
  if (duration <= 4 * hourMs) {
    interval = 30 * 60 * 1000 // 30 minutes
  } else if (duration <= 12 * hourMs) {
    interval = hourMs // 1 hour
  } else if (duration <= 3 * dayMs) {
    interval = 2 * hourMs // 2 hours
  } else {
    interval = 4 * hourMs // 4 hours
  }

  const start = Math.floor(min / interval) * interval
  for (let t = start; t <= max; t += interval) {
    const position = ((t - min) / duration) * 100
    if (position >= -5 && position <= 105) {
      const date = new Date(t)
      const h = String(date.getUTCHours()).padStart(2, '0')
      const m = String(date.getUTCMinutes()).padStart(2, '0')
      ticks.push({ label: `${h}:${m}`, position })
    }
  }
  return ticks
})

const ganttDependencyLinks = computed(() => {
  const links: { path: string }[] = []
  const { min, totalMs } = ganttTimeRange.value
  const rowHeight = 56 // matches .schedule-board__gantt-row min-height
  const laneMap = new Map<string, number>()
  ganttLanes.value.forEach((lane, index) => {
    laneMap.set(lane.resourceId, index)
  })

  filteredItems.value.forEach((item) => {
    if (!item.dependencyTaskIds || item.dependencyTaskIds.length === 0) return
    const targetLaneIndex = laneMap.get(item.resourceId)
    if (targetLaneIndex === undefined) return
    const targetStart = new Date(item.startAt).getTime()
    const targetY = targetLaneIndex * rowHeight + rowHeight / 2
    const targetX = ((targetStart - min) / totalMs) * 100

    item.dependencyTaskIds.forEach((depId) => {
      const dep = filteredItems.value.find((i) => i.taskId === depId)
      if (!dep) return
      const sourceLaneIndex = laneMap.get(dep.resourceId)
      if (sourceLaneIndex === undefined) return
      const sourceEnd = new Date(dep.endAt).getTime()
      const sourceY = sourceLaneIndex * rowHeight + rowHeight / 2
      const sourceX = ((sourceEnd - min) / totalMs) * 100

      // Normalize to 0-100 viewBox: y needs to be scaled to match x percentage
      // Since SVG uses percentage-based viewBox, we need a common scale.
      // Instead, let's use a large viewBox and calculate pixel positions.
      // Actually, easier: use viewBox="0 0 100 {totalHeight}" and scale y accordingly.
      // Let's keep viewBox="0 0 100 100" and normalize y as percentage of total height.
      const totalHeight = ganttLanes.value.length * rowHeight
      const sx = Math.max(0, Math.min(100, sourceX))
      const sy = (sourceY / totalHeight) * 100
      const tx = Math.max(0, Math.min(100, targetX))
      const ty = (targetY / totalHeight) * 100

      const midX = (sx + tx) / 2
      const path = `M ${sx} ${sy} C ${midX} ${sy}, ${midX} ${ty}, ${tx} ${ty}`
      links.push({ path })
    })
  })

  return links
})

const taskColorMap = computed(() => {
  const colors = ['primary', 'secondary', 'accent', 'danger'] as const
  const map = new Map<string, typeof colors[number]>()
  filteredItems.value.forEach((item, index) => {
    map.set(item.taskId, colors[index % colors.length])
  })
  return map
})

function ganttBarClass(taskId: string) {
  const color = taskColorMap.value.get(taskId) ?? 'primary'
  return `schedule-board__gantt-bar--${color}`
}

function ganttBarStyle(item: ScheduledItem) {
  const { min, totalMs } = ganttTimeRange.value
  const start = new Date(item.startAt).getTime()
  const end = new Date(item.endAt).getTime()
  const left = ((start - min) / totalMs) * 100
  const width = ((end - start) / totalMs) * 100
  return {
    left: `${Math.max(0, left)}%`,
    width: `${Math.max(0.5, width)}%`
  }
}

function formatDateTime(value: string) {
  const date = new Date(value)
  const y = date.getUTCFullYear()
  const m = String(date.getUTCMonth() + 1).padStart(2, '0')
  const d = String(date.getUTCDate()).padStart(2, '0')
  const h = String(date.getUTCHours()).padStart(2, '0')
  const min = String(date.getUTCMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}`
}

function exportSchedule() {
  if (props.items.length === 0) return

  const headers = ['任务编号', '资源编号', '资源组', '开始时间', '结束时间', '持续时间']
  const rows = props.items.map((item) => [
    item.taskId,
    item.resourceId,
    item.resourceGroupName,
    formatDateTime(item.startAt),
    formatDateTime(item.endAt),
    formatDuration(item.startAt, item.endAt)
  ])

  const escapeCsv = (value: string) => {
    const str = String(value)
    if (str.includes(',') || str.includes('"') || str.includes('\n')) {
      return `"${str.replace(/"/g, '""')}"`
    }
    return str
  }

  const csvContent = [headers, ...rows].map((row) => row.map(escapeCsv).join(',')).join('\r\n')
  const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  const timestamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')
  link.href = URL.createObjectURL(blob)
  link.download = `排程结果-${timestamp}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}

function printSchedule() {
  window.print()
}

function startDrag(event: MouseEvent, item: ScheduledItem) {
  if (viewMode.value !== 'gantt') return
  event.preventDefault()
  draggingTaskId.value = item.taskId
  dragStartX.value = event.clientX
  dragItem.value = item

  document.addEventListener('mousemove', onDragMove)
  document.addEventListener('mouseup', onDragEnd)
}

function onDragMove(event: MouseEvent) {
  if (!draggingTaskId.value || !dragItem.value) return
  // 拖拽过程中不做实时更新，只在mouseup时计算
  // 可以在这里添加视觉反馈
}

function onDragEnd(event: MouseEvent) {
  document.removeEventListener('mousemove', onDragMove)
  document.removeEventListener('mouseup', onDragEnd)

  if (!draggingTaskId.value || !dragItem.value) {
    draggingTaskId.value = null
    dragItem.value = null
    return
  }

  const timelineEl = document.querySelector('.schedule-board__gantt-timeline') as HTMLElement | null
  if (!timelineEl) {
    draggingTaskId.value = null
    dragItem.value = null
    return
  }

  const deltaPx = event.clientX - dragStartX.value
  const timelineWidth = timelineEl.clientWidth
  if (timelineWidth <= 0) {
    draggingTaskId.value = null
    dragItem.value = null
    return
  }

  const { totalMs } = ganttTimeRange.value
  const deltaMs = (deltaPx / timelineWidth) * totalMs

  if (Math.abs(deltaMs) < 60 * 1000) {
    // 忽略小于1分钟的拖拽
    draggingTaskId.value = null
    dragItem.value = null
    return
  }

  const item = dragItem.value
  const duration = new Date(item.endAt).getTime() - new Date(item.startAt).getTime()
  const newStartAt = new Date(new Date(item.startAt).getTime() + deltaMs).toISOString().replace('.000', '')
  const newEndAt = new Date(new Date(item.startAt).getTime() + deltaMs + duration).toISOString().replace('.000', '')

  emit('update-task-time', {
    taskId: item.taskId,
    newStartAt,
    newEndAt
  })

  draggingTaskId.value = null
  dragItem.value = null
}

function formatDuration(startAt: string, endAt: string) {
  const start = new Date(startAt).getTime()
  const end = new Date(endAt).getTime()
  const diff = Math.max(end - start, 0)
  const hours = Math.floor(diff / (1000 * 60 * 60))
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  if (hours > 0 && minutes > 0) {
    return `${hours}小时${minutes}分`
  }
  if (hours > 0) {
    return `${hours}小时`
  }
  return `${minutes}分`
}
</script>

import { reactive } from 'vue'
import { dashboardApi } from '@/api/dashboard'

export interface CapacitySummary {
  status: string
  loadRate: number
}

export interface WorkOrderStats {
  total: number
  urgentCount: number
  riskDistribution: Record<string, number>
}

export interface ResourceStats {
  total: number
  defaultPlannerCount: number
}

export interface DashboardOverview {
  capacitySummary: CapacitySummary
  workOrderStats: WorkOrderStats
  resourceStats: ResourceStats
}

const initialState = () => ({
  loading: false,
  summary: null as CapacitySummary | null,
  overview: null as DashboardOverview | null,
  error: ''
})

export const dashboardState = reactive(initialState())

export function resetDashboardState() {
  Object.assign(dashboardState, initialState())
}

export async function loadDashboardSummary() {
  dashboardState.loading = true
  dashboardState.error = ''

  try {
    dashboardState.summary = await dashboardApi.loadCapacitySummary()
  } catch (error) {
    dashboardState.error =
      error instanceof Error && error.message.startsWith('HTTP ')
        ? '加载驾驶舱摘要失败'
        : error instanceof Error
          ? error.message
          : '加载驾驶舱摘要失败'
    dashboardState.summary = null
  } finally {
    dashboardState.loading = false
  }
}

export async function loadDashboardOverview() {
  dashboardState.loading = true
  dashboardState.error = ''

  try {
    dashboardState.overview = await dashboardApi.loadOverview()
    if (dashboardState.overview) {
      dashboardState.summary = dashboardState.overview.capacitySummary
    }
  } catch (error) {
    dashboardState.error =
      error instanceof Error && error.message.startsWith('HTTP ')
        ? '加载驾驶舱概览失败'
        : error instanceof Error
          ? error.message
          : '加载驾驶舱概览失败'
    dashboardState.overview = null
  } finally {
    dashboardState.loading = false
  }
}

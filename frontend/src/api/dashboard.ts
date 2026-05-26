import { http } from '@/api/http'

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

async function loadCapacitySummary() {
  return http.get<CapacitySummary>('/api/dashboard/capacity-summary')
}

async function loadOverview() {
  return http.get<DashboardOverview>('/api/dashboard/overview')
}

export const dashboardApi = {
  loadCapacitySummary,
  loadOverview
}

import { beforeEach, describe, expect, it, vi } from 'vitest'

const { loadCapacitySummary, loadOverview } = vi.hoisted(() => ({
  loadCapacitySummary: vi.fn(),
  loadOverview: vi.fn()
}))

vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    loadCapacitySummary,
    loadOverview
  }
}))

import { dashboardState, loadDashboardSummary, loadDashboardOverview, resetDashboardState } from '@/stores/dashboard'

describe('dashboard store', () => {
  beforeEach(() => {
    resetDashboardState()
    loadCapacitySummary.mockReset()
    loadOverview.mockReset()
  })

  it('会加载驾驶舱摘要', async () => {
    loadCapacitySummary.mockResolvedValue({
      status: 'tight',
      loadRate: 1
    })

    await loadDashboardSummary()

    expect(dashboardState.summary).toEqual({
      status: 'tight',
      loadRate: 1
    })
    expect(dashboardState.loading).toBe(false)
    expect(dashboardState.error).toBe('')
  })

  it('加载失败时记录可读错误并清空旧摘要', async () => {
    dashboardState.summary = {
      status: 'feasible',
      loadRate: 0.5
    }
    loadCapacitySummary.mockRejectedValue(new Error('HTTP 500'))

    await loadDashboardSummary()

    expect(dashboardState.summary).toBeNull()
    expect(dashboardState.error).toBe('加载驾驶舱摘要失败')
    expect(dashboardState.loading).toBe(false)
  })

  it('驾驶舱接口异常时写入可读错误语义', async () => {
    loadCapacitySummary.mockRejectedValue(new Error('HTTP 500'))

    await loadDashboardSummary()

    expect(dashboardState.error).toBe('加载驾驶舱摘要失败')
    expect(dashboardState.summary).toBeNull()
    expect(dashboardState.loading).toBe(false)
  })

  it('会加载驾驶舱概览并同步摘要', async () => {
    loadOverview.mockResolvedValue({
      capacitySummary: { status: 'feasible', loadRate: 0.44 },
      workOrderStats: { total: 2, urgentCount: 1, riskDistribution: { low: 1, high: 1 } },
      resourceStats: { total: 3, defaultPlannerCount: 2 }
    })

    await loadDashboardOverview()

    expect(dashboardState.overview).toEqual({
      capacitySummary: { status: 'feasible', loadRate: 0.44 },
      workOrderStats: { total: 2, urgentCount: 1, riskDistribution: { low: 1, high: 1 } },
      resourceStats: { total: 3, defaultPlannerCount: 2 }
    })
    expect(dashboardState.summary).toEqual({ status: 'feasible', loadRate: 0.44 })
    expect(dashboardState.loading).toBe(false)
    expect(dashboardState.error).toBe('')
  })

  it('概览加载失败时记录可读错误', async () => {
    loadOverview.mockRejectedValue(new Error('HTTP 500'))

    await loadDashboardOverview()

    expect(dashboardState.error).toBe('加载驾驶舱概览失败')
    expect(dashboardState.overview).toBeNull()
    expect(dashboardState.loading).toBe(false)
  })
})

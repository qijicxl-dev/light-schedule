import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const { loadDashboardOverview, dashboardState, defaultOverview } = vi.hoisted(() => {
  const defaultOverview = () => ({
    capacitySummary: { status: 'tight', loadRate: 1 },
    workOrderStats: { total: 1, urgentCount: 0, riskDistribution: { low: 1 } },
    resourceStats: { total: 3, defaultPlannerCount: 2 }
  })
  return {
    loadDashboardOverview: vi.fn(),
    defaultOverview,
    dashboardState: {
      loading: false,
      summary: null as { status: string; loadRate: number } | null,
      overview: defaultOverview() as {
        capacitySummary: { status: string; loadRate: number }
        workOrderStats: { total: number; urgentCount: number; riskDistribution: Record<string, number> }
        resourceStats: { total: number; defaultPlannerCount: number }
      } | null,
      error: ''
    }
  }
})

vi.mock('@/stores/dashboard', () => ({
  loadDashboardOverview,
  dashboardState
}))

import BossDashboardView from '@/views/BossDashboardView.vue'

describe('BossDashboardView', () => {
  beforeEach(() => {
    dashboardState.loading = false
    dashboardState.overview = defaultOverview()
    dashboardState.summary = null
    dashboardState.error = ''
    loadDashboardOverview.mockReset()
    loadDashboardOverview.mockResolvedValue(undefined)
  })

  it('页面加载时会拉取驾驶舱概览', async () => {
    mount(BossDashboardView)
    await flushPromises()

    expect(loadDashboardOverview).toHaveBeenCalledTimes(1)
  })

  it('加载中时显示加载提示', () => {
    dashboardState.loading = true
    dashboardState.overview = null

    const wrapper = mount(BossDashboardView)

    expect(wrapper.text()).toContain('加载中')
  })

  it('加载失败时显示错误信息', () => {
    dashboardState.error = '加载驾驶舱概览失败'
    dashboardState.overview = null

    const wrapper = mount(BossDashboardView)

    expect(wrapper.text()).toContain('加载驾驶舱概览失败')
  })

  it('概览为空时显示空状态提示', async () => {
    dashboardState.overview = null

    const wrapper = mount(BossDashboardView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无驾驶舱摘要')
  })

  it('显示负荷摘要状态和负荷率', async () => {
    const wrapper = mount(BossDashboardView)
    await flushPromises()

    expect(wrapper.text()).toContain('tight')
    expect(wrapper.text()).toContain('100%')
  })

  it('显示工单统计数据', async () => {
    const wrapper = mount(BossDashboardView)
    await flushPromises()

    expect(wrapper.text()).toContain('工单统计')
    expect(wrapper.text()).toContain('工单总数')
    expect(wrapper.text()).toContain('1')
  })

  it('显示资源概览数据', async () => {
    const wrapper = mount(BossDashboardView)
    await flushPromises()

    expect(wrapper.text()).toContain('资源概览')
    expect(wrapper.text()).toContain('资源总数')
    expect(wrapper.text()).toContain('3')
  })
})

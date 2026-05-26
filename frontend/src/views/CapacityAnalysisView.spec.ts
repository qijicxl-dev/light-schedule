import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { CapacityAnalysisOverview } from '@/stores/capacityAnalysis'

type MockCapacityAnalysisState = {
  loading: boolean
  overview: CapacityAnalysisOverview | null
  error: string
}

const { loadCapacityAnalysisOverview, capacityAnalysisState } = vi.hoisted(() => {
  const defaultOverview = (): CapacityAnalysisOverview => ({
    trends: [
      {
        resourceId: 'LINE-A',
        bucketLabel: '2026-04-25 08:00',
        status: 'tight',
        loadRate: 0.95
      }
    ],
    groupDiffs: [
      {
        groupName: '冲压组',
        gapRate: 0.15
      }
    ],
    peakPeriods: [
      {
        bucketLabel: '2026-04-25 10:00',
        status: 'overloaded',
        loadRate: 1.02
      }
    ]
  })

  const capacityAnalysisState: MockCapacityAnalysisState = {
    loading: false,
    overview: defaultOverview(),
    error: ''
  }

  return {
    loadCapacityAnalysisOverview: vi.fn(),
    capacityAnalysisState,
    defaultOverview
  }
})

vi.mock('@/stores/capacityAnalysis', () => ({
  loadCapacityAnalysisOverview,
  capacityAnalysisState
}))

import CapacityAnalysisView from '@/views/CapacityAnalysisView.vue'

describe('CapacityAnalysisView', () => {
  beforeEach(() => {
    capacityAnalysisState.loading = false
    capacityAnalysisState.overview = {
      trends: [
        {
          resourceId: 'LINE-A',
          bucketLabel: '2026-04-25 08:00',
          status: 'tight',
          loadRate: 0.95
        }
      ],
      groupDiffs: [
        {
          groupName: '冲压组',
          gapRate: 0.15
        }
      ],
      peakPeriods: [
        {
          bucketLabel: '2026-04-25 10:00',
          status: 'overloaded',
          loadRate: 1.02
        }
      ]
    }
    capacityAnalysisState.error = ''
    loadCapacityAnalysisOverview.mockReset()
    loadCapacityAnalysisOverview.mockResolvedValue(undefined)
  })

  it('页面加载时会拉取能力分析数据', async () => {
    mount(CapacityAnalysisView)
    await flushPromises()

    expect(loadCapacityAnalysisOverview).toHaveBeenCalledTimes(1)
  })

  it('加载中时显示加载提示', () => {
    capacityAnalysisState.loading = true
    capacityAnalysisState.overview = null

    const wrapper = mount(CapacityAnalysisView)

    expect(wrapper.text()).toContain('加载中')
  })

  it('加载失败时显示错误信息', () => {
    capacityAnalysisState.error = '加载能力分析失败'
    capacityAnalysisState.overview = null

    const wrapper = mount(CapacityAnalysisView)

    expect(wrapper.text()).toContain('加载能力分析失败')
  })

  it('数据为空时显示空状态提示', async () => {
    capacityAnalysisState.overview = null

    const wrapper = mount(CapacityAnalysisView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无能力分析数据')
  })

  it('显示真实能力分析摘要', async () => {
    const wrapper = mount(CapacityAnalysisView)
    await flushPromises()

    expect(wrapper.text()).toContain('能力分析')
    expect(wrapper.text()).toContain('资源负荷趋势')
    expect(wrapper.text()).toContain('LINE-A')
    expect(wrapper.text()).toContain('95%')
    expect(wrapper.text()).toContain('同组差异')
    expect(wrapper.text()).toContain('冲压组')
    expect(wrapper.text()).toContain('15%')
    expect(wrapper.text()).toContain('高负荷时段')
    expect(wrapper.text()).toContain('2026-04-25 10:00')
    expect(wrapper.text()).toContain('102%')
  })
})

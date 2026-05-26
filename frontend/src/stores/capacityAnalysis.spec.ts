import { beforeEach, describe, expect, it, vi } from 'vitest'

const { loadOverview } = vi.hoisted(() => ({
  loadOverview: vi.fn()
}))

vi.mock('@/api/capacityAnalysis', () => ({
  capacityAnalysisApi: {
    loadOverview
  }
}))

import {
  capacityAnalysisState,
  loadCapacityAnalysisOverview,
  resetCapacityAnalysisState
} from '@/stores/capacityAnalysis'

describe('capacityAnalysis store', () => {
  beforeEach(() => {
    resetCapacityAnalysisState()
    loadOverview.mockReset()
  })

  it('会加载能力分析总览', async () => {
    loadOverview.mockResolvedValue({
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

    await loadCapacityAnalysisOverview()

    expect(capacityAnalysisState.overview?.trends[0].resourceId).toBe('LINE-A')
    expect(capacityAnalysisState.overview?.groupDiffs[0].groupName).toBe('冲压组')
    expect(capacityAnalysisState.overview?.peakPeriods[0].status).toBe('overloaded')
    expect(capacityAnalysisState.loading).toBe(false)
    expect(capacityAnalysisState.error).toBe('')
  })

  it('加载失败时记录可读错误并清空数据', async () => {
    capacityAnalysisState.overview = {
      trends: [
        {
          resourceId: 'LINE-A',
          bucketLabel: '2026-04-25 08:00',
          status: 'tight',
          loadRate: 0.95
        }
      ],
      groupDiffs: [],
      peakPeriods: []
    }
    loadOverview.mockRejectedValue(new Error('HTTP 500'))

    await loadCapacityAnalysisOverview()

    expect(capacityAnalysisState.overview).toBeNull()
    expect(capacityAnalysisState.error).toBe('加载能力分析失败')
    expect(capacityAnalysisState.loading).toBe(false)
  })
})

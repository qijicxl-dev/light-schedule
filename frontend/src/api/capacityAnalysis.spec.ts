import { afterEach, describe, expect, it, vi } from 'vitest'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('capacity analysis api', () => {
  it('会读取能力分析总览接口', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        trends: [],
        groupDiffs: [],
        peakPeriods: []
      })
    })
    vi.stubGlobal('fetch', fetchMock)

    const { capacityAnalysisApi } = await import('./capacityAnalysis')
    const result = await capacityAnalysisApi.loadOverview()

    expect(fetchMock).toHaveBeenCalledWith('/api/capacity-analysis/overview')
    expect(result).toEqual({
      trends: [],
      groupDiffs: [],
      peakPeriods: []
    })
  })
})

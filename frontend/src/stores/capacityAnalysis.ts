import { reactive } from 'vue'
import { capacityAnalysisApi } from '@/api/capacityAnalysis'

export interface CapacityTrendItem {
  resourceId: string
  bucketLabel: string
  status: string
  loadRate: number
}

export interface CapacityGroupDiffItem {
  groupName: string
  gapRate: number
}

export interface CapacityPeakPeriodItem {
  bucketLabel: string
  status: string
  loadRate: number
}

export interface CapacityAnalysisOverview {
  trends: CapacityTrendItem[]
  groupDiffs: CapacityGroupDiffItem[]
  peakPeriods: CapacityPeakPeriodItem[]
}

const initialState = () => ({
  loading: false,
  overview: null as CapacityAnalysisOverview | null,
  error: ''
})

export const capacityAnalysisState = reactive(initialState())

export function resetCapacityAnalysisState() {
  Object.assign(capacityAnalysisState, initialState())
}

export async function loadCapacityAnalysisOverview() {
  capacityAnalysisState.loading = true
  capacityAnalysisState.error = ''

  try {
    capacityAnalysisState.overview = await capacityAnalysisApi.loadOverview()
  } catch (error) {
    capacityAnalysisState.error =
      error instanceof Error && error.message.startsWith('HTTP ')
        ? '加载能力分析失败'
        : error instanceof Error
          ? error.message
          : '加载能力分析失败'
    capacityAnalysisState.overview = null
  } finally {
    capacityAnalysisState.loading = false
  }
}

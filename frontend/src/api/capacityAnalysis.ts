import { http } from '@/api/http'
import type { CapacityAnalysisOverview } from '@/stores/capacityAnalysis'

async function loadOverview() {
  return http.get<CapacityAnalysisOverview>('/api/capacity-analysis/overview')
}

export const capacityAnalysisApi = {
  loadOverview
}

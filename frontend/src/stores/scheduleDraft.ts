import { reactive } from 'vue'
import { plannerApi } from '@/api/planner'

export interface TaskPoolItem {
  workOrderCode: string
  dueAt: string
  urgent: boolean
  materialRisk: string
  readiness: string
}

export interface ScheduledItem {
  taskId: string
  resourceId: string
  resourceGroupName: string
  startAt: string
  endAt: string
  dependencyTaskIds?: string[]
}

export interface Suggestion {
  action: string
  reason: string
}

export interface PublishResult {
  draftId: string
  auditId: string
  status: string
  writebackStatus: string
}

export interface WritebackStatus {
  auditId: string
  draftId: string
  status: string
  writebackStatus: string
  message: string
  retryable: boolean
  attemptCount: number
  maxAttempts: number
  nextRetryAt: string | null
}

export interface PublishStatusView extends WritebackStatus {}

const initialState = () => ({
  loading: false,
  draftId: '',
  taskPoolItems: [] as TaskPoolItem[],
  scheduledItems: [] as ScheduledItem[],
  affectedTaskIds: [] as string[],
  suggestions: [] as Suggestion[],
  urgentInsertion: null as { taskId: string; resourceId: string; startAt: string; endAt: string } | null,
  publishResult: null as PublishStatusView | null,
  error: '',
  urgentLoading: false,
  urgentError: '',
  publishLoading: false,
  publishError: ''
})

let latestPublishAttemptId = 0
let latestUrgentAttemptId = 0

export const scheduleDraftState = reactive(initialState())

const storeApi = {
  scheduleDraftState,
  resetScheduleDraftState,
  resetUrgentReplanResult,
  resetPublishResult,
  addTaskToSchedule,
  applySuggestionToSchedule,
  loadPlannerData,
  loadUrgentSuggestions,
  publishPlannerDraft,
  refreshPublishStatus
}

if (typeof window !== 'undefined' && import.meta.env.DEV) {
  ;(window as Window & { __scheduleDraftStore?: typeof storeApi }).__scheduleDraftStore = storeApi
}

export function resetScheduleDraftState() {
  latestPublishAttemptId = 0
  latestUrgentAttemptId = 0
  Object.assign(scheduleDraftState, initialState())
}

export function resetUrgentReplanResult() {
  latestUrgentAttemptId += 1
  scheduleDraftState.affectedTaskIds = []
  scheduleDraftState.suggestions = []
  scheduleDraftState.urgentInsertion = null
  scheduleDraftState.urgentError = ''
  scheduleDraftState.urgentLoading = false
}

export function resetPublishResult() {
  latestPublishAttemptId += 1
  scheduleDraftState.publishResult = null
  scheduleDraftState.publishError = ''
  scheduleDraftState.publishLoading = false
}

export function addTaskToSchedule(task: TaskPoolItem) {
  if (scheduleDraftState.scheduledItems.some((item) => item.taskId === task.workOrderCode)) {
    return
  }

  resetPublishResult()
  resetUrgentReplanResult()

  const contextItem = scheduleDraftState.scheduledItems[0]
  const resourceId = contextItem?.resourceId ?? 'LINE-A'
  const resourceGroupName = contextItem?.resourceGroupName ?? '默认资源组'
  const resourceTailItem = [...scheduleDraftState.scheduledItems]
    .filter((item) => item.resourceId === resourceId)
    .sort((left, right) => left.endAt.localeCompare(right.endAt))
    .at(-1)
  const startAt = resourceTailItem?.endAt ?? task.dueAt
  const endAt = new Date(new Date(startAt).getTime() + 2 * 60 * 60 * 1000).toISOString().replace('.000', '')

  scheduleDraftState.taskPoolItems = scheduleDraftState.taskPoolItems.filter((item) => item.workOrderCode !== task.workOrderCode)
  scheduleDraftState.scheduledItems = [
    ...scheduleDraftState.scheduledItems,
    {
      taskId: task.workOrderCode,
      resourceId,
      resourceGroupName,
      startAt,
      endAt,
      dependencyTaskIds: []
    }
  ]
}

export function applySuggestionToSchedule(suggestion: Suggestion) {
  if (!scheduleDraftState.suggestions.some((item) => item.action === suggestion.action && item.reason === suggestion.reason)) {
    return
  }

  resetPublishResult()
  scheduleDraftState.urgentError = ''

  const affectedItems = scheduleDraftState.affectedTaskIds
    .map((taskId) => scheduleDraftState.scheduledItems.find((item) => item.taskId === taskId))
    .filter((item): item is ScheduledItem => Boolean(item))

  const urgentInsertion = scheduleDraftState.urgentInsertion

  if (affectedItems.length === 0 && !urgentInsertion) {
    return
  }

  const remainingItems = scheduleDraftState.scheduledItems.filter(
    (item) => !scheduleDraftState.affectedTaskIds.includes(item.taskId)
  )
  const resourceId = urgentInsertion?.resourceId ?? affectedItems[0]?.resourceId ?? remainingItems[0]?.resourceId ?? 'LINE-A'
  const resourceGroupName =
    affectedItems[0]?.resourceGroupName ??
    scheduleDraftState.scheduledItems.find((item) => item.resourceId === resourceId)?.resourceGroupName ??
    '默认资源组'
  const resourceTailItem = remainingItems
    .filter((item) => item.resourceId === resourceId)
    .sort((left, right) => left.endAt.localeCompare(right.endAt))
    .at(-1)
  let cursor = urgentInsertion?.startAt ?? resourceTailItem?.endAt ?? affectedItems[0]?.startAt ?? ''

  const updatedItems: ScheduledItem[] = []

  if (urgentInsertion) {
    updatedItems.push({
      taskId: urgentInsertion.taskId,
      resourceId,
      resourceGroupName,
      startAt: urgentInsertion.startAt,
      endAt: urgentInsertion.endAt
    })
    cursor = urgentInsertion.endAt
  }

  const updatedAffectedItems = affectedItems.map((item) => {
    const duration = new Date(item.endAt).getTime() - new Date(item.startAt).getTime()
    const startAt = cursor
    const endAt = new Date(new Date(startAt).getTime() + duration).toISOString().replace('.000', '')
    cursor = endAt
    return {
      ...item,
      resourceId,
      resourceGroupName,
      startAt,
      endAt
    }
  })

  scheduleDraftState.scheduledItems = [...remainingItems, ...updatedItems, ...updatedAffectedItems]
  scheduleDraftState.suggestions = []
  scheduleDraftState.affectedTaskIds = []
  scheduleDraftState.urgentInsertion = null
}

export async function loadPlannerData() {
  latestPublishAttemptId += 1
  latestUrgentAttemptId += 1
  scheduleDraftState.loading = true
  scheduleDraftState.error = ''
  scheduleDraftState.draftId = ''
  scheduleDraftState.taskPoolItems = [] as TaskPoolItem[]
  scheduleDraftState.scheduledItems = [] as ScheduledItem[]
  scheduleDraftState.affectedTaskIds = [] as string[]
  scheduleDraftState.suggestions = [] as Suggestion[]
  scheduleDraftState.urgentInsertion = null
  scheduleDraftState.urgentError = ''
  scheduleDraftState.urgentLoading = false
  scheduleDraftState.publishResult = null
  scheduleDraftState.publishError = ''
  scheduleDraftState.publishLoading = false

  try {
    const [taskPoolItems, scheduleDraft] = await Promise.all([
      plannerApi.listTaskPool(),
      plannerApi.loadScheduleDraft()
    ])

    scheduleDraftState.draftId = scheduleDraft.draftId
    scheduleDraftState.taskPoolItems = taskPoolItems
    scheduleDraftState.scheduledItems = scheduleDraft.items
  } catch (error) {
    scheduleDraftState.error = error instanceof Error ? error.message : '加载排程数据失败'
  } finally {
    scheduleDraftState.loading = false
  }
}

export async function loadUrgentSuggestions(request?: Parameters<typeof plannerApi.replanUrgent>[0]) {
  scheduleDraftState.urgentLoading = true
  scheduleDraftState.urgentError = ''
  scheduleDraftState.affectedTaskIds = []
  scheduleDraftState.suggestions = []
  scheduleDraftState.urgentInsertion = null
  latestUrgentAttemptId += 1
  const urgentAttemptId = latestUrgentAttemptId

  try {
    const fallbackUrgentItem = scheduleDraftState.scheduledItems[0]
    const replanRequest = request ?? {
      urgentTaskId: '',
      urgentResourceId: fallbackUrgentItem?.resourceId ?? '',
      urgentStartAt: fallbackUrgentItem?.startAt ?? '',
      urgentEndAt: fallbackUrgentItem?.endAt ?? '',
      items: scheduleDraftState.scheduledItems
    }
    const replanResult = await plannerApi.replanUrgent(replanRequest)

    if (urgentAttemptId !== latestUrgentAttemptId) {
      return
    }

    scheduleDraftState.affectedTaskIds = replanResult.affectedTaskIds
    scheduleDraftState.suggestions = replanResult.suggestions
    scheduleDraftState.urgentInsertion = {
      taskId: replanResult.urgentTaskId,
      resourceId: replanRequest.urgentResourceId,
      startAt: replanRequest.urgentStartAt,
      endAt: replanRequest.urgentEndAt
    }
  } catch (error) {
    if (urgentAttemptId !== latestUrgentAttemptId) {
      return
    }

    scheduleDraftState.urgentError = error instanceof Error ? error.message : '加载急单重排建议失败'
  } finally {
    if (urgentAttemptId === latestUrgentAttemptId) {
      scheduleDraftState.urgentLoading = false
    }
  }
}

export async function publishPlannerDraft() {
  scheduleDraftState.publishLoading = true
  scheduleDraftState.publishError = ''

  const draftId = scheduleDraftState.draftId || scheduleDraftState.publishResult?.draftId || ''
  scheduleDraftState.publishResult = null
  latestPublishAttemptId += 1
  const publishAttemptId = latestPublishAttemptId
  let pendingPublishResult: PublishStatusView | null = null

  try {
    const publishResult = await plannerApi.publishScheduleDraft({
      draftId,
      items: scheduleDraftState.scheduledItems
    })

    if (publishAttemptId !== latestPublishAttemptId) {
      return
    }

    pendingPublishResult = {
      draftId: publishResult.draftId,
      auditId: publishResult.auditId ?? '',
      status: publishResult.status,
      writebackStatus: publishResult.writebackStatus,
      message: 'queued',
      retryable: false,
      attemptCount: 0,
      maxAttempts: 0,
      nextRetryAt: null
    }
    scheduleDraftState.publishResult = pendingPublishResult
    scheduleDraftState.affectedTaskIds = []
    scheduleDraftState.suggestions = []

    if (!publishResult.auditId) {
      return
    }

    const writebackStatus = await plannerApi.loadWritebackStatus(publishResult.auditId)

    if (publishAttemptId !== latestPublishAttemptId) {
      return
    }

    scheduleDraftState.publishResult = writebackStatus
    scheduleDraftState.affectedTaskIds = []
    scheduleDraftState.suggestions = []
  } catch (error) {
    if (publishAttemptId !== latestPublishAttemptId) {
      return
    }

    if (pendingPublishResult) {
      scheduleDraftState.publishResult = pendingPublishResult
    }

    if (error instanceof Error && error.message === 'HTTP 409') {
      scheduleDraftState.publishError = '存在阻塞项，无法回写'
    } else {
      scheduleDraftState.publishError = error instanceof Error ? error.message : '发布排程草稿失败'
    }
  } finally {
    if (publishAttemptId === latestPublishAttemptId) {
      scheduleDraftState.publishLoading = false
    }
  }
}

export async function refreshPublishStatus() {
  const auditId = scheduleDraftState.publishResult?.auditId
  if (!auditId) {
    return
  }

  scheduleDraftState.publishLoading = true
  scheduleDraftState.publishError = ''
  latestPublishAttemptId += 1
  const publishAttemptId = latestPublishAttemptId

  try {
    const writebackStatus = await plannerApi.loadWritebackStatus(auditId)

    if (publishAttemptId !== latestPublishAttemptId) {
      return
    }

    scheduleDraftState.publishResult = writebackStatus
  } catch (error) {
    if (publishAttemptId !== latestPublishAttemptId) {
      return
    }

    scheduleDraftState.publishError = error instanceof Error ? error.message : '加载回写状态失败'
  } finally {
    if (publishAttemptId === latestPublishAttemptId) {
      scheduleDraftState.publishLoading = false
    }
  }
}

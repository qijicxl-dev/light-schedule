import { describe, expect, it, beforeEach, vi } from 'vitest'

const { listTaskPool, loadScheduleDraft, replanUrgent, publishScheduleDraft, loadWritebackStatus, createWorkOrder, updateWorkOrder, deleteWorkOrder, listResources, listRoutes, listRouteSteps, createResource, updateResource, deleteResource } = vi.hoisted(() => ({
  listTaskPool: vi.fn(),
  loadScheduleDraft: vi.fn(),
  replanUrgent: vi.fn(),
  publishScheduleDraft: vi.fn(),
  loadWritebackStatus: vi.fn(),
  createWorkOrder: vi.fn(),
  updateWorkOrder: vi.fn(),
  deleteWorkOrder: vi.fn(),
  listResources: vi.fn(),
  listRoutes: vi.fn(),
  listRouteSteps: vi.fn(),
  createResource: vi.fn(),
  updateResource: vi.fn(),
  deleteResource: vi.fn()
}))

vi.mock(import('@/api/planner'), () => ({
  plannerApi: {
    listTaskPool,
    loadScheduleDraft,
    replanUrgent,
    publishScheduleDraft,
    loadWritebackStatus,
    createWorkOrder,
    updateWorkOrder,
    deleteWorkOrder,
    listResources,
    listRoutes,
    listRouteSteps,
    createResource,
    updateResource,
    deleteResource
  }
}))

import {
  addTaskToSchedule,
  applySuggestionToSchedule,
  loadPlannerData,
  loadUrgentSuggestions,
  publishPlannerDraft,
  refreshPublishStatus,
  resetPublishResult,
  resetUrgentReplanResult,
  scheduleDraftState,
  resetScheduleDraftState
} from '@/stores/scheduleDraft'

describe('scheduleDraft', () => {
  beforeEach(() => {
    resetScheduleDraftState()
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ]
    scheduleDraftState.publishResult = {
      auditId: 'audit-0',
      draftId: 'draft-1',
      status: 'draft_ready',
      writebackStatus: 'idle',
      message: '',
      retryable: false,
      attemptCount: 0,
      maxAttempts: 3,
      nextRetryAt: null
    }
    listTaskPool.mockReset()
    loadScheduleDraft.mockReset()
    replanUrgent.mockReset()
    publishScheduleDraft.mockReset()
    loadWritebackStatus.mockReset()
  })

  it('加载任务池和初排结果到状态中', async () => {
    listTaskPool.mockResolvedValue([
      {
        workOrderCode: 'MO-1001',
        dueAt: '2026-04-25T00:00:00Z',
        urgent: true,
        materialRisk: 'missing',
        readiness: 'ready'
      }
    ])
    loadScheduleDraft.mockResolvedValue({
      items: [
        {
          taskId: 'MO-1001',
          resourceId: 'LINE-A',
          resourceGroupName: '冲压组',
          startAt: '2026-04-24T08:00:00Z',
          endAt: '2026-04-24T10:00:00Z'
        }
      ]
    })

    await loadPlannerData()

    expect(listTaskPool).toHaveBeenCalledTimes(1)
    expect(loadScheduleDraft).toHaveBeenCalledTimes(1)
    expect(replanUrgent).not.toHaveBeenCalled()
    expect(publishScheduleDraft).not.toHaveBeenCalled()
    expect(loadWritebackStatus).not.toHaveBeenCalled()
    expect(scheduleDraftState.taskPoolItems).toEqual([
      {
        workOrderCode: 'MO-1001',
        dueAt: '2026-04-25T00:00:00Z',
        urgent: true,
        materialRisk: 'missing',
        readiness: 'ready'
      }
    ])
    expect(scheduleDraftState.scheduledItems).toEqual([
      {
        taskId: 'MO-1001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ])
    expect(scheduleDraftState.suggestions).toEqual([])
    expect(scheduleDraftState.publishResult).toBeNull()
    expect(scheduleDraftState.loading).toBe(false)
    expect(scheduleDraftState.error).toBe('')
  })

  it('重新加载排程数据时会清空上一轮回写状态', async () => {
    scheduleDraftState.publishResult = null
    scheduleDraftState.publishError = '存在阻塞项，无法回写'
    scheduleDraftState.publishLoading = true
    listTaskPool.mockResolvedValue([
      {
        workOrderCode: 'MO-1001',
        dueAt: '2026-04-25T00:00:00Z',
        urgent: true,
        materialRisk: 'missing',
        readiness: 'ready'
      }
    ])
    loadScheduleDraft.mockResolvedValue({
      draftId: 'draft-2',
      items: [
        {
          taskId: 'MO-1001',
          resourceId: 'LINE-A',
          resourceGroupName: '冲压组',
          startAt: '2026-04-24T08:00:00Z',
          endAt: '2026-04-24T10:00:00Z'
        }
      ]
    })

    await loadPlannerData()

    expect(scheduleDraftState.publishResult).toBeNull()
    expect(scheduleDraftState.publishError).toBe('')
    expect(scheduleDraftState.publishLoading).toBe(false)
  })

  it('重新加载排程数据时会清空上一轮急单插入上下文，避免保留过期画板插入位置', async () => {
    ;(scheduleDraftState as { urgentInsertion: { taskId: string; resourceId: string; startAt: string; endAt: string } | null }).urgentInsertion = {
      taskId: 'WO-URGENT-001',
      resourceId: 'LINE-A',
      startAt: '2026-04-24T08:30:00Z',
      endAt: '2026-04-24T09:00:00Z'
    }
    scheduleDraftState.affectedTaskIds = ['TASK-001']
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ]
    scheduleDraftState.urgentError = '旧的急单建议状态'
    listTaskPool.mockResolvedValue([
      {
        workOrderCode: 'MO-1001',
        dueAt: '2026-04-25T00:00:00Z',
        urgent: true,
        materialRisk: 'missing',
        readiness: 'ready'
      }
    ])
    loadScheduleDraft.mockResolvedValue({
      draftId: 'draft-2',
      items: [
        {
          taskId: 'MO-1001',
          resourceId: 'LINE-A',
          resourceGroupName: '冲压组',
          startAt: '2026-04-24T08:00:00Z',
          endAt: '2026-04-24T10:00:00Z'
        }
      ]
    })

    await loadPlannerData()

    expect((scheduleDraftState as { urgentInsertion: { taskId: string; resourceId: string; startAt: string; endAt: string } | null }).urgentInsertion).toBeNull()
    expect(scheduleDraftState.affectedTaskIds).toEqual([])
    expect(scheduleDraftState.suggestions).toEqual([])
    expect(scheduleDraftState.urgentError).toBe('')
  })

  it('触发急单重排时会带上当前排程项上下文', async () => {
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      },
      {
        taskId: 'TASK-002',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T10:00:00Z',
        endAt: '2026-04-24T11:30:00Z'
      }
    ]
    replanUrgent.mockResolvedValue({
      urgentTaskId: 'WO-1001',
      affectedTaskIds: ['TASK-001'],
      suggestions: [
        {
          action: 'move_next_slot',
          reason: 'line overloaded'
        }
      ]
    })

    await loadUrgentSuggestions({
      urgentTaskId: 'WO-1001',
      urgentResourceId: 'LINE-A',
      urgentStartAt: '2026-04-24T08:00:00Z',
      urgentEndAt: '2026-04-24T08:30:00Z',
      items: scheduleDraftState.scheduledItems
    })

    expect(replanUrgent).toHaveBeenCalledWith({
      urgentTaskId: 'WO-1001',
      urgentResourceId: 'LINE-A',
      urgentStartAt: '2026-04-24T08:00:00Z',
      urgentEndAt: '2026-04-24T08:30:00Z',
      items: [
        {
          taskId: 'TASK-001',
          resourceId: 'LINE-A',
          resourceGroupName: '冲压组',
          startAt: '2026-04-24T08:00:00Z',
          endAt: '2026-04-24T10:00:00Z'
        },
        {
          taskId: 'TASK-002',
          resourceId: 'LINE-A',
          resourceGroupName: '冲压组',
          startAt: '2026-04-24T10:00:00Z',
          endAt: '2026-04-24T11:30:00Z'
        }
      ]
    })
    expect(scheduleDraftState.suggestions).toEqual([
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ])
  })

  it('开始新一轮急单重排时会先清空上一轮急单插入上下文，避免保留过期插入位置', async () => {
    ;(scheduleDraftState as { urgentInsertion: { taskId: string; resourceId: string; startAt: string; endAt: string } | null }).urgentInsertion = {
      taskId: 'WO-URGENT-001',
      resourceId: 'LINE-A',
      startAt: '2026-04-24T08:30:00Z',
      endAt: '2026-04-24T09:00:00Z'
    }
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ]
    replanUrgent.mockImplementation(
      () =>
        new Promise(() => {
          // keep pending so we can inspect the immediate reset state at request start
        })
    )

    void loadUrgentSuggestions({
      urgentTaskId: 'WO-1002',
      urgentResourceId: 'LINE-A',
      urgentStartAt: '2026-04-24T10:00:00Z',
      urgentEndAt: '2026-04-24T10:30:00Z',
      items: scheduleDraftState.scheduledItems
    })

    expect((scheduleDraftState as { urgentInsertion: { taskId: string; resourceId: string; startAt: string; endAt: string } | null }).urgentInsertion).toBeNull()
    expect(scheduleDraftState.urgentLoading).toBe(true)
  })

  it('重置急单重排结果时会清空上一轮建议和错误', () => {
    scheduleDraftState.affectedTaskIds = ['TASK-001', 'WO-URGENT-001']
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ]
    scheduleDraftState.urgentError = '加载急单重排建议失败'
    scheduleDraftState.urgentLoading = true

    resetUrgentReplanResult()

    expect(scheduleDraftState.affectedTaskIds).toEqual([])
    expect(scheduleDraftState.suggestions).toEqual([])
    expect(scheduleDraftState.urgentError).toBe('')
    expect(scheduleDraftState.urgentLoading).toBe(false)
  })

  it('重置回写结果时会清空上一轮结果和错误', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'pending',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    }
    scheduleDraftState.publishError = '存在阻塞项，无法回写'
    scheduleDraftState.publishLoading = true

    resetPublishResult()

    expect(scheduleDraftState.publishResult).toBeNull()
    expect(scheduleDraftState.publishError).toBe('')
    expect(scheduleDraftState.publishLoading).toBe(false)
  })

  it('加入排程时会把任务从任务池移入排程列表并顺延到当前资源末尾', () => {
    scheduleDraftState.taskPoolItems = [
      {
        workOrderCode: 'MO-1001',
        dueAt: '2026-04-25T00:00:00Z',
        urgent: true,
        materialRisk: 'missing',
        readiness: 'ready'
      }
    ]
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ]

    addTaskToSchedule(scheduleDraftState.taskPoolItems[0])

    expect(scheduleDraftState.taskPoolItems).toEqual([])
    expect(scheduleDraftState.scheduledItems).toEqual([
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      },
      {
        taskId: 'MO-1001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T10:00:00Z',
        endAt: '2026-04-24T12:00:00Z'
      }
    ])
  })

  it('应用风险建议时会把受影响任务顺延到当前资源末尾', () => {
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ]
    scheduleDraftState.affectedTaskIds = ['TASK-001', 'TASK-002']
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-KEEP',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T06:00:00Z',
        endAt: '2026-04-24T08:00:00Z'
      },
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T05:00:00Z',
        endAt: '2026-04-24T06:00:00Z'
      },
      {
        taskId: 'TASK-002',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T04:00:00Z',
        endAt: '2026-04-24T05:30:00Z'
      }
    ]

    applySuggestionToSchedule(scheduleDraftState.suggestions[0])

    expect(scheduleDraftState.suggestions).toEqual([])
    expect(scheduleDraftState.affectedTaskIds).toEqual([])
    expect(scheduleDraftState.scheduledItems).toEqual([
      {
        taskId: 'TASK-KEEP',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T06:00:00Z',
        endAt: '2026-04-24T08:00:00Z'
      },
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T09:00:00Z'
      },
      {
        taskId: 'TASK-002',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T09:00:00Z',
        endAt: '2026-04-24T10:30:00Z'
      }
    ])
  })

  it('应用风险建议后会清空旧的回写结果状态，避免误用过期结果版本', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    }
    scheduleDraftState.publishError = '旧的回写状态'
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ]
    scheduleDraftState.affectedTaskIds = ['TASK-001']
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-KEEP',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T06:00:00Z',
        endAt: '2026-04-24T08:00:00Z'
      },
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T05:00:00Z',
        endAt: '2026-04-24T06:00:00Z'
      }
    ]

    applySuggestionToSchedule(scheduleDraftState.suggestions[0])

    expect(scheduleDraftState.publishResult).toBeNull()
    expect(scheduleDraftState.publishError).toBe('')
  })

  it('应用风险建议后会清空旧的急单错误状态，避免保留过期建议错误', () => {
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ]
    scheduleDraftState.affectedTaskIds = ['TASK-001']
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-KEEP',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T06:00:00Z',
        endAt: '2026-04-24T08:00:00Z'
      },
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T05:00:00Z',
        endAt: '2026-04-24T06:00:00Z'
      }
    ]
    scheduleDraftState.urgentError = '旧的急单建议错误'

    applySuggestionToSchedule(scheduleDraftState.suggestions[0])

    expect(scheduleDraftState.urgentError).toBe('')
  })

  it('应用急单重排建议时会把急单插入画板并顺延受影响任务', async () => {
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-KEEP',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T06:00:00Z',
        endAt: '2026-04-24T08:00:00Z'
      },
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ]
    replanUrgent.mockResolvedValue({
      urgentTaskId: 'WO-URGENT-001',
      affectedTaskIds: ['TASK-001'],
      suggestions: [
        {
          action: 'move_next_slot',
          reason: '急单优先'
        }
      ]
    })

    await loadUrgentSuggestions({
      urgentTaskId: 'WO-URGENT-001',
      urgentResourceId: 'LINE-A',
      urgentStartAt: '2026-04-24T08:00:00Z',
      urgentEndAt: '2026-04-24T09:00:00Z',
      items: scheduleDraftState.scheduledItems
    })

    applySuggestionToSchedule(scheduleDraftState.suggestions[0])

    expect(scheduleDraftState.suggestions).toEqual([])
    expect(scheduleDraftState.affectedTaskIds).toEqual([])
    expect(scheduleDraftState.scheduledItems).toEqual([
      {
        taskId: 'TASK-KEEP',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T06:00:00Z',
        endAt: '2026-04-24T08:00:00Z'
      },
      {
        taskId: 'WO-URGENT-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T09:00:00Z'
      },
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T09:00:00Z',
        endAt: '2026-04-24T11:00:00Z'
      }
    ])
  })

  it('加入排程时在没有已排任务上下文时会回退到演示默认值', () => {
    scheduleDraftState.taskPoolItems = [
      {
        workOrderCode: 'MO-1002',
        dueAt: '2026-04-25T09:30:00Z',
        urgent: false,
        materialRisk: 'low',
        readiness: 'ready'
      }
    ]
    scheduleDraftState.scheduledItems = []

    addTaskToSchedule(scheduleDraftState.taskPoolItems[0])

    expect(scheduleDraftState.taskPoolItems).toEqual([])
    expect(scheduleDraftState.scheduledItems).toEqual([
      {
        taskId: 'MO-1002',
        resourceId: 'LINE-A',
        resourceGroupName: '默认资源组',
        startAt: '2026-04-25T09:30:00Z',
        endAt: '2026-04-25T11:30:00Z'
      }
    ])
  })

  it('重复加入已存在的任务时不会生成重复排程项', () => {
    scheduleDraftState.taskPoolItems = [
      {
        workOrderCode: 'MO-1001',
        dueAt: '2026-04-25T00:00:00Z',
        urgent: true,
        materialRisk: 'missing',
        readiness: 'ready'
      }
    ]
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'MO-1001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ]

    addTaskToSchedule(scheduleDraftState.taskPoolItems[0])

    expect(scheduleDraftState.taskPoolItems).toHaveLength(1)
    expect(scheduleDraftState.scheduledItems).toEqual([
      {
        taskId: 'MO-1001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ])
  })

  it('加入排程后会清空旧的回写结果状态，避免误用过期结果版本', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    }
    scheduleDraftState.publishError = '旧的回写状态'
    scheduleDraftState.taskPoolItems = [
      {
        workOrderCode: 'MO-1002',
        dueAt: '2026-04-25T00:00:00Z',
        urgent: false,
        materialRisk: 'low',
        readiness: 'ready'
      }
    ]
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ]

    addTaskToSchedule(scheduleDraftState.taskPoolItems[0])

    expect(scheduleDraftState.publishResult).toBeNull()
    expect(scheduleDraftState.publishError).toBe('')
  })

  it('加入排程后会清空上一轮急单建议状态，避免保留过期风险建议', () => {
    scheduleDraftState.taskPoolItems = [
      {
        workOrderCode: 'MO-1002',
        dueAt: '2026-04-25T00:00:00Z',
        urgent: false,
        materialRisk: 'low',
        readiness: 'ready'
      }
    ]
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ]
    scheduleDraftState.affectedTaskIds = ['TASK-001', 'WO-URGENT-001']
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ]
    ;(scheduleDraftState as any).urgentInsertion = {
      taskId: 'WO-URGENT-001',
      resourceId: 'LINE-A',
      startAt: '2026-04-24T08:30:00Z',
      endAt: '2026-04-24T09:00:00Z'
    }
    scheduleDraftState.urgentError = '旧的急单建议状态'

    addTaskToSchedule(scheduleDraftState.taskPoolItems[0])

    expect(scheduleDraftState.affectedTaskIds).toEqual([])
    expect(scheduleDraftState.suggestions).toEqual([])
    expect((scheduleDraftState as any).urgentInsertion).toBeNull()
    expect(scheduleDraftState.urgentError).toBe('')
  })

  it('触发回写成功后会按 auditId 刷新后端回写状态真值', async () => {
    scheduleDraftState.affectedTaskIds = ['TASK-001', 'WO-URGENT-001']
    publishScheduleDraft.mockResolvedValue({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending'
    })
    loadWritebackStatus.mockResolvedValue({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })

    await publishPlannerDraft()

    expect(loadWritebackStatus).toHaveBeenCalledWith('audit-1')
    expect(scheduleDraftState.publishResult).toEqual({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.affectedTaskIds).toEqual([])
  })

  it('回写返回待处理但没有 auditId 时保留最小结果而不继续查状态', async () => {
    scheduleDraftState.affectedTaskIds = ['TASK-001']
    publishScheduleDraft.mockResolvedValue({
      draftId: 'draft-1',
      auditId: null,
      status: 'validated',
      writebackStatus: 'pending'
    })

    await publishPlannerDraft()

    expect(loadWritebackStatus).not.toHaveBeenCalled()
    expect(scheduleDraftState.publishResult).toEqual({
      draftId: 'draft-1',
      auditId: '',
      status: 'validated',
      writebackStatus: 'pending',
      message: 'queued',
      retryable: false,
      attemptCount: 0,
      maxAttempts: 0,
      nextRetryAt: null
    })
    expect(scheduleDraftState.affectedTaskIds).toEqual([])
    expect(scheduleDraftState.publishError).toBe('')
  })

  it('首次加载回写状态失败时仍保留 auditId 供后续继续刷新', async () => {
    scheduleDraftState.affectedTaskIds = ['TASK-001']
    publishScheduleDraft.mockResolvedValue({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending'
    })
    loadWritebackStatus.mockRejectedValue(new Error('加载回写状态失败'))

    await publishPlannerDraft()

    expect(scheduleDraftState.publishResult).toEqual({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending',
      message: 'queued',
      retryable: false,
      attemptCount: 0,
      maxAttempts: 0,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('加载回写状态失败')
    expect(scheduleDraftState.publishLoading).toBe(false)
    expect(scheduleDraftState.affectedTaskIds).toEqual([])
  })

  it('发布后的旧状态查询结果不会覆盖后续手动刷新拿到的新状态', async () => {
    let resolvePublish: (value: {
      draftId: string
      auditId: string
      status: string
      writebackStatus: string
    }) => void = (_value): void => {
      throw new Error('publish promise was not initialized')
    }
    let resolveInitialStatus: (value: {
      auditId: string
      draftId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: null
    }) => void = (_value): void => {
      throw new Error('initial status promise was not initialized')
    }
    let resolveRefreshStatus: (value: {
      auditId: string
      draftId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: null
    }) => void = (_value): void => {
      throw new Error('refresh status promise was not initialized')
    }

    publishScheduleDraft.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolvePublish = resolve
        })
    )
    loadWritebackStatus
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveInitialStatus = resolve
          })
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveRefreshStatus = resolve
          })
      )

    const publishPromise = publishPlannerDraft()

    resolvePublish({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending'
    })
    await Promise.resolve()

    expect(scheduleDraftState.publishResult).toEqual({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending',
      message: 'queued',
      retryable: false,
      attemptCount: 0,
      maxAttempts: 0,
      nextRetryAt: null
    })

    const refreshPromise = refreshPublishStatus()

    resolveRefreshStatus({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    await refreshPromise

    expect(scheduleDraftState.publishResult).toEqual({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('')

    resolveInitialStatus({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    await publishPromise

    expect(scheduleDraftState.publishResult).toEqual({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('')
    expect(scheduleDraftState.publishLoading).toBe(false)
  })

  it('再次发布后旧 auditId 的迟到状态结果不会污染新一轮发布结果', async () => {
    let resolveFirstPublish: (value: {
      draftId: string
      auditId: string
      status: string
      writebackStatus: string
    }) => void = (_value): void => {
      throw new Error('first publish promise was not initialized')
    }
    let resolveSecondPublish: (value: {
      draftId: string
      auditId: string
      status: string
      writebackStatus: string
    }) => void = (_value): void => {
      throw new Error('second publish promise was not initialized')
    }
    let resolveFirstStatus: (value: {
      auditId: string
      draftId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: null
    }) => void = (_value): void => {
      throw new Error('first status promise was not initialized')
    }
    let resolveSecondStatus: (value: {
      auditId: string
      draftId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: null
    }) => void = (_value): void => {
      throw new Error('second status promise was not initialized')
    }

    publishScheduleDraft
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirstPublish = resolve
          })
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecondPublish = resolve
          })
      )
    loadWritebackStatus
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirstStatus = resolve
          })
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecondStatus = resolve
          })
      )

    const firstPublishPromise = publishPlannerDraft()

    resolveFirstPublish({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending'
    })
    await Promise.resolve()

    const secondPublishPromise = publishPlannerDraft()

    resolveSecondPublish({
      draftId: 'draft-2',
      auditId: 'audit-2',
      status: 'validated',
      writebackStatus: 'pending'
    })
    await Promise.resolve()

    resolveSecondStatus({
      auditId: 'audit-2',
      draftId: 'draft-2',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    await secondPublishPromise

    expect(scheduleDraftState.publishResult).toEqual({
      auditId: 'audit-2',
      draftId: 'draft-2',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })

    resolveFirstStatus({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    await firstPublishPromise

    expect(scheduleDraftState.publishResult).toEqual({
      auditId: 'audit-2',
      draftId: 'draft-2',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('')
    expect(scheduleDraftState.publishLoading).toBe(false)
  })

  it('按 auditId 刷新回写状态时会覆盖本地旧的 pending 结果', async () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'pending',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    }
    loadWritebackStatus.mockResolvedValue({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })

    await refreshPublishStatus()

    expect(loadWritebackStatus).toHaveBeenCalledWith('audit-1')
    expect(scheduleDraftState.publishResult).toEqual({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('')
    expect(scheduleDraftState.publishLoading).toBe(false)
  })

  it('按 auditId 刷新回写状态失败时会保留上一轮结果并写入错误', async () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    }
    loadWritebackStatus.mockRejectedValue(new Error('加载回写状态失败'))

    await refreshPublishStatus()

    expect(loadWritebackStatus).toHaveBeenCalledWith('audit-1')
    expect(scheduleDraftState.publishResult).toEqual({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('加载回写状态失败')
    expect(scheduleDraftState.publishLoading).toBe(false)
  })

  it('旧的刷新成功结果不会覆盖后一次刷新状态', async () => {
    let resolveFirstRefresh: (value: {
      auditId: string
      draftId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: null
    }) => void = (_value): void => {
      throw new Error('first refresh promise was not initialized')
    }
    let resolveSecondRefresh: (value: {
      auditId: string
      draftId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: null
    }) => void = (_value): void => {
      throw new Error('second refresh promise was not initialized')
    }

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'pending',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    }

    loadWritebackStatus
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirstRefresh = resolve
          })
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecondRefresh = resolve
          })
      )

    const firstRefreshPromise = refreshPublishStatus()
    const secondRefreshPromise = refreshPublishStatus()

    resolveSecondRefresh({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    await secondRefreshPromise

    expect(scheduleDraftState.publishResult).toEqual({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('')

    resolveFirstRefresh({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    await firstRefreshPromise

    expect(scheduleDraftState.publishResult).toEqual({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('')
    expect(scheduleDraftState.publishLoading).toBe(false)
  })

  it('旧的刷新失败结果不会覆盖后一次刷新成功状态', async () => {
    let rejectFirstRefresh: (reason?: unknown) => void = (_reason?: unknown): void => {
      throw new Error('first refresh promise was not initialized')
    }
    let resolveSecondRefresh: (value: {
      auditId: string
      draftId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: null
    }) => void = (_value): void => {
      throw new Error('second refresh promise was not initialized')
    }

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'pending',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    }

    loadWritebackStatus
      .mockImplementationOnce(
        () =>
          new Promise((_, reject) => {
            rejectFirstRefresh = reject
          })
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecondRefresh = resolve
          })
      )

    const firstRefreshPromise = refreshPublishStatus()
    const secondRefreshPromise = refreshPublishStatus()

    resolveSecondRefresh({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    await secondRefreshPromise

    expect(scheduleDraftState.publishResult).toEqual({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('')

    rejectFirstRefresh(new Error('加载回写状态失败'))
    await firstRefreshPromise

    expect(scheduleDraftState.publishResult).toEqual({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('')
    expect(scheduleDraftState.publishLoading).toBe(false)
  })

  it('加载失败时写入错误信息', async () => {
    const error = new Error('planner api failed')
    listTaskPool.mockRejectedValue(error)

    await loadPlannerData()

    expect(scheduleDraftState.error).toBe('planner api failed')
    expect(scheduleDraftState.loading).toBe(false)
    expect(scheduleDraftState.taskPoolItems).toEqual([])
    expect(scheduleDraftState.scheduledItems).toEqual([])
    expect(scheduleDraftState.suggestions).toEqual([])
    expect(scheduleDraftState.publishResult).toBeNull()
  })

  it('急单重排失败时只写入急单错误状态', async () => {
    ;(scheduleDraftState as any).publishError = '已有回写错误'
    replanUrgent.mockRejectedValue(new Error('加载急单重排建议失败'))

    await loadUrgentSuggestions()

    expect((scheduleDraftState as any).urgentError).toBe('加载急单重排建议失败')
    expect((scheduleDraftState as any).urgentLoading).toBe(false)
    expect((scheduleDraftState as any).publishError).toBe('已有回写错误')
    expect((scheduleDraftState as any).publishLoading).toBe(false)
  })

  it('回写失败时只写入回写错误状态', async () => {
    ;(scheduleDraftState as any).urgentError = '已有急单错误'
    publishScheduleDraft.mockRejectedValue(new Error('发布排程草稿失败'))

    await publishPlannerDraft()

    expect((scheduleDraftState as any).publishError).toBe('发布排程草稿失败')
    expect((scheduleDraftState as any).publishLoading).toBe(false)
    expect((scheduleDraftState as any).urgentError).toBe('已有急单错误')
    expect((scheduleDraftState as any).urgentLoading).toBe(false)
  })

  it('回写阻塞时写入可读错误信息', async () => {
    publishScheduleDraft.mockRejectedValue(new Error('HTTP 409'))

    await publishPlannerDraft()

    expect((scheduleDraftState as any).publishError).toBe('存在阻塞项，无法回写')
    expect((scheduleDraftState as any).publishLoading).toBe(false)
  })

  it('旧的非阻塞失败结果不会覆盖新一次成功回写状态', async () => {
    let resolveFirstPublish: (reason?: unknown) => void = (_reason?: unknown): void => {
      throw new Error('first publish promise was not initialized')
    }
    let resolveSecondPublish: (value: {
      draftId: string
      auditId: string
      status: string
      writebackStatus: string
    }) => void = (_value): void => {
      throw new Error('second publish promise was not initialized')
    }

    publishScheduleDraft
      .mockImplementationOnce(
        () =>
          new Promise((_, reject) => {
            resolveFirstPublish = reject
          })
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecondPublish = resolve
          })
      )

    loadWritebackStatus.mockResolvedValueOnce({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })

    const firstPublishPromise = publishPlannerDraft()
    const secondPublishPromise = publishPlannerDraft()

    resolveSecondPublish({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending'
    })
    await secondPublishPromise

    expect(scheduleDraftState.publishResult).toEqual({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('')

    resolveFirstPublish(new Error('发布排程草稿失败'))
    await firstPublishPromise

    expect(scheduleDraftState.publishResult).toEqual({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })
    expect(scheduleDraftState.publishError).toBe('')
    expect(scheduleDraftState.publishLoading).toBe(false)
  })

  it('重置回写结果后，旧的发布成功结果不会再回流到当前画板', async () => {
    let resolvePublish: (value: {
      draftId: string
      auditId: string
      status: string
      writebackStatus: string
    }) => void = (_value): void => {
      throw new Error('publish promise was not initialized')
    }

    publishScheduleDraft.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolvePublish = resolve
        })
    )
    loadWritebackStatus.mockResolvedValueOnce({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })

    const publishPromise = publishPlannerDraft()

    resetPublishResult()

    resolvePublish({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending'
    })
    await publishPromise

    expect(scheduleDraftState.publishResult).toBeNull()
    expect(scheduleDraftState.publishError).toBe('')
    expect(scheduleDraftState.publishLoading).toBe(false)
  })

  it('旧的急单成功结果不会覆盖新一次失败状态', async () => {
    let resolveFirstUrgent: (value: {
      urgentTaskId: string
      affectedTaskIds: string[]
      suggestions: Array<{
        action: string
        reason: string
      }>
    }) => void = (_value): void => {
      throw new Error('first urgent promise was not initialized')
    }
    let resolveSecondUrgent: (reason?: unknown) => void = (_reason?: unknown): void => {
      throw new Error('second urgent promise was not initialized')
    }

    replanUrgent
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirstUrgent = resolve
          })
      )
      .mockImplementationOnce(
        () =>
          new Promise((_, reject) => {
            resolveSecondUrgent = reject
          })
      )

    const firstUrgentPromise = loadUrgentSuggestions({
      urgentTaskId: 'WO-1001',
      urgentResourceId: 'LINE-A',
      urgentStartAt: '2026-04-24T08:00:00Z',
      urgentEndAt: '2026-04-24T08:30:00Z',
      items: scheduleDraftState.scheduledItems
    })
    const secondUrgentPromise = loadUrgentSuggestions({
      urgentTaskId: 'WO-1002',
      urgentResourceId: 'LINE-A',
      urgentStartAt: '2026-04-24T09:00:00Z',
      urgentEndAt: '2026-04-24T09:30:00Z',
      items: scheduleDraftState.scheduledItems
    })

    resolveSecondUrgent(new Error('加载急单重排建议失败'))
    await secondUrgentPromise

    expect(scheduleDraftState.suggestions).toEqual([])
    expect(scheduleDraftState.affectedTaskIds).toEqual([])
    expect(scheduleDraftState.urgentError).toBe('加载急单重排建议失败')

    resolveFirstUrgent({
      urgentTaskId: 'WO-1001',
      affectedTaskIds: ['TASK-001'],
      suggestions: [
        {
          action: 'move_next_slot',
          reason: 'line overloaded'
        }
      ]
    })
    await firstUrgentPromise

    expect(scheduleDraftState.suggestions).toEqual([])
    expect(scheduleDraftState.affectedTaskIds).toEqual([])
    expect(scheduleDraftState.urgentError).toBe('加载急单重排建议失败')
    expect(scheduleDraftState.urgentLoading).toBe(false)
  })
})

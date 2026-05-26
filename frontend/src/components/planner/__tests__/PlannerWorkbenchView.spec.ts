import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import TaskPoolPanel from '@/components/planner/TaskPoolPanel.vue'
import ScheduleBoard from '@/components/planner/ScheduleBoard.vue'
import RiskSidePanel from '@/components/planner/RiskSidePanel.vue'

const defaultTaskPoolItems = () => [
  {
    workOrderCode: 'MO-1001',
    dueAt: '2026-04-25T00:00:00Z',
    urgent: true,
    materialRisk: 'missing',
    readiness: 'ready'
  }
]

const defaultScheduledItems = () => [
  {
    taskId: 'MO-1001',
    resourceId: 'LINE-A',
    resourceGroupName: '冲压组',
    startAt: '2026-04-24T08:00:00Z',
    endAt: '2026-04-24T10:00:00Z'
  }
]

const defaultSuggestions = () => [
  {
    action: 'move_next_slot',
    reason: 'line overloaded'
  }
]

const defaultPublishResult = () => ({
  draftId: 'draft-1',
  status: 'validated',
  writebackStatus: 'pending'
})

const {
  addTaskToSchedule,
  applySuggestionToSchedule,
  loadPlannerData,
  loadUrgentSuggestions,
  publishPlannerDraft,
  refreshPublishStatus,
  resetPublishResult,
  resetUrgentReplanResult,
  scheduleDraftState
} = vi.hoisted(() => ({
  addTaskToSchedule: vi.fn((task) => {
    const exists = scheduleDraftState.scheduledItems.some((item) => item.taskId === task.workOrderCode)
    if (exists) {
      return
    }

    scheduleDraftState.publishResult = null
    scheduleDraftState.publishError = ''
    scheduleDraftState.publishLoading = false
    scheduleDraftState.affectedTaskIds = []
    scheduleDraftState.suggestions = []
    ;(scheduleDraftState as any).urgentInsertion = null
    scheduleDraftState.urgentError = ''
    scheduleDraftState.urgentLoading = false
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
        endAt
      }
    ]
  }),
  applySuggestionToSchedule: vi.fn((suggestion) => {
    const exists = scheduleDraftState.suggestions.some(
      (item) => item.action === suggestion.action && item.reason === suggestion.reason
    )
    if (!exists) {
      return
    }

    scheduleDraftState.publishResult = null
    scheduleDraftState.publishError = ''
    scheduleDraftState.publishLoading = false
    scheduleDraftState.urgentError = ''
    const affectedItems = scheduleDraftState.affectedTaskIds
      .map((taskId) => scheduleDraftState.scheduledItems.find((item) => item.taskId === taskId))
      .filter((item): item is (typeof scheduleDraftState.scheduledItems)[number] => Boolean(item))
    const urgentInsertion = (scheduleDraftState as any).urgentInsertion ?? null

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

    const updatedItems: Array<(typeof scheduleDraftState.scheduledItems)[number]> = []

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
    ;(scheduleDraftState as any).urgentInsertion = null
  }),
  loadPlannerData: vi.fn(),
  loadUrgentSuggestions: vi.fn(),
  publishPlannerDraft: vi.fn(),
  refreshPublishStatus: vi.fn(),
  resetPublishResult: vi.fn(() => {
    scheduleDraftState.publishResult = null
    scheduleDraftState.publishError = ''
    scheduleDraftState.publishLoading = false
  }),
  resetUrgentReplanResult: vi.fn(() => {
    scheduleDraftState.affectedTaskIds = []
    scheduleDraftState.suggestions = []
    ;(scheduleDraftState as any).urgentInsertion = null
    scheduleDraftState.urgentError = ''
    scheduleDraftState.urgentLoading = false
  }),
  scheduleDraftState: {
    loading: false,
    draftId: 'draft-1',
    taskPoolItems: [
      {
        workOrderCode: 'MO-1001',
        dueAt: '2026-04-25T00:00:00Z',
        urgent: true,
        materialRisk: 'missing',
        readiness: 'ready'
      }
    ],
    scheduledItems: [
      {
        taskId: 'MO-1001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ],
    affectedTaskIds: [] as string[],
    suggestions: [
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ],
    publishResult: {
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'pending'
    } as { draftId: string; status: string; writebackStatus: string } | null,
    error: '',
    urgentLoading: false,
    urgentError: '',
    publishLoading: false,
    publishError: ''
  }
}))

vi.mock('@/stores/scheduleDraft', () => ({
  scheduleDraftState,
  addTaskToSchedule,
  applySuggestionToSchedule,
  loadPlannerData,
  loadUrgentSuggestions,
  publishPlannerDraft,
  refreshPublishStatus,
  resetPublishResult,
  resetUrgentReplanResult
}))

import PlannerWorkbenchView from '@/views/PlannerWorkbenchView.vue'

describe('PlannerWorkbenchView', () => {
  beforeEach(() => {
    scheduleDraftState.loading = false
    scheduleDraftState.taskPoolItems = defaultTaskPoolItems()
    scheduleDraftState.scheduledItems = defaultScheduledItems()
    scheduleDraftState.affectedTaskIds = []
    scheduleDraftState.suggestions = defaultSuggestions()
    scheduleDraftState.publishResult = defaultPublishResult()
    scheduleDraftState.error = ''
    scheduleDraftState.urgentLoading = false
    scheduleDraftState.urgentError = ''
    scheduleDraftState.publishLoading = false
    scheduleDraftState.publishError = ''
    addTaskToSchedule.mockClear()
    loadPlannerData.mockReset()
    loadPlannerData.mockResolvedValue(undefined)
    loadUrgentSuggestions.mockReset()
    loadUrgentSuggestions.mockResolvedValue(undefined)
    publishPlannerDraft.mockReset()
    publishPlannerDraft.mockResolvedValue(undefined)
    refreshPublishStatus.mockReset()
    refreshPublishStatus.mockResolvedValue(undefined)
    resetPublishResult.mockClear()
    resetUrgentReplanResult.mockClear()
  })

  it('页面加载时会拉取排程基础数据', async () => {
    mount(PlannerWorkbenchView)
    await flushPromises()

    expect(loadPlannerData).toHaveBeenCalledTimes(1)
    expect(loadUrgentSuggestions).not.toHaveBeenCalled()
    expect(publishPlannerDraft).not.toHaveBeenCalled()
  })

  it('基础加载中时显示骨架屏', () => {
    scheduleDraftState.loading = true
    scheduleDraftState.taskPoolItems = []
    scheduleDraftState.scheduledItems = []
    scheduleDraftState.suggestions = []

    const wrapper = mount(PlannerWorkbenchView)

    expect(wrapper.find('[data-testid="planner-skeleton"]').exists()).toBe(true)
    expect(wrapper.find('.planner-skeleton').exists()).toBe(true)
  })

  it('基础加载失败时显示错误信息', () => {
    scheduleDraftState.error = '加载排程数据失败'
    scheduleDraftState.taskPoolItems = []
    scheduleDraftState.scheduledItems = []
    scheduleDraftState.suggestions = []

    const wrapper = mount(PlannerWorkbenchView)

    expect(wrapper.text()).toContain('加载排程数据失败')
  })

  it('基础数据为空时显示空状态提示', async () => {
    scheduleDraftState.taskPoolItems = []
    scheduleDraftState.scheduledItems = []
    scheduleDraftState.suggestions = []

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无排程数据')
  })

  it('点击插入急单时先打开表单而不立即触发重排', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    const urgentButton = wrapper.get('[data-testid="open-urgent-order"]')
    await urgentButton.trigger('click')

    expect(resetUrgentReplanResult).toHaveBeenCalledTimes(1)
    expect(loadUrgentSuggestions).not.toHaveBeenCalled()
    expect(wrapper.find('[aria-label="急单插入"]').exists()).toBe(true)
  })

  it('重新打开急单弹窗时会清空上一轮急单结果', async () => {
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ]
    scheduleDraftState.urgentError = '加载急单重排建议失败'
    ;(scheduleDraftState as any).affectedTaskIds = ['TASK-001', 'WO-URGENT-001']

    const wrapper = mount(PlannerWorkbenchView)
    const urgentButton = wrapper.get('[data-testid="open-urgent-order"]')
    await urgentButton.trigger('click')

    const urgentDialog = wrapper.get('[aria-label="急单插入"]')
    expect(urgentDialog.text()).toContain('暂无重排建议')
    expect(urgentDialog.text()).not.toContain('move_next_slot')
    expect(urgentDialog.text()).not.toContain('line overloaded')
    expect(urgentDialog.text()).not.toContain('加载急单重排建议失败')
    expect(urgentDialog.text()).not.toContain('WO-URGENT-001')
  })

  it('重新打开急单弹窗时会清空 mock store 里的旧 urgentInsertion', async () => {
    ;(scheduleDraftState as any).urgentInsertion = {
      taskId: 'WO-URGENT-001',
      resourceId: 'LINE-A',
      startAt: '2026-04-24T08:30:00Z',
      endAt: '2026-04-24T09:00:00Z'
    }

    const wrapper = mount(PlannerWorkbenchView)
    const urgentButton = wrapper.get('[data-testid="open-urgent-order"]')
    await urgentButton.trigger('click')

    expect((scheduleDraftState as any).urgentInsertion).toBeNull()
  })

  it('提交急单插单表单时会带上资源与时间窗', async () => {
    scheduleDraftState.taskPoolItems = [
      {
        workOrderCode: 'WO-URGENT-001',
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
      },
      {
        taskId: 'TASK-002',
        resourceId: 'LINE-B',
        resourceGroupName: '装配组',
        startAt: '2026-04-24T10:00:00Z',
        endAt: '2026-04-24T11:30:00Z'
      }
    ]

    const wrapper = mount(PlannerWorkbenchView)
    const urgentButton = wrapper.get('[data-testid="open-urgent-order"]')
    await urgentButton.trigger('click')
    await wrapper.get('select').setValue('LINE-B')
    await wrapper.get('input[aria-label="插入开始时间"]').setValue('2026-04-24T09:00:00Z')
    await wrapper.get('input[aria-label="插入结束时间"]').setValue('2026-04-24T09:45:00Z')
    await wrapper.get('button[data-testid="submit-urgent-replan"]').trigger('click')
    await flushPromises()

    expect(loadUrgentSuggestions).toHaveBeenCalledWith({
      urgentTaskId: 'WO-URGENT-001',
      urgentResourceId: 'LINE-B',
      urgentStartAt: '2026-04-24T09:00:00Z',
      urgentEndAt: '2026-04-24T09:45:00Z',
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
          resourceId: 'LINE-B',
          resourceGroupName: '装配组',
          startAt: '2026-04-24T10:00:00Z',
          endAt: '2026-04-24T11:30:00Z'
        }
      ]
    })
  })

  it('默认打开待排任务页签并可切换到排程画板', async () => {
    const wrapper = mount(PlannerWorkbenchView)

    const tabs = wrapper.get('[role="tablist"]').findAll('[role="tab"]')
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(tabs[0].text()).toContain('待排任务')
    expect(tabs[0].text()).toContain('1')
    expect(tabs[1].attributes('aria-selected')).toBe('false')
    expect(wrapper.findComponent(TaskPoolPanel).exists()).toBe(true)
    expect(wrapper.findComponent(ScheduleBoard).exists()).toBe(false)
    expect(wrapper.findComponent(RiskSidePanel).exists()).toBe(false)

    await tabs[1].trigger('click')

    expect(tabs[0].attributes('aria-selected')).toBe('false')
    expect(tabs[1].attributes('aria-selected')).toBe('true')
    expect(wrapper.findComponent(TaskPoolPanel).exists()).toBe(false)
    expect(wrapper.findComponent(ScheduleBoard).exists()).toBe(true)
    expect(wrapper.findComponent(RiskSidePanel).exists()).toBe(false)
    expect(wrapper.find('[aria-label="排程数据表格"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('08:00')
    expect(wrapper.text()).toContain('10:00')
  })

  it('切换到风险与建议后仍保留常驻急单与回写入口', async () => {
    const wrapper = mount(PlannerWorkbenchView)

    const tabs = wrapper.get('[role="tablist"]').findAll('[role="tab"]')
    await tabs[2].trigger('click')

    expect(wrapper.findComponent(TaskPoolPanel).exists()).toBe(false)
    expect(wrapper.findComponent(ScheduleBoard).exists()).toBe(false)
    expect(wrapper.findComponent(RiskSidePanel).exists()).toBe(true)
    expect(wrapper.get('[data-testid="open-urgent-order"]')).toBeTruthy()
    expect(wrapper.get('[data-testid="open-publish-dialog"]')).toBeTruthy()
  })

  it('点击插入急单时立即打开弹窗', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    const urgentButton = wrapper.get('[data-testid="open-urgent-order"]')
    await urgentButton.trigger('click')

    expect(loadUrgentSuggestions).not.toHaveBeenCalled()
    expect(wrapper.find('[aria-label="急单插入"]').exists()).toBe(true)
  })

  it('点击加入排程时会调用 store 并更新任务池与画板内容', async () => {
    scheduleDraftState.taskPoolItems = [
      {
        workOrderCode: 'MO-2001',
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

    const wrapper = mount(PlannerWorkbenchView)

    await wrapper.get('[data-testid="add-to-schedule-MO-2001"]').trigger('click')
    await wrapper.get('[role="tab"][aria-controls="planner-panel-scheduleBoard"]').trigger('click')
    await flushPromises()

    expect(addTaskToSchedule).toHaveBeenCalledWith({
      workOrderCode: 'MO-2001',
      dueAt: '2026-04-25T00:00:00Z',
      urgent: false,
      materialRisk: 'low',
      readiness: 'ready'
    })
    expect(wrapper.text()).not.toContain('MO-2001交期：2026-04-25T00:00:00Z')
    expect(wrapper.text()).toContain('MO-2001')
    expect(wrapper.text()).toContain('LINE-A')
  })

  it('排程画板会以表格形式按资源分组展示任务', async () => {
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
        resourceId: 'LINE-B',
        resourceGroupName: '装配组',
        startAt: '2026-04-24T09:00:00Z',
        endAt: '2026-04-24T11:00:00Z'
      }
    ]

    const wrapper = mount(PlannerWorkbenchView)
    await wrapper.get('[role="tab"][aria-controls="planner-panel-scheduleBoard"]').trigger('click')
    await flushPromises()

    const table = wrapper.find('table')
    expect(table.exists()).toBe(true)
    expect(wrapper.find('[data-testid="schedule-row-TASK-001"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="schedule-row-TASK-002"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid^="schedule-row-"]')).toHaveLength(2)
    expect(wrapper.text()).toContain('LINE-A')
    expect(wrapper.text()).toContain('LINE-B')
    expect(wrapper.text()).toContain('TASK-001')
    expect(wrapper.text()).toContain('TASK-002')
  })

  it('排程表格中正确显示每条任务的开始时间和结束时间', async () => {
    scheduleDraftState.scheduledItems = [
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
        endAt: '2026-04-24T11:00:00Z'
      }
    ]

    const wrapper = mount(PlannerWorkbenchView)
    await wrapper.get('[role="tab"][aria-controls="planner-panel-scheduleBoard"]').trigger('click')
    await flushPromises()

    const rows = wrapper.findAll('[data-testid^="schedule-row-"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('08:00')
    expect(rows[0].text()).toContain('09:00')
    expect(rows[1].text()).toContain('09:00')
    expect(rows[1].text()).toContain('11:00')
  })

  it('同一资源下的任务按开始时间升序排列', async () => {
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-LATER',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T10:00:00Z',
        endAt: '2026-04-24T11:00:00Z'
      },
      {
        taskId: 'TASK-EARLIER',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T09:00:00Z'
      }
    ]

    const wrapper = mount(PlannerWorkbenchView)
    await wrapper.get('[role="tab"][aria-controls="planner-panel-scheduleBoard"]').trigger('click')
    await flushPromises()

    const rows = wrapper.findAll('[data-testid^="schedule-row-"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('TASK-EARLIER')
    expect(rows[1].text()).toContain('TASK-LATER')
  })

  it('加入排程时会把新任务顺延到当前资源末尾，而不是复用首条开始时间', async () => {
    scheduleDraftState.taskPoolItems = [
      {
        workOrderCode: 'MO-2001',
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
      },
      {
        taskId: 'TASK-002',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T10:00:00Z',
        endAt: '2026-04-24T11:30:00Z'
      }
    ]

    mount(PlannerWorkbenchView)

    await mount(PlannerWorkbenchView).get('[data-testid="add-to-schedule-MO-2001"]').trigger('click')

    expect(scheduleDraftState.scheduledItems.at(-1)).toEqual({
      taskId: 'MO-2001',
      resourceId: 'LINE-A',
      resourceGroupName: '冲压组',
      startAt: '2026-04-24T11:30:00Z',
      endAt: '2026-04-24T13:30:00Z'
    })
  })

  it('加入排程后会清空 mock store 里的旧回写状态，避免沿用过期结果版本', async () => {
    scheduleDraftState.publishResult = {
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'submitted'
    } as {
      draftId: string
      auditId: string
      status: string
      writebackStatus: string
    }
    scheduleDraftState.publishError = '旧的回写状态'
    scheduleDraftState.taskPoolItems = [
      {
        workOrderCode: 'MO-2001',
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

    const wrapper = mount(PlannerWorkbenchView)

    await wrapper.get('[data-testid="add-to-schedule-MO-2001"]').trigger('click')

    expect(scheduleDraftState.publishResult).toBeNull()
    expect(scheduleDraftState.publishError).toBe('')
  })

  it('加入排程后会清空 mock store 里的旧 urgentInsertion，避免沿用过期急单插入位置', async () => {
    ;(scheduleDraftState as any).urgentInsertion = {
      taskId: 'WO-URGENT-001',
      resourceId: 'LINE-A',
      startAt: '2026-04-24T08:30:00Z',
      endAt: '2026-04-24T09:00:00Z'
    }
    scheduleDraftState.taskPoolItems = [
      {
        workOrderCode: 'MO-2001',
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

    const wrapper = mount(PlannerWorkbenchView)

    await wrapper.get('[data-testid="add-to-schedule-MO-2001"]').trigger('click')

    expect((scheduleDraftState as any).urgentInsertion).toBeNull()
  })

  it('回写提交中入口按钮切换为查看回写状态', () => {
    scheduleDraftState.publishResult = null
    scheduleDraftState.publishLoading = true

    const wrapper = mount(PlannerWorkbenchView)

    expect(wrapper.get('[data-testid="open-publish-dialog"]').text()).toBe('查看回写状态')
  })

  it('回写失败后入口按钮切换为查看回写状态', () => {
    scheduleDraftState.publishResult = null
    scheduleDraftState.publishLoading = false
    scheduleDraftState.publishError = '存在阻塞项，无法回写'

    const wrapper = mount(PlannerWorkbenchView)

    expect(wrapper.get('[data-testid="open-publish-dialog"]').text()).toBe('查看回写状态')
  })

  it('回写入队后入口按钮切换为查看回写状态', () => {
    scheduleDraftState.publishResult = {
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'pending'
    }

    const wrapper = mount(PlannerWorkbenchView)

    expect(wrapper.get('[data-testid="open-publish-dialog"]').text()).toBe('查看回写状态')
  })

  it('点击确认回写时先打开弹窗而不立即触发发布', async () => {
    scheduleDraftState.publishResult = null

    const wrapper = mount(PlannerWorkbenchView)
    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')

    expect(publishPlannerDraft).not.toHaveBeenCalled()
    expect(wrapper.find('[aria-label="回写确认"]').exists()).toBe(true)
  })



  it('回写提交中重新打开弹窗时仍保留加载状态', async () => {
    scheduleDraftState.publishResult = null
    scheduleDraftState.publishLoading = true

    const wrapper = mount(PlannerWorkbenchView)
    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写确认"]')
    expect(publishDialog.text()).toContain('加载中')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    const reopenedDialog = wrapper.get('[aria-label="回写确认"]')
    expect(reopenedDialog.text()).toContain('加载中')
  })

  it('回写失败后重新打开弹窗时仍保留上一轮阻塞信息', async () => {
    scheduleDraftState.publishResult = null
    scheduleDraftState.publishLoading = false
    scheduleDraftState.publishError = '存在阻塞项，无法回写'

    const wrapper = mount(PlannerWorkbenchView)
    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写确认"]')
    expect(publishDialog.text()).toContain('存在阻塞项，无法回写')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写确认"]')
    expect(publishDialog.text()).toContain('当前草稿：draft-1')
    expect(publishDialog.text()).toContain('存在阻塞项，无法回写')
    expect(publishDialog.text()).not.toContain('暂无回写结果')
  })

  it('已有回写结果但刷新失败时重新打开弹窗仍保留上一轮状态摘要', async () => {
    scheduleDraftState.publishResult = {
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    } as {
      draftId: string
      auditId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: null
    }
    scheduleDraftState.publishError = '加载回写状态失败'
    scheduleDraftState.taskPoolItems = defaultTaskPoolItems()
    scheduleDraftState.scheduledItems = defaultScheduledItems()

    const wrapper = mount(PlannerWorkbenchView)
    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写已进入队列"]')
    expect(publishDialog.text()).toContain('加载回写状态失败')
    expect(publishDialog.text()).toContain('结果版本：draft-1')
    expect(publishDialog.text()).toContain('当前结果版本已进入回写队列')
    expect(publishDialog.text()).not.toContain('暂无回写结果')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已进入队列"]')
    expect(publishDialog.text()).toContain('加载回写状态失败')
    expect(publishDialog.text()).toContain('结果版本：draft-1')
    expect(publishDialog.text()).toContain('当前结果版本已进入回写队列')
    expect(publishDialog.text()).not.toContain('暂无回写结果')
  })

  it('回写 submitted 后重新打开回写弹窗时会再次按 auditId 刷新后端状态', async () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted'
    } as {
      auditId: string
      draftId: string
      status: string
      writebackStatus: string
    }

    const wrapper = mount(PlannerWorkbenchView)
    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写已进入队列"]')
    expect(publishDialog.text()).toContain('结果版本：draft-1')
    expect(publishDialog.text()).toContain('当前结果版本已进入回写队列')
    expect(refreshPublishStatus).toHaveBeenCalledTimes(1)

    refreshPublishStatus.mockClear()

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    expect(refreshPublishStatus).toHaveBeenCalledTimes(1)
  })

  it('首次状态查询失败但仍保留 auditId 时，重新打开回写弹窗会继续刷新状态', async () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'pending',
      message: 'queued',
      retryable: false,
      attemptCount: 0,
      maxAttempts: 0,
      nextRetryAt: null
    } as {
      auditId: string
      draftId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: null
    }

    const wrapper = mount(PlannerWorkbenchView)
    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    expect(refreshPublishStatus).toHaveBeenCalledTimes(1)

    refreshPublishStatus.mockClear()

    await wrapper.get('[aria-label="回写请求已提交"] button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    expect(refreshPublishStatus).toHaveBeenCalledTimes(1)
  })

  it('风险侧栏会保留未映射到当前草稿的受影响任务编号', async () => {
    scheduleDraftState.suggestions = [
      {
        action: 'reassign_same_group',
        reason: '冲压组仍有剩余能力'
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
    ;(scheduleDraftState as any).affectedTaskIds = ['TASK-001', 'WO-URGENT-001']

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()
    await wrapper.get('[role="tab"][aria-controls="planner-panel-risk"]').trigger('click')
    await flushPromises()

    const riskPanel = wrapper.findComponent(RiskSidePanel)
    expect(riskPanel.exists()).toBe(true)
    expect(riskPanel.text()).toContain('TASK-001')
    expect(riskPanel.text()).toContain('WO-URGENT-001')
  })

  it('风险侧栏会显示受影响任务所属资源组', async () => {
    scheduleDraftState.suggestions = [
      {
        action: 'reassign_same_group',
        reason: '冲压组仍有剩余能力'
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
    ;(scheduleDraftState as any).affectedTaskIds = ['TASK-001']

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()
    await wrapper.get('[role="tab"][aria-controls="planner-panel-risk"]').trigger('click')
    await flushPromises()

    expect(wrapper.findComponent(RiskSidePanel).exists()).toBe(true)
    expect(wrapper.text()).toContain('reassign_same_group')
    expect(wrapper.text()).toContain('TASK-001')
    expect(wrapper.text()).toContain('冲压组')
  })

  it('点击应用建议后会把建议事件交给 store 处理', async () => {
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: '下一可用时间窗可承接'
      }
    ]
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
    ;(scheduleDraftState as any).affectedTaskIds = ['TASK-001']

    const wrapper = mount(PlannerWorkbenchView)
    const tabs = wrapper.get('[role="tablist"]').findAll('[role="tab"]')
    await flushPromises()
    await tabs[2].trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="apply-suggestion-0"]').trigger('click')

    expect(applySuggestionToSchedule).toHaveBeenCalledWith({
      action: 'move_next_slot',
      reason: '下一可用时间窗可承接'
    })
    expect(scheduleDraftState.suggestions).toEqual([])
    expect(scheduleDraftState.affectedTaskIds).toEqual([])
  })

  it('应用建议时会把受影响任务顺延到当前资源末尾，而不是回退到受影响任务原开始时间', async () => {
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: '下一可用时间窗可承接'
      }
    ]
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
    ;(scheduleDraftState as any).affectedTaskIds = ['TASK-001']

    const wrapper = mount(PlannerWorkbenchView)
    const tabs = wrapper.get('[role="tablist"]').findAll('[role="tab"]')
    await flushPromises()
    await tabs[2].trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="apply-suggestion-0"]').trigger('click')

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
      }
    ])
  })

  it('应用建议后会清空 mock store 里的旧 urgentError，避免沿用过期急单错误', async () => {
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: '下一可用时间窗可承接'
      }
    ]
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
    ;(scheduleDraftState as any).affectedTaskIds = ['TASK-001']
    scheduleDraftState.urgentError = '旧的急单建议错误'

    const wrapper = mount(PlannerWorkbenchView)
    const tabs = wrapper.get('[role="tablist"]').findAll('[role="tab"]')
    await flushPromises()
    await tabs[2].trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="apply-suggestion-0"]').trigger('click')

    expect(scheduleDraftState.urgentError).toBe('')
  })


  it('急单弹窗关闭时收起弹窗', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    const urgentButton = wrapper.get('[data-testid="open-urgent-order"]')
    await urgentButton.trigger('click')
    await flushPromises()
    await wrapper.get('[aria-label="急单插入"] button').trigger('click')

    expect(wrapper.find('[aria-label="急单插入"]').exists()).toBe(false)
  })

  it('回写 submitted 后重新打开回写弹窗时摘要改为已进入回写队列', async () => {
    scheduleDraftState.publishResult = {
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted'
    }

    const wrapper = mount(PlannerWorkbenchView)
    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写已进入队列"]')
    expect(publishDialog.text()).toContain('当前结果版本已进入回写队列')
    expect(publishDialog.text()).not.toContain('当前结果版本的回写请求已提交')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    const reopenedDialog = wrapper.get('[aria-label="回写已进入队列"]')
    expect(reopenedDialog.text()).toContain('当前结果版本已进入回写队列')
    expect(reopenedDialog.text()).not.toContain('当前结果版本的回写请求已提交')
  })

  it('回写 pending 后重新打开回写弹窗时摘要仍保持请求已提交语义而不混入已进入队列', async () => {
    scheduleDraftState.publishResult = {
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'pending'
    }

    const wrapper = mount(PlannerWorkbenchView)
    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写请求已提交"]')
    expect(publishDialog.text()).toContain('当前结果版本的回写请求已提交')
    expect(publishDialog.text()).not.toContain('当前结果版本已进入回写队列')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    const reopenedDialog = wrapper.get('[aria-label="回写请求已提交"]')
    expect(reopenedDialog.text()).toContain('当前结果版本的回写请求已提交')
    expect(reopenedDialog.text()).not.toContain('当前结果版本已进入回写队列')
  })

  it('回写终态失败后重新打开回写弹窗时会保留失败语义', async () => {
    scheduleDraftState.publishResult = {
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'TERMINAL_FAILED',
      message: 'writeback_failed',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    } as {
      draftId: string
      auditId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: null
    }

    const wrapper = mount(PlannerWorkbenchView)
    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写失败"]')
    expect(publishDialog.text()).toContain('当前结果版本回写失败')
    expect(publishDialog.text()).toContain('回写状态：回写失败')
    expect(publishDialog.text()).toContain('回写说明：writeback_failed')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写失败"]')
    expect(publishDialog.text()).toContain('当前结果版本回写失败')
    expect(publishDialog.text()).toContain('回写状态：回写失败')
    expect(publishDialog.text()).toContain('回写说明：writeback_failed')
  })

  it('有排程数据时显示资源负载概览', async () => {
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
        resourceId: 'LINE-B',
        resourceGroupName: '装配组',
        startAt: '2026-04-24T09:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ]

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    const loadSection = wrapper.find('[aria-label="资源负载概览"]')
    expect(loadSection.exists()).toBe(true)
    expect(loadSection.text()).toContain('LINE-A')
    expect(loadSection.text()).toContain('LINE-B')
  })

  it('无排程数据时不显示资源负载概览', async () => {
    scheduleDraftState.scheduledItems = []

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    expect(wrapper.find('[aria-label="资源负载概览"]').exists()).toBe(false)
  })

  it('回写重试中后重新打开回写弹窗时会保留重试中语义', async () => {
    scheduleDraftState.publishResult = {
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: 'gateway_timeout',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    } as {
      draftId: string
      auditId: string
      status: string
      writebackStatus: string
      message: string
      retryable: boolean
      attemptCount: number
      maxAttempts: number
      nextRetryAt: string
    }

    const wrapper = mount(PlannerWorkbenchView)
    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写重试中"]')
    expect(publishDialog.text()).toContain('当前结果版本回写重试中')
    expect(publishDialog.text()).toContain('回写状态：回写重试中')
    expect(publishDialog.text()).toContain('回写说明：gateway_timeout')
    expect(publishDialog.text()).toContain('下一次重试：2026-04-24T10:30:00Z')
    expect(publishDialog.text()).not.toContain('可关闭弹窗并等待当前结果版本的回写结果')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写重试中"]')
    expect(publishDialog.text()).toContain('当前结果版本回写重试中')
    expect(publishDialog.text()).toContain('回写状态：回写重试中')
    expect(publishDialog.text()).toContain('回写说明：gateway_timeout')
    expect(publishDialog.text()).toContain('下一次重试：2026-04-24T10:30:00Z')
    expect(publishDialog.text()).not.toContain('可关闭弹窗并等待当前结果版本的回写结果')
  })
})

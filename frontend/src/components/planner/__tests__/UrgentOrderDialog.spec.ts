import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import type { ScheduledItem, Suggestion, TaskPoolItem } from '@/stores/scheduleDraft'

const { scheduleDraftState, resetUrgentReplanResult } = vi.hoisted(() => ({
  scheduleDraftState: {
    loading: false,
    taskPoolItems: [] as TaskPoolItem[],
    scheduledItems: [] as ScheduledItem[],
    affectedTaskIds: [] as string[],
    suggestions: [
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ] as Suggestion[],
    urgentInsertion: null as { taskId: string; resourceId: string; startAt: string; endAt: string } | null,
    error: '',
    publishResult: null,
    urgentLoading: false,
    urgentError: '',
    publishLoading: false,
    publishError: ''
  },
  resetUrgentReplanResult: vi.fn(() => {
    scheduleDraftState.affectedTaskIds = []
    scheduleDraftState.suggestions = []
    scheduleDraftState.urgentInsertion = null
    scheduleDraftState.urgentError = ''
    scheduleDraftState.urgentLoading = false
  })
}))

vi.mock('@/stores/scheduleDraft', () => ({
  scheduleDraftState,
  resetUrgentReplanResult
}))

import UrgentOrderDialog from '@/components/planner/UrgentOrderDialog.vue'

describe('UrgentOrderDialog', () => {
  beforeEach(() => {
    scheduleDraftState.loading = false
    scheduleDraftState.error = ''
    scheduleDraftState.urgentLoading = false
    scheduleDraftState.urgentError = ''
    scheduleDraftState.publishLoading = false
    scheduleDraftState.publishError = ''
    scheduleDraftState.affectedTaskIds = []
    scheduleDraftState.scheduledItems = []
    scheduleDraftState.suggestions = [
      {
        action: 'move_next_slot',
        reason: 'line overloaded'
      }
    ]
    scheduleDraftState.urgentInsertion = null
    resetUrgentReplanResult.mockClear()
  })

  it('打开时显示重排建议', () => {
    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('move_next_slot')
    expect(wrapper.text()).toContain('line overloaded')
  })

  it('显示受影响任务所在资源组', () => {
    scheduleDraftState.affectedTaskIds = ['TASK-001', 'TASK-003']
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      },
      {
        taskId: 'TASK-003',
        resourceId: 'LINE-C',
        resourceGroupName: '装配组',
        startAt: '2026-04-24T10:00:00Z',
        endAt: '2026-04-24T11:00:00Z'
      }
    ]

    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('TASK-001')
    expect(wrapper.text()).toContain('冲压组')
    expect(wrapper.text()).toContain('TASK-003')
    expect(wrapper.text()).toContain('装配组')
  })

  it('受影响任务包含未落到当前草稿的急单时仍显示任务编号', () => {
    scheduleDraftState.affectedTaskIds = ['TASK-001', 'WO-URGENT-001']
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ]

    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('TASK-001')
    expect(wrapper.text()).toContain('WO-URGENT-001')
  })

  it('提交插单表单时会上报资源与时间窗', async () => {
    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true,
        resourceOptions: [
          {
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组'
          },
          {
            resourceId: 'LINE-B',
            resourceGroupName: '装配组'
          }
        ],
        defaultUrgentResourceId: 'LINE-A',
        defaultUrgentStartAt: '2026-04-24T08:00:00Z',
        defaultUrgentEndAt: '2026-04-24T08:30:00Z'
      }
    })

    await wrapper.get('select').setValue('LINE-B')
    await wrapper.get('input[aria-label="插入开始时间"]').setValue('2026-04-24T09:00:00Z')
    await wrapper.get('input[aria-label="插入结束时间"]').setValue('2026-04-24T09:45:00Z')
    await wrapper.get('button[data-testid="submit-urgent-replan"]').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([
      [
        {
          resourceId: 'LINE-B',
          startAt: '2026-04-24T09:00:00Z',
          endAt: '2026-04-24T09:45:00Z'
        }
      ]
    ])
  })
  it('时间为空时显示可读提示，修正后恢复提交', async () => {
    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true,
        resourceOptions: [
          {
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组'
          }
        ],
        defaultUrgentResourceId: 'LINE-A',
        defaultUrgentStartAt: '2026-04-24T08:00:00Z',
        defaultUrgentEndAt: '2026-04-24T08:30:00Z'
      }
    })

    await wrapper.get('input[aria-label="插入开始时间"]').setValue('')
    await wrapper.get('button[data-testid="submit-urgent-replan"]').trigger('click')

    expect(wrapper.text()).toContain('请填写完整的插入时间窗')
    expect(wrapper.emitted('submit')).toBeUndefined()

    await wrapper.get('input[aria-label="插入开始时间"]').setValue('2026-04-24T09:00:00Z')
    await wrapper.get('input[aria-label="插入结束时间"]').setValue('2026-04-24T09:30:00Z')
    await wrapper.get('button[data-testid="submit-urgent-replan"]').trigger('click')

    expect(wrapper.text()).not.toContain('请填写完整的插入时间窗')
    expect(wrapper.emitted('submit')).toEqual([
      [
        {
          resourceId: 'LINE-A',
          startAt: '2026-04-24T09:00:00Z',
          endAt: '2026-04-24T09:30:00Z'
        }
      ]
    ])
  })

  it('结束时间早于开始时间时显示可读提示，修正后立即清空提示并恢复提交', async () => {
    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true,
        resourceOptions: [
          {
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组'
          }
        ],
        defaultUrgentResourceId: 'LINE-A',
        defaultUrgentStartAt: '2026-04-24T08:00:00Z',
        defaultUrgentEndAt: '2026-04-24T08:30:00Z'
      }
    })

    await wrapper.get('input[aria-label="插入开始时间"]').setValue('2026-04-24T09:00:00Z')
    await wrapper.get('input[aria-label="插入结束时间"]').setValue('2026-04-24T08:45:00Z')
    await wrapper.get('button[data-testid="submit-urgent-replan"]').trigger('click')

    expect(wrapper.text()).toContain('插入结束时间不能早于开始时间')
    expect(wrapper.emitted('submit')).toBeUndefined()

    await wrapper.get('input[aria-label="插入结束时间"]').setValue('2026-04-24T09:45:00Z')

    expect(wrapper.text()).not.toContain('插入结束时间不能早于开始时间')

    await wrapper.get('button[data-testid="submit-urgent-replan"]').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([
      [
        {
          resourceId: 'LINE-A',
          startAt: '2026-04-24T09:00:00Z',
          endAt: '2026-04-24T09:45:00Z'
        }
      ]
    ])
  })

  it('开始或结束时间为空时不提交插单请求', async () => {
    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true,
        resourceOptions: [
          {
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组'
          }
        ],
        defaultUrgentResourceId: 'LINE-A',
        defaultUrgentStartAt: '2026-04-24T08:00:00Z',
        defaultUrgentEndAt: '2026-04-24T08:30:00Z'
      }
    })

    await wrapper.get('input[aria-label="插入开始时间"]').setValue('')
    await wrapper.get('button[data-testid="submit-urgent-replan"]').trigger('click')

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('结束时间早于开始时间时不提交插单请求', async () => {
    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true,
        resourceOptions: [
          {
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组'
          }
        ],
        defaultUrgentResourceId: 'LINE-A',
        defaultUrgentStartAt: '2026-04-24T08:00:00Z',
        defaultUrgentEndAt: '2026-04-24T08:30:00Z'
      }
    })

    await wrapper.get('input[aria-label="插入开始时间"]').setValue('2026-04-24T09:00:00Z')
    await wrapper.get('input[aria-label="插入结束时间"]').setValue('2026-04-24T08:45:00Z')
    await wrapper.get('button[data-testid="submit-urgent-replan"]').trigger('click')

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('没有建议时显示空状态提示', () => {
    scheduleDraftState.suggestions = []

    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('暂无重排建议')
  })

  it('急单加载中时显示加载提示', () => {
    scheduleDraftState.urgentLoading = true
    scheduleDraftState.suggestions = []

    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('加载中')
  })

  it('急单失败时显示错误信息', () => {
    scheduleDraftState.urgentError = '加载急单重排建议失败'
    scheduleDraftState.suggestions = []

    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('加载急单重排建议失败')
  })


  it('修改插单资源时会清空上一轮急单建议', async () => {
    scheduleDraftState.affectedTaskIds = ['TASK-001']
    scheduleDraftState.scheduledItems = [
      {
        taskId: 'TASK-001',
        resourceId: 'LINE-A',
        resourceGroupName: '冲压组',
        startAt: '2026-04-24T08:00:00Z',
        endAt: '2026-04-24T10:00:00Z'
      }
    ]

    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true,
        resourceOptions: [
          {
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组'
          },
          {
            resourceId: 'LINE-B',
            resourceGroupName: '装配组'
          }
        ],
        defaultUrgentResourceId: 'LINE-A',
        defaultUrgentStartAt: '2026-04-24T08:00:00Z',
        defaultUrgentEndAt: '2026-04-24T08:30:00Z'
      }
    })

    await wrapper.get('select').setValue('LINE-B')

    expect(resetUrgentReplanResult).toHaveBeenCalledTimes(1)
    expect(scheduleDraftState.suggestions).toEqual([])
    expect(scheduleDraftState.affectedTaskIds).toEqual([])
    expect(wrapper.text()).toContain('暂无重排建议')
  })

  it('只存在上一轮急单插入上下文时，修改插单时间也会清空旧结果', async () => {
    scheduleDraftState.suggestions = []
    scheduleDraftState.affectedTaskIds = []
    scheduleDraftState.urgentInsertion = {
      taskId: 'WO-URGENT-001',
      resourceId: 'LINE-A',
      startAt: '2026-04-24T08:00:00Z',
      endAt: '2026-04-24T08:30:00Z'
    }

    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true,
        resourceOptions: [
          {
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组'
          }
        ],
        defaultUrgentResourceId: 'LINE-A',
        defaultUrgentStartAt: '2026-04-24T08:00:00Z',
        defaultUrgentEndAt: '2026-04-24T08:30:00Z'
      }
    })

    await wrapper.get('input[aria-label="插入开始时间"]').setValue('2026-04-24T09:00:00Z')

    expect(resetUrgentReplanResult).toHaveBeenCalledTimes(1)
    expect(scheduleDraftState.urgentInsertion).toBeNull()
  })

  it('点击建议上的应用到画板时会上报建议并关闭弹窗', async () => {
    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true
      }
    })

    await wrapper.get('[data-testid="apply-urgent-suggestion-0"]').trigger('click')

    expect(wrapper.emitted('apply-suggestion')).toEqual([
      [
        {
          action: 'move_next_slot',
          reason: 'line overloaded'
        }
      ]
    ])
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('点击关闭按钮时通知父组件关闭', async () => {
    const wrapper = mount(UrgentOrderDialog, {
      props: {
        modelValue: true
      }
    })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })
})


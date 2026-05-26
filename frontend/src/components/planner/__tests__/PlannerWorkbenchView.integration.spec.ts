import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { get, post, put, del } = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn()
}))

vi.mock(import('@/api/http'), () => ({
  http: {
    get,
    post,
    put,
    delete: del
  }
}))

import PlannerWorkbenchView from '@/views/PlannerWorkbenchView.vue'
import TaskPoolPanel from '@/components/planner/TaskPoolPanel.vue'
import ScheduleBoard from '@/components/planner/ScheduleBoard.vue'
import RiskSidePanel from '@/components/planner/RiskSidePanel.vue'
import { resetScheduleDraftState, scheduleDraftState } from '@/stores/scheduleDraft'

describe('PlannerWorkbenchView integration', () => {
  beforeEach(() => {
    resetScheduleDraftState()
    get.mockReset()
    post.mockReset()

    get.mockResolvedValueOnce([
      {
        workOrderCode: 'MO-1001',
        dueAt: '2026-04-25T00:00:00Z',
        urgent: true,
        materialRisk: 'missing',
        readiness: 'ready'
      }
    ])
    post.mockResolvedValueOnce({
      draftId: 'draft-1',
      items: [
        {
          taskId: 'TASK-001',
          resourceId: 'LINE-A',
          resourceGroupName: '冲压组',
          startAt: '2026-04-24T08:00:00Z',
          endAt: '2026-04-24T10:00:00Z'
        }
      ]
    })
  })

  it('页签具备可访问性且默认停留在待排任务面板', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    const tabs = wrapper.get('[role="tablist"]').findAll('[role="tab"]')
    expect(tabs).toHaveLength(3)
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(tabs[0].attributes('aria-controls')).toBe('planner-panel-taskPool')
    expect(tabs[1].attributes('aria-selected')).toBe('false')
    expect(tabs[1].attributes('aria-controls')).toBe('planner-panel-scheduleBoard')
    expect(tabs[2].attributes('aria-selected')).toBe('false')
    expect(tabs[2].attributes('aria-controls')).toBe('planner-panel-risk')
    expect(wrapper.findComponent(TaskPoolPanel).exists()).toBe(true)
    expect(wrapper.findComponent(ScheduleBoard).exists()).toBe(false)
    expect(wrapper.findComponent(RiskSidePanel).exists()).toBe(false)
  })

  it('加载中时不渲染页签壳', () => {
    scheduleDraftState.loading = true
    const wrapper = mount(PlannerWorkbenchView)

    expect(wrapper.get('.status-block').text()).toBe('加载中')
    expect(wrapper.find('[role="tablist"]').exists()).toBe(false)
  })

  it('错误时不渲染页签壳', () => {
    scheduleDraftState.error = '加载排程数据失败'
    const wrapper = mount(PlannerWorkbenchView)

    expect(wrapper.get('.status-block').text()).toBe('加载排程数据失败')
    expect(wrapper.find('[role="tablist"]').exists()).toBe(false)
  })

  it('空状态时不渲染页签壳', async () => {
    get.mockReset()
    post.mockReset()
    get.mockResolvedValueOnce([])
    post.mockResolvedValueOnce({
      draftId: 'draft-1',
      items: []
    })
    scheduleDraftState.taskPoolItems = []
    scheduleDraftState.scheduledItems = []
    scheduleDraftState.suggestions = []

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    expect(wrapper.get('.status-block').text()).toBe('暂无排程数据')
    expect(wrapper.find('[role="tablist"]').exists()).toBe(false)
  })

  it('加入排程后会清空旧回写状态并避免沿用过期结果版本', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

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
    }
    scheduleDraftState.publishError = '旧的回写状态'
    await flushPromises()

    const addToScheduleButton = wrapper.get('[data-testid="add-to-schedule-MO-1001"]')
    await addToScheduleButton.trigger('click')
    await flushPromises()

    expect(scheduleDraftState.publishResult).toBeNull()
    expect(scheduleDraftState.publishError).toBe('')
    expect(wrapper.get('[data-testid="open-publish-dialog"]').text()).toBe('确认回写')
  })

  it('同页重新打开回写弹窗并刷新成功后会清掉上一轮错误并显示后端新状态', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

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
    scheduleDraftState.publishError = '加载回写状态失败'
    get.mockResolvedValueOnce({
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
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    expect(publishButton.text()).toBe('查看回写状态')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).toContain('回写说明：writeback_succeeded')
    expect(publishDialog.text()).not.toContain('加载回写状态失败')
    expect(scheduleDraftState.publishError).toBe('')
    expect(get).toHaveBeenLastCalledWith('/api/writeback/audit-1')
  })

  it('发布后首次查状态失败，再次打开弹窗刷新成功后会显示后端新状态', async () => {
    post.mockResolvedValueOnce({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending'
    })
    get.mockRejectedValueOnce(new Error('HTTP 500'))
    get.mockResolvedValueOnce({
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

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="confirm-publish"]').trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写请求已提交"]')
    expect(publishDialog.text()).toContain('加载回写状态失败')
    expect(publishDialog.text()).toContain('当前结果版本的回写请求已提交')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).toContain('回写说明：writeback_succeeded')
    expect(publishDialog.text()).not.toContain('加载回写状态失败')
    expect(scheduleDraftState.publishError).toBe('')
    expect(get).toHaveBeenNthCalledWith(2, '/api/writeback/audit-1')
    expect(get).toHaveBeenNthCalledWith(3, '/api/writeback/audit-1')
  })

  it('同页重新打开 submitted 回写弹窗刷新失败时保留队列语义并显示错误', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

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
    get.mockRejectedValueOnce(new Error('HTTP 500'))
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    expect(publishButton.text()).toBe('查看回写状态')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写已进入队列"]')
    expect(publishDialog.text()).toContain('加载回写状态失败')
    expect(publishDialog.text()).toContain('回写已进入队列')
    expect(publishDialog.text()).toContain('当前结果版本已进入回写队列')
    expect(publishDialog.text()).not.toContain('当前结果版本的回写请求已提交')
    expect(scheduleDraftState.publishError).toBe('加载回写状态失败')
    expect(get).toHaveBeenLastCalledWith('/api/writeback/audit-1')
  })

  it('连续两次打开回写弹窗刷新时，旧刷新结果不会覆盖后一次新状态', async () => {
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

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

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
    await flushPromises()

    get
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

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写请求已提交"]')
    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

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
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')

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
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).not.toContain('当前结果版本已进入回写队列')
  })

  it('连续两次打开回写弹窗刷新时，旧刷新失败不会覆盖后一次成功状态', async () => {
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

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

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
    await flushPromises()

    get
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

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写请求已提交"]')
    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

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
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).not.toContain('加载回写状态失败')

    rejectFirstRefresh(new Error('HTTP 500'))
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).not.toContain('加载回写状态失败')
  })

  it('发布后的旧状态查询结果不会覆盖后续重新打开弹窗刷新拿到的新状态', async () => {
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

    post.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolvePublish = resolve
        })
    )
    get
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

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="confirm-publish"]').trigger('click')
    await flushPromises()

    resolvePublish({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending'
    })
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写请求已提交"]')
    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

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
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).toContain('回写说明：writeback_succeeded')

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
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).toContain('回写说明：writeback_succeeded')
    expect(publishDialog.text()).not.toContain('当前结果版本已进入回写队列')
  })

  it('连续重新打开回写弹窗时 pending 状态仍保持请求已提交语义', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

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
    get
      .mockResolvedValueOnce({
        auditId: 'audit-1',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'pending',
        message: 'queued',
        retryable: false,
        attemptCount: 1,
        maxAttempts: 3,
        nextRetryAt: null
      })
      .mockResolvedValueOnce({
        auditId: 'audit-1',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'pending',
        message: 'queued',
        retryable: false,
        attemptCount: 1,
        maxAttempts: 3,
        nextRetryAt: null
      })
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写请求已提交"]')
    expect(publishDialog.text()).toContain('回写请求已提交')
    expect(publishDialog.text()).toContain('当前结果版本的回写请求已提交')
    expect(publishDialog.text()).not.toContain('当前结果版本已进入回写队列')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写请求已提交"]')
    expect(publishDialog.text()).toContain('回写请求已提交')
    expect(publishDialog.text()).toContain('当前结果版本的回写请求已提交')
    expect(publishDialog.text()).not.toContain('当前结果版本已进入回写队列')
  })

  it('连续重新打开回写弹窗时会按 pending → submitted → completed 展示后端阶段流转', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

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
    get
      .mockResolvedValueOnce({
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
      .mockResolvedValueOnce({
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
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写已进入队列"]')
    expect(publishDialog.text()).toContain('回写已进入队列')
    expect(publishDialog.text()).toContain('当前结果版本已进入回写队列')
    expect(publishDialog.text()).not.toContain('当前结果版本的回写请求已提交')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).not.toContain('当前结果版本已进入回写队列')
    expect(publishDialog.text()).not.toContain('当前结果版本的回写请求已提交')
  })

  it('存在阻塞项时重新打开回写弹窗会保留阻塞信息', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = null
    scheduleDraftState.publishLoading = false
    scheduleDraftState.publishError = '存在阻塞项，无法回写'
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写确认"]')
    expect(publishDialog.text()).toContain('存在阻塞项，无法回写')
    expect(publishDialog.text()).toContain('当前草稿：draft-1')
    expect(publishDialog.text()).not.toContain('暂无回写结果')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写确认"]')
    expect(publishDialog.text()).toContain('存在阻塞项，无法回写')
    expect(publishDialog.text()).toContain('当前草稿：draft-1')
    expect(publishDialog.text()).not.toContain('暂无回写结果')
  })

  it('回写前校验未通过后可在同一弹窗重新提交结果版本', async () => {
    post.mockResolvedValueOnce({
      draftId: 'draft-1',
      auditId: 'audit-2',
      status: 'validated',
      writebackStatus: 'pending'
    })
    get.mockResolvedValueOnce({
      auditId: 'audit-2',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validation_failed',
      writebackStatus: 'TERMINAL_FAILED',
      message: '字段映射不合法',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    }
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="未通过回写前校验"]')
    await publishDialog.get('[data-testid="confirm-publish"]').trigger('click')
    await flushPromises()

    expect(post).toHaveBeenNthCalledWith(2, '/api/writeback/publish', {
      draftId: 'draft-1',
      items: scheduleDraftState.scheduledItems
    })
    expect(get).toHaveBeenLastCalledWith('/api/writeback/audit-2')
    expect(wrapper.get('[aria-label="回写已进入队列"]').text()).toContain('当前结果版本已进入回写队列')
  })

  it('终态回写失败后重新打开弹窗刷新失败时会保留失败语义并显示错误', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'TERMINAL_FAILED',
      message: 'writeback_failed',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    }
    scheduleDraftState.publishError = '加载回写状态失败'
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    expect(publishButton.text()).toBe('查看回写状态')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写失败"]')
    expect(publishDialog.text()).toContain('加载回写状态失败')
    expect(publishDialog.text()).toContain('当前结果版本回写失败')
    expect(publishDialog.text()).toContain('回写状态：回写失败')
    expect(publishDialog.text()).toContain('回写说明：writeback_failed')
    expect(scheduleDraftState.publishError).toBe('加载回写状态失败')
  })

  it('终态回写失败后重新打开弹窗且后端仍失败时会保留失败语义', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'TERMINAL_FAILED',
      message: 'writeback_failed',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    }
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    expect(publishButton.text()).toBe('查看回写状态')
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

  it('终态回写失败后可在同一弹窗重新发起回写', async () => {
    post.mockResolvedValueOnce({
      draftId: 'draft-1',
      auditId: 'audit-2',
      status: 'validated',
      writebackStatus: 'pending'
    })
    get.mockResolvedValueOnce({
      auditId: 'audit-2',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'submitted',
      message: 'queued',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    })

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'TERMINAL_FAILED',
      message: 'writeback_failed',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    }
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写失败"]')
    await publishDialog.get('[data-testid="confirm-publish"]').trigger('click')
    await flushPromises()

    expect(post).toHaveBeenNthCalledWith(2, '/api/writeback/publish', {
      draftId: 'draft-1',
      items: scheduleDraftState.scheduledItems
    })
    expect(get).toHaveBeenLastCalledWith('/api/writeback/audit-2')
    expect(wrapper.get('[aria-label="回写已进入队列"]').text()).toContain('当前结果版本已进入回写队列')
  })

  it('回写重试中后重新打开弹窗刷新失败时会保留重试语义并显示错误', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: 'gateway_timeout',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }
    get.mockRejectedValueOnce(new Error('HTTP 500'))
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    expect(publishButton.text()).toBe('查看回写状态')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写重试中"]')
    expect(publishDialog.text()).toContain('加载回写状态失败')
    expect(publishDialog.text()).toContain('当前结果版本回写重试中')
    expect(publishDialog.text()).toContain('回写状态：回写重试中')
    expect(publishDialog.text()).toContain('回写说明：gateway_timeout')
    expect(publishDialog.text()).toContain('下一次重试：2026-04-24T10:30:00Z')
    expect(publishDialog.text()).not.toContain('可关闭弹窗并等待当前结果版本的回写结果')
    expect(scheduleDraftState.publishError).toBe('加载回写状态失败')
    expect(get).toHaveBeenLastCalledWith('/api/writeback/audit-1')
  })

  it('回写重试中后重新打开弹窗且后端仍重试中时会保留重试语义', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: 'gateway_timeout',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }
    get
      .mockResolvedValueOnce({
        auditId: 'audit-1',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'RETRYABLE_FAILED',
        message: 'gateway_timeout',
        retryable: true,
        attemptCount: 2,
        maxAttempts: 3,
        nextRetryAt: '2026-04-24T10:30:00Z'
      })
      .mockResolvedValueOnce({
        auditId: 'audit-1',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'RETRYABLE_FAILED',
        message: 'gateway_timeout',
        retryable: true,
        attemptCount: 2,
        maxAttempts: 3,
        nextRetryAt: '2026-04-24T10:30:00Z'
      })
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    expect(publishButton.text()).toBe('查看回写状态')
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

  it('回写重试中后重新打开弹窗刷新成功时会显示后端新状态', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: 'gateway_timeout',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }
    get.mockResolvedValueOnce({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    })
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).toContain('回写说明：writeback_succeeded')
    expect(publishDialog.text()).not.toContain('可关闭弹窗并等待下一次自动重试')
    expect(get).toHaveBeenLastCalledWith('/api/writeback/audit-1')
  })

  it('连续两次打开回写重试中弹窗刷新时，旧刷新成功不会覆盖后一次新的重试中状态', async () => {
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
      nextRetryAt: string
    }) => void = (_value): void => {
      throw new Error('second refresh promise was not initialized')
    }

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: 'gateway_timeout',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }
    await flushPromises()

    get
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

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写重试中"]')
    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    resolveSecondRefresh({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: 'gateway_timeout',
      retryable: true,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T11:00:00Z'
    })
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写重试中"]')
    expect(publishDialog.text()).toContain('回写重试中')
    expect(publishDialog.text()).toContain('下一次重试：2026-04-24T11:00:00Z')

    resolveFirstRefresh({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    })
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写重试中"]')
    expect(publishDialog.text()).toContain('回写重试中')
    expect(publishDialog.text()).toContain('下一次重试：2026-04-24T11:00:00Z')
    expect(publishDialog.text()).not.toContain('回写已完成')
  })

  it('连续两次打开回写重试中弹窗刷新时，旧刷新失败不会覆盖后一次新的重试中状态', async () => {
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
      nextRetryAt: string
    }) => void = (_value): void => {
      throw new Error('second refresh promise was not initialized')
    }

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: 'gateway_timeout',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }
    await flushPromises()

    get
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

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写重试中"]')
    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    resolveSecondRefresh({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: 'gateway_timeout',
      retryable: true,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T11:00:00Z'
    })
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写重试中"]')
    expect(publishDialog.text()).toContain('回写重试中')
    expect(publishDialog.text()).toContain('下一次重试：2026-04-24T11:00:00Z')
    expect(publishDialog.text()).not.toContain('加载回写状态失败')

    rejectFirstRefresh(new Error('HTTP 500'))
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写重试中"]')
    expect(publishDialog.text()).toContain('回写重试中')
    expect(publishDialog.text()).toContain('下一次重试：2026-04-24T11:00:00Z')
    expect(publishDialog.text()).not.toContain('加载回写状态失败')
  })

  it('终态失败后再次发布成功时会切到新一轮回写结果', async () => {
    post
      .mockResolvedValueOnce({
        draftId: 'draft-1',
        auditId: 'audit-1',
        status: 'validated',
        writebackStatus: 'pending'
      })
      .mockResolvedValueOnce({
        draftId: 'draft-2',
        auditId: 'audit-2',
        status: 'validated',
        writebackStatus: 'pending'
      })
    get
      .mockResolvedValueOnce({
        auditId: 'audit-1',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'TERMINAL_FAILED',
        message: 'writeback_failed',
        retryable: false,
        attemptCount: 3,
        maxAttempts: 3,
        nextRetryAt: null
      })
      .mockResolvedValueOnce({
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

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="confirm-publish"]').trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写失败"]')
    expect(publishDialog.text()).toContain('结果版本：draft-1')
    expect(publishDialog.text()).toContain('回写说明：writeback_failed')

    await publishDialog.get('[data-testid="confirm-publish"]').trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('结果版本：draft-2')
    expect(publishDialog.text()).toContain('回写说明：writeback_succeeded')
    expect(publishDialog.text()).not.toContain('结果版本：draft-1')
    expect(publishDialog.text()).not.toContain('回写说明：writeback_failed')
  })

  it('终态失败后再次发布仍失败时会保持新一轮失败语义', async () => {
    post
      .mockResolvedValueOnce({
        draftId: 'draft-1',
        auditId: 'audit-1',
        status: 'validated',
        writebackStatus: 'pending'
      })
      .mockResolvedValueOnce({
        draftId: 'draft-2',
        auditId: 'audit-2',
        status: 'validated',
        writebackStatus: 'pending'
      })
    get
      .mockResolvedValueOnce({
        auditId: 'audit-1',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'TERMINAL_FAILED',
        message: 'writeback_failed',
        retryable: false,
        attemptCount: 3,
        maxAttempts: 3,
        nextRetryAt: null
      })
      .mockResolvedValueOnce({
        auditId: 'audit-2',
        draftId: 'draft-2',
        status: 'validated',
        writebackStatus: 'TERMINAL_FAILED',
        message: 'writeback_failed_again',
        retryable: false,
        attemptCount: 3,
        maxAttempts: 3,
        nextRetryAt: null
      })

    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="confirm-publish"]').trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写失败"]')
    expect(publishDialog.text()).toContain('结果版本：draft-1')
    expect(publishDialog.text()).toContain('回写说明：writeback_failed')

    await publishDialog.get('[data-testid="confirm-publish"]').trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写失败"]')
    expect(publishDialog.text()).toContain('结果版本：draft-2')
    expect(publishDialog.text()).toContain('回写说明：writeback_failed_again')
    expect(publishDialog.text()).not.toContain('结果版本：draft-1')
  })

  it('回写重试中时同页不显示手动重新发起入口', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: 'gateway_timeout',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }
    get.mockResolvedValueOnce({
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: 'gateway_timeout',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    })
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    expect(publishButton.text()).toBe('查看回写状态')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写重试中"]')
    expect(publishDialog.text()).toContain('可关闭弹窗并等待下一次自动重试')
    expect(publishDialog.find('[data-testid="confirm-publish"]').exists()).toBe(false)
  })

  it('回写完成后重新打开弹窗不再显示手动确认入口', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    }
    get.mockResolvedValueOnce({
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
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    expect(publishButton.text()).toBe('查看回写状态')
    await publishButton.trigger('click')
    await flushPromises()

    const publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.find('[data-testid="confirm-publish"]').exists()).toBe(false)
  })

  it('回写完成后关闭再重新打开弹窗摘要文案保持 success 语义一致', async () => {
    const wrapper = mount(PlannerWorkbenchView)
    await flushPromises()

    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: 'writeback_succeeded',
      retryable: false,
      attemptCount: 1,
      maxAttempts: 3,
      nextRetryAt: null
    }
    get
      .mockResolvedValueOnce({
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
      .mockResolvedValueOnce({
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
    await flushPromises()

    const publishButton = wrapper.get('[data-testid="open-publish-dialog"]')
    await publishButton.trigger('click')
    await flushPromises()

    let publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).toContain('回写状态：回写成功')
    expect(publishDialog.text()).toContain('当前结果版本已完成回写')
    expect(publishDialog.text()).toContain('当前结果版本已回写至金蝶云星空')
    expect(publishDialog.text()).toContain('可按结果版本 draft-1 追溯本次回写结果')
    expect(publishDialog.text()).toContain('系统已记录本次回写结果留痕')

    await publishDialog.get('button').trigger('click')
    await publishButton.trigger('click')
    await flushPromises()

    publishDialog = wrapper.get('[aria-label="回写已完成"]')
    expect(publishDialog.text()).toContain('回写已完成')
    expect(publishDialog.text()).toContain('当前结果版本回写已完成')
    expect(publishDialog.text()).toContain('回写状态：回写成功')
    expect(publishDialog.text()).toContain('当前结果版本已完成回写')
    expect(publishDialog.text()).not.toContain('回写请求已提交')
    expect(publishDialog.text()).not.toContain('当前结果版本的回写请求已提交')
    expect(publishDialog.text()).not.toContain('回写已进入队列')
    expect(publishDialog.text()).not.toContain('当前结果版本已进入回写队列')
  })
})

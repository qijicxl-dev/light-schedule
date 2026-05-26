import { beforeEach, describe, expect, it, vi } from 'vitest'

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

import { plannerApi } from '@/api/planner'

describe('plannerApi', () => {
  beforeEach(() => {
    get.mockReset()
    post.mockReset()
  })

  it('急单重排会带上急单插入资源、时间窗与当前排程项请求体', async () => {
    post.mockResolvedValue({
      urgentTaskId: 'WO-1001',
      affectedTaskIds: ['TASK-001'],
      suggestions: []
    })

    await plannerApi.replanUrgent({
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
        }
      ]
    })

    expect(post).toHaveBeenCalledWith('/api/replans/urgent', {
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
        }
      ]
    })
  })

  it('回写发布会带上草稿标识与当前排程项请求体', async () => {
    post.mockResolvedValue({
      draftId: 'draft-1',
      auditId: 'audit-1',
      status: 'validated',
      writebackStatus: 'pending'
    })

    await plannerApi.publishScheduleDraft({
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

    expect(post).toHaveBeenCalledWith('/api/writeback/publish', {
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

  it('回写状态查询会按 auditId 读取后端真值', async () => {
    get.mockResolvedValue({
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

    await plannerApi.loadWritebackStatus('audit-1')

    expect(get).toHaveBeenCalledWith('/api/writeback/audit-1')
  })

  it('回写阻塞时把 409 映射为可读错误语义', async () => {
    post.mockRejectedValue(new Error('HTTP 409'))

    await expect(
      plannerApi.publishScheduleDraft({
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
    ).rejects.toThrow('存在阻塞项，无法回写')
  })

  it('回写非阻塞失败时把通用状态码映射为可读失败文案', async () => {
    post.mockRejectedValue(new Error('HTTP 500'))

    await expect(
      plannerApi.publishScheduleDraft({
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
    ).rejects.toThrow('发布排程草稿失败')
  })

  it('回写状态查询失败时返回可读错误文案', async () => {
    get.mockRejectedValue(new Error('HTTP 500'))

    await expect(plannerApi.loadWritebackStatus('audit-1')).rejects.toThrow('加载回写状态失败')
  })

  it('资源列表查询会调用后端资源接口', async () => {
    get.mockResolvedValue([
      { resourceId: 'LINE-A', groupName: '冲压组', defaultPlannerResource: true },
      { resourceId: 'LINE-C', groupName: '装配组', defaultPlannerResource: false }
    ])

    const result = await plannerApi.listResources()

    expect(get).toHaveBeenCalledWith('/api/resources')
    expect(result).toHaveLength(2)
    expect(result[0].resourceId).toBe('LINE-A')
    expect(result[1].defaultPlannerResource).toBe(false)
  })
})

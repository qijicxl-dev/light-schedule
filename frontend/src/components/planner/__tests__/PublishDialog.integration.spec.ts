import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PublishDialog from '@/components/planner/PublishDialog.vue'
import { resetScheduleDraftState, scheduleDraftState } from '@/stores/scheduleDraft'

describe('PublishDialog integration', () => {
  beforeEach(() => {
    resetScheduleDraftState()
    scheduleDraftState.draftId = 'draft-1'
    scheduleDraftState.publishLoading = false
    scheduleDraftState.publishError = ''
    scheduleDraftState.publishResult = null
  })

  it('已有回写结果且刷新失败时同时显示错误和上一轮状态摘要', () => {
    scheduleDraftState.publishError = '加载回写状态失败'
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('加载回写状态失败')
    expect(wrapper.text()).toContain('回写已进入队列')
    expect(wrapper.text()).toContain('结果版本：draft-1')
    expect(wrapper.text()).toContain('当前结果版本已进入回写队列')
    expect(wrapper.text()).not.toContain('暂无回写结果')
  })

  it('已有回写结果且刷新中时同时显示加载提示和上一轮状态摘要', () => {
    scheduleDraftState.publishLoading = true
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('加载中')
    expect(wrapper.text()).toContain('回写已进入队列')
    expect(wrapper.text()).toContain('结果版本：draft-1')
    expect(wrapper.text()).toContain('当前结果版本已进入回写队列')
    expect(wrapper.text()).not.toContain('暂无回写结果')
  })
})

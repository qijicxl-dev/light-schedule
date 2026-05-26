import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PublishDialog from '@/components/planner/PublishDialog.vue'
import { resetScheduleDraftState, scheduleDraftState, type PublishStatusView } from '@/stores/scheduleDraft'

describe('PublishDialog', () => {
  beforeEach(() => {
    resetScheduleDraftState()
    scheduleDraftState.draftId = 'draft-1'
    scheduleDraftState.loading = false
    scheduleDraftState.error = ''
    scheduleDraftState.urgentLoading = false
    scheduleDraftState.urgentError = ''
    scheduleDraftState.publishLoading = false
    scheduleDraftState.publishError = ''
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
  })

  it('回写入队时 pending 真值只显示一处结果版本标识', () => {
    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text().match(/结果版本：draft-1/g)?.length).toBe(1)
    expect(wrapper.text()).toContain('结果版本：draft-1')
    expect(wrapper.text()).not.toContain('草稿：draft-1')
  })

  it('回写前校验未通过时标题与 aria-label 改为未通过回写前校验', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('未通过回写前校验')
    expect(wrapper.get('[role="dialog"] > div').text()).toBe('未通过回写前校验')
    expect(wrapper.text()).not.toContain('回写请求状态')
  })

  it('回写前校验未通过时结果区主标题改为未通过回写前校验', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('未通过回写前校验结果版本：draft-1')
    expect(wrapper.text()).not.toContain('回写前校验失败结果版本：draft-1')
  })

  it('回写前校验未通过时顶部摘要句改为停在回写前校验环节', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('结果版本：draft-1当前结果版本停在回写前校验环节')
    expect(wrapper.text()).not.toContain('结果版本：draft-1当前结果版本未通过回写前校验')
    expect(wrapper.text()).not.toContain('结果版本：draft-1当前结果版本回写前校验失败')
  })

  it('回写前校验未通过时顶部摘要句不再显示回写失败', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('未通过回写前校验')
    expect(wrapper.text()).toContain('当前结果版本停在回写前校验环节')
    expect(wrapper.text()).not.toContain('当前结果版本未通过回写前校验')
    expect(wrapper.text()).not.toContain('回写失败')
    expect(wrapper.text()).not.toContain('当前结果版本回写失败')
  })

  it('回写前校验未通过时确认状态标签改为校验状态', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('校验状态：未通过')
    expect(wrapper.text()).not.toContain('确认状态：校验失败')
  })

  it('回写前校验未通过时确认状态详情改为停在回写前校验环节', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本停在回写前校验环节')
    expect(wrapper.text()).not.toContain('当前结果版本校验状态为未通过')
    expect(wrapper.text()).not.toContain('当前结果版本确认状态为回写前校验失败')
  })

  it('回写前校验未通过时确认状态改为校验状态未通过', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('校验状态：未通过')
    expect(wrapper.text()).toContain('当前结果版本停在回写前校验环节')
    expect(wrapper.text()).not.toContain('确认状态：已失败')
    expect(wrapper.text()).not.toContain('当前结果版本确认状态已失败')
  })

  it('回写入队时 pending 真值标题与 aria-label 改为回写请求已提交', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('回写请求已提交')
    expect(wrapper.get('[role="dialog"] > div').text()).toBe('回写请求已提交')
  })

  it('回写 submitted 真值时标题与 aria-label 改为回写已进入队列', () => {
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

    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('回写已进入队列')
    expect(wrapper.get('[role="dialog"] > div').text()).toBe('回写已进入队列')
  })

  it('回写入队时 pending 真值把校验步骤明确表达为回写前校验', () => {
    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('回写前校验：已通过')
    expect(wrapper.text()).not.toContain('校验结果：校验通过')
  })

  it('回写成功时明确当前结果版本回写已完成', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('当前结果版本回写已完成')
  })

  it('回写入队时 pending 真值明确当前结果版本已通过回写前校验', () => {
    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本已通过回写前校验')
  })

  it('回写成功时明确当前结果版本已完成回写', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('当前结果版本已完成回写')
  })

  it('回写成功时确认状态改为已完成', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('当前结果版本确认状态已完成')
  })

  it('回写入队时 pending 真值仍显示已生成当前结果版本的回写清单', () => {
    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('系统已生成当前结果版本的回写清单')
  })

  it('回写成功时执行阶段文案改为已完成回写', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('当前结果版本已按回写清单完成回写')
  })

  it('回写入队时 pending 真值提示可关闭弹窗并等待回写请求进入队列', () => {
    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('可关闭弹窗并等待回写请求进入队列')
  })

  it('回写入队时 pending 真值说明可按具体结果版本号追溯本次回写请求', () => {
    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('可按结果版本 draft-1 追溯本次回写请求')
    expect(wrapper.html()).not.toContain('可按结果版本 draft-1 追溯本次回写</div>')
  })

  it('回写结果停在回写前校验环节时展示校验状态文案', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validation_failed',
      writebackStatus: 'TERMINAL_FAILED',
      message: '存在缺失字段',
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

    expect(wrapper.text()).toContain('校验状态：未通过')
    expect(wrapper.text()).toContain('当前结果版本停在回写前校验环节')
    expect(wrapper.text()).toContain('回写前校验：未通过')
    expect(wrapper.text()).toContain('当前结果版本停在回写前校验环节')
    expect(wrapper.text()).not.toContain('当前结果版本已确认，待回写')
    expect(wrapper.text()).not.toContain('当前结果版本已通过回写前校验')
  })

  it('终态回写失败时不再显示已生成清单和继续执行回写', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本未回写至金蝶云星空')
    expect(wrapper.text()).toContain('当前结果版本停在回写前校验环节，未生成执行清单')
    expect(wrapper.text()).toContain('当前结果版本停在回写前校验环节，未进入回写执行环节')
    expect(wrapper.text()).not.toContain('系统已生成当前结果版本的回写清单')
    expect(wrapper.text()).not.toContain('系统将按当前结果版本执行回写')
  })

  it('终态回写失败时不再提示继续等待后台回写', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本未回写至金蝶云星空')
    expect(wrapper.text()).toContain('请处理回写前校验未通过原因后重新提交结果版本')
    expect(wrapper.text()).not.toContain('可关闭弹窗并等待当前结果版本的回写结果')
    expect(wrapper.text()).not.toContain('系统将继续在后台回写至金蝶云星空')
  })

  it('回写可重试时展示后端返回的重试信息', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: '金蝶网关超时',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('回写说明：金蝶网关超时')
    expect(wrapper.text()).toContain('重试次数：2/3')
    expect(wrapper.text()).toContain('下一次重试：2026-04-24T10:30:00Z')
  })


  it('回写失败状态会显示中文状态语义', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'TERMINAL_FAILED',
      message: '字段映射不合法',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('回写状态：回写失败')
  })

  it('终态回写失败时标题与 aria-label 改为回写失败', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'TERMINAL_FAILED',
      message: '字段映射不合法',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('回写失败')
    expect(wrapper.get('[role="dialog"] > div').text()).toBe('回写失败')
    expect(wrapper.text()).not.toContain('回写请求状态')
  })

  it('回写成功后顶部状态改为已完成', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('回写已完成')
    expect(wrapper.text()).not.toContain('回写请求已提交')
  })

  it('回写成功时标题与 aria-label 改为回写已完成', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.get('[role="dialog"]').attributes('aria-label')).toBe('回写已完成')
    expect(wrapper.get('[role="dialog"] > div').text()).toBe('回写已完成')
    expect(wrapper.text()).not.toContain('回写请求状态')
  })

  it('终态回写失败时顶部状态改为失败', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('未通过回写前校验')
    expect(wrapper.text()).toContain('当前结果版本停在回写前校验环节')
    expect(wrapper.text()).not.toContain('回写请求已提交')
    expect(wrapper.text()).not.toContain('当前结果版本的回写请求已提交')
  })

  it('回写重试中时顶部状态改为重试中', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: '金蝶网关超时',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('回写重试中')
    expect(wrapper.text()).toContain('当前结果版本回写重试中')
    expect(wrapper.text()).not.toContain('回写请求已提交')
    expect(wrapper.text()).not.toContain('当前结果版本的回写请求已提交')
  })

  it('已有回写结果时留痕文案不再使用将来时', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('系统已记录本次回写结果留痕')
    expect(wrapper.text()).not.toContain('系统将记录本次回写留痕')
  })

  it('回写前校验未通过时处理结果标签改为处理结果', () => {

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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('处理结果：未执行回写')
    expect(wrapper.text()).not.toContain('执行方式：未执行回写')
  })

  it('回写前校验未通过时处理结果改为未执行回写', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('处理结果：未执行回写')
    expect(wrapper.text()).not.toContain('执行方式：批量回写')
  })

  it('回写成功后不再提示等待结果和将要回写', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('当前结果版本已完成回写')
    expect(wrapper.text()).toContain('当前结果版本回写结果已生成')
    expect(wrapper.text()).toContain('当前结果版本已回写至金蝶云星空')
    expect(wrapper.text()).not.toContain('可关闭弹窗并等待当前结果版本的回写结果')
    expect(wrapper.text()).not.toContain('当前结果版本将回写至金蝶云星空')
  })

  it('回写成功时追溯与留痕文案改为成功结果语义', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('可按结果版本 draft-1 追溯本次回写结果')
    expect(wrapper.text()).toContain('系统已记录本次回写结果留痕')
    expect(wrapper.text()).not.toContain('可按结果版本 draft-1 追溯本次回写请求')
    expect(wrapper.text()).not.toContain('系统已记录本次回写请求留痕')
  })

  it('回写成功后不再显示继续执行回写', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('当前结果版本已按回写清单完成回写')
    expect(wrapper.text()).not.toContain('系统将按当前结果版本执行回写')
  })

  it('回写失败后确认状态不再显示待处理或待回写', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'TERMINAL_FAILED',
      message: '字段映射不合法',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('确认状态：已失败')
    expect(wrapper.text()).toContain('当前结果版本确认状态已失败')
    expect(wrapper.text()).not.toContain('确认状态：已确认，待回写')
    expect(wrapper.text()).not.toContain('当前结果版本已确认，待回写')
  })

  it('回写成功后确认状态不再显示待回写', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('确认状态：已完成')
    expect(wrapper.text()).toContain('当前结果版本确认状态已完成')
    expect(wrapper.text()).not.toContain('确认状态：已确认，待回写')
    expect(wrapper.text()).not.toContain('当前结果版本已确认，待回写')
  })

  it('回写成功状态会显示成功细节文案', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'SUCCEEDED',
      message: '回写完成',
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

    expect(wrapper.text()).toContain('当前结果版本已完成回写')
  })

  it('回写重试中时提示将按重试策略继续执行', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: '金蝶网关超时',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本将按重试策略继续回写')
    expect(wrapper.text()).not.toContain('系统将按当前结果版本执行回写')
  })

  it('回写重试中时不再提示仅等待结果而是等待下一次重试', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: '金蝶网关超时',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('可关闭弹窗并等待下一次自动重试')
    expect(wrapper.text()).not.toContain('可关闭弹窗并等待当前结果版本的回写结果')
  })

  it('回写重试中时使用持续回写中的目标文案', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: '金蝶网关超时',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本仍将回写至金蝶云星空')
    expect(wrapper.text()).not.toContain('当前结果版本将回写至金蝶云星空')
  })

  it('回写入队时 submitted 真值提示等待队列执行而非泛化等待结果', () => {
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

    expect(wrapper.text()).toContain('可关闭弹窗并等待队列执行回写')
    expect(wrapper.text()).not.toContain('可关闭弹窗并等待当前结果版本的回写结果')
  })

  it('回写入队时 pending 真值使用进入队列后回写中的目标文案', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本将在进入回写队列后回写至金蝶云星空')
    expect(wrapper.text()).not.toContain('当前结果版本将回写至金蝶云星空')
  })

  it('回写入队时 submitted 真值仍使用排队回写中的目标文案', () => {
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

    expect(wrapper.text()).toContain('当前结果版本排队回写至金蝶云星空')
    expect(wrapper.text()).not.toContain('当前结果版本将回写至金蝶云星空')
  })

  it('回写入队时 submitted 真值使用队列状态摘要', () => {
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

    expect(wrapper.text()).toContain('回写已进入队列')
    expect(wrapper.text()).toContain('当前结果版本已进入回写队列')
    expect(wrapper.text()).not.toContain('当前结果版本的回写请求已提交')
  })

  it('回写入队时 submitted 真值追溯文案改为追溯队列状态', () => {
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

    expect(wrapper.text()).toContain('可按结果版本 draft-1 追溯本次回写队列状态')
    expect(wrapper.text()).not.toContain('可按结果版本 draft-1 追溯本次回写请求')
    expect(wrapper.html()).not.toContain('可按结果版本 draft-1 追溯本次回写</div>')
  })

  it('回写入队时 submitted 真值留痕文案改为队列状态留痕', () => {
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

    expect(wrapper.text()).toContain('系统已记录本次回写队列状态留痕')
    expect(wrapper.text()).not.toContain('系统已记录本次回写请求留痕')
    expect(wrapper.text()).not.toContain('系统已记录本次回写留痕')
  })

  it('回写入队时 submitted 真值确认状态改为已进入队列', () => {
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

    expect(wrapper.text()).toContain('确认状态：已进入队列')
    expect(wrapper.text()).toContain('当前结果版本确认状态已进入回写队列')
    expect(wrapper.text()).not.toContain('确认状态：已确认，待回写')
    expect(wrapper.text()).not.toContain('当前结果版本已确认，待回写')
  })

  it('回写入队时 pending 真值提示等待进入回写队列而非等待队列执行', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('可关闭弹窗并等待回写请求进入队列')
    expect(wrapper.text()).not.toContain('可关闭弹窗并等待队列执行回写')
  })

  it('回写入队时 submitted 真值执行阶段改为已进入回写队列', () => {
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

    expect(wrapper.text()).toContain('当前结果版本已进入回写队列，等待执行回写')
    expect(wrapper.text()).not.toContain('系统将按当前结果版本执行回写')
  })

  it('回写入队时 submitted 真值回写状态详情改为等待执行回写', () => {
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

    expect(wrapper.text()).toContain('回写状态：已进入回写队列当前结果版本已进入回写队列，等待执行回写')
    expect(wrapper.text()).not.toContain('回写状态：已进入回写队列当前结果版本已进入回写队列回写说明：queued')
  })

  it('回写入队时 pending 真值回写状态仍停留在请求已提交', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('回写状态：回写请求已提交')
    expect(wrapper.text()).toContain('当前结果版本的回写请求已提交，等待进入回写队列')
    expect(wrapper.text()).not.toContain('回写状态：已进入回写队列')
    expect(wrapper.text()).not.toContain('当前结果版本已进入回写队列')
  })

  it('回写入队时 pending 真值目标去向改为进入队列后回写', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本将在进入回写队列后回写至金蝶云星空')
    expect(wrapper.text()).not.toContain('当前结果版本排队回写至金蝶云星空')
  })

  it('回写入队时 pending 真值确认状态改为请求已提交', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('确认状态：回写请求已提交')
    expect(wrapper.text()).toContain('当前结果版本确认状态为回写请求已提交，等待进入回写队列')
    expect(wrapper.text()).not.toContain('确认状态：已确认，待回写')
    expect(wrapper.text()).not.toContain('当前结果版本已确认，待回写')
  })

  it('回写入队时 pending 真值执行阶段改为请求已提交待进入队列', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本回写请求已提交，等待进入回写队列后执行')
    expect(wrapper.text()).not.toContain('系统将按当前结果版本执行回写')
  })

  it('回写入队时 pending 真值追溯文案改为追溯本次回写请求', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('可按结果版本 draft-1 追溯本次回写请求')
    expect(wrapper.html()).not.toContain('可按结果版本 draft-1 追溯本次回写</div>')
  })

  it('回写入队时 pending 真值留痕文案改为回写请求留痕', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('系统已记录本次回写请求留痕')
    expect(wrapper.text()).not.toContain('系统已记录本次回写留痕')
  })

  it('回写入队时 pending 真值仍使用请求已提交标题', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('回写请求已提交')
    expect(wrapper.text()).toContain('当前结果版本的回写请求已提交')
    expect(wrapper.text()).not.toContain('回写已进入队列')
  })

  it('回写重试中时确认状态显示重试中而非待回写', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: '金蝶网关超时',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('确认状态：重试中')
    expect(wrapper.text()).toContain('当前结果版本确认状态重试中')
    expect(wrapper.text()).not.toContain('确认状态：已确认，待回写')
    expect(wrapper.text()).not.toContain('当前结果版本已确认，待回写')
  })

  it('回写重试中状态会显示重试细节文案', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'RETRYABLE_FAILED',
      message: '金蝶网关超时',
      retryable: true,
      attemptCount: 2,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本将在后台继续重试回写')
  })

  it('回写失败状态会显示失败细节文案', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'TERMINAL_FAILED',
      message: '字段映射不合法',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本回写失败，需人工处理')
  })

  it('终态回写失败时追溯与留痕文案改为失败结果语义', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validated',
      writebackStatus: 'TERMINAL_FAILED',
      message: '字段映射不合法',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('可按结果版本 draft-1 追溯本次回写失败结果')
    expect(wrapper.text()).toContain('系统已记录本次回写失败留痕')
    expect(wrapper.text()).not.toContain('可按结果版本 draft-1 追溯本次回写请求')
    expect(wrapper.text()).not.toContain('系统已记录本次回写请求留痕')
  })

  it('回写前校验未通过时目标文案改为未回写至金蝶云星空', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本未回写至金蝶云星空')
    expect(wrapper.text()).not.toContain('当前结果版本未执行回写至金蝶云星空')
    expect(wrapper.text()).not.toContain('当前结果版本回写目标为金蝶云星空')
  })

  it('回写前校验未通过时回写状态改为未执行回写', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('回写状态：未执行回写')
    expect(wrapper.text()).not.toContain('回写状态：回写失败')
  })

  it('回写前校验未通过时回写状态详情明确停在回写前校验环节', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('回写状态：未执行回写当前结果版本停在回写前校验环节，未执行回写')
    expect(wrapper.text()).not.toContain('回写状态：未执行回写当前结果版本未通过回写前校验，未执行回写')
  })

  it('回写前校验未通过时追溯文案改为追溯回写前校验结果', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('可按结果版本 draft-1 追溯本次回写前校验结果')
    expect(wrapper.text()).not.toContain('可按结果版本 draft-1 追溯本次校验结果')
  })


  it('回写前校验未通过时执行阶段文案改为停在回写前校验环节', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本停在回写前校验环节，未进入回写执行环节')
    expect(wrapper.text()).not.toContain('当前结果版本未进入回写执行环节')
    expect(wrapper.text()).not.toContain('当前结果版本未进入回写执行阶段')
    expect(wrapper.text()).not.toContain('当前结果版本不会继续执行自动回写')
  })

  it('回写前校验未通过时 checklist 文案明确停在回写前校验环节', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('当前结果版本停在回写前校验环节，未生成执行清单')
    expect(wrapper.text()).not.toContain('当前结果版本停在回写前校验阶段，未生成执行清单')
    expect(wrapper.text()).not.toContain('当前结果版本未生成可执行回写清单')
  })

  it('回写前校验未通过时留痕文案改为回写前校验留痕', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('系统已记录本次回写前校验留痕')
    expect(wrapper.text()).not.toContain('系统已记录本次校验留痕')
    expect(wrapper.text()).not.toContain('系统已记录本次回写留痕')
  })

  it('回写前校验未通过时说明标签改为校验说明', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('校验说明：字段映射不合法')
    expect(wrapper.text()).not.toContain('结果说明：字段映射不合法')
    expect(wrapper.text()).not.toContain('回写说明：字段映射不合法')
  })

  it('回写前校验未通过时不会显示重试信息', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).not.toContain('重试次数：3/3')
  })

  it('回写前校验未通过且没有说明时不显示校验说明', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validation_failed',
      writebackStatus: 'TERMINAL_FAILED',
      message: '',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: null
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).not.toContain('结果说明：')
  })

  it('回写前校验未通过时不会显示重试次数标签', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).not.toContain('重试次数：')
  })

  it('回写前校验未通过时不会显示下一次重试', () => {
    scheduleDraftState.publishResult = {
      auditId: 'audit-1',
      draftId: 'draft-1',
      status: 'validation_failed',
      writebackStatus: 'TERMINAL_FAILED',
      message: '字段映射不合法',
      retryable: false,
      attemptCount: 3,
      maxAttempts: 3,
      nextRetryAt: '2026-04-24T10:30:00Z'
    }

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).not.toContain('下一次重试：2026-04-24T10:30:00Z')
  })

  it('回写前校验未通过时 follow-up 文案改为重新提交结果版本', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('请处理回写前校验未通过原因后重新提交结果版本')
    expect(wrapper.text()).not.toContain('请处理校验失败原因后重新提交结果版本')
    expect(wrapper.text()).not.toContain('请处理失败原因后重新发起回写')
  })

  it('回写前校验未通过时显示重新提交结果版本入口', async () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.get('button[data-testid="confirm-publish"]').text()).toBe('重新提交结果版本')

    await wrapper.get('button[data-testid="confirm-publish"]').trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[]])
  })

  it('终态回写失败时显示重新发起回写入口', async () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.get('button[data-testid="confirm-publish"]').text()).toBe('重新发起回写')

    await wrapper.get('button[data-testid="confirm-publish"]').trigger('click')

    expect(wrapper.emitted('confirm')).toEqual([[]])
  })

  it('回写加载中时禁用确认按钮', () => {
    scheduleDraftState.publishLoading = true
    scheduleDraftState.publishResult = null

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.get('button[data-testid="confirm-publish"]').attributes('disabled')).toBeDefined()
  })

  it('回写失败时显示错误信息', () => {
    scheduleDraftState.publishError = '发布排程草稿失败'
    scheduleDraftState.publishResult = null

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.text()).toContain('发布排程草稿失败')
  })

  it('已有回写结果时隐藏确认按钮', () => {
    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.find('button[data-testid="confirm-publish"]').exists()).toBe(false)
  })

  it('回写重试中时不显示手动重新发起入口', () => {
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

    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    expect(wrapper.find('button[data-testid="confirm-publish"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('可关闭弹窗并等待下一次自动重试')
  })

  it('点击关闭按钮时通知父组件关闭', async () => {
    const wrapper = mount(PublishDialog, {
      props: {
        modelValue: true
      }
    })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })
})


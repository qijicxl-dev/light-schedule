import { describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { createWorkOrder } = vi.hoisted(() => ({
  createWorkOrder: vi.fn()
}))

vi.mock('@/api/planner', () => ({
  plannerApi: {
    createWorkOrder
  }
}))

import CreateWorkOrderDialog from '@/components/planner/CreateWorkOrderDialog.vue'

describe('CreateWorkOrderDialog', () => {
  beforeEach(() => {
    createWorkOrder.mockReset()
  })

  it('关闭按钮触发 update:modelValue false', async () => {
    const wrapper = mount(CreateWorkOrderDialog, {
      props: { modelValue: true, 'onUpdate:modelValue': () => {} }
    })

    await wrapper.find('button[type="button"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([false])
  })

  it('空表单提交时显示必填错误', async () => {
    const wrapper = mount(CreateWorkOrderDialog, {
      props: { modelValue: true, 'onUpdate:modelValue': () => {} }
    })

    await wrapper.find('[data-testid="submit-create-work-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请填写工单编号和交期')
    expect(createWorkOrder).not.toHaveBeenCalled()
  })

  it('成功创建后关闭弹窗并触发 created 事件', async () => {
    createWorkOrder.mockResolvedValue({
      workOrderCode: 'WO-002',
      dueAt: '2026-04-25T08:00:00Z',
      urgent: false,
      materialRisk: 'low'
    })

    const wrapper = mount(CreateWorkOrderDialog, {
      props: { modelValue: true, 'onUpdate:modelValue': () => {} }
    })

    await wrapper.find('input[aria-label="工单编号"]').setValue('WO-002')
    await wrapper.find('input[aria-label="交期"]').setValue('2026-04-25T08:00:00Z')
    await wrapper.find('[data-testid="submit-create-work-order"]').trigger('click')
    await flushPromises()

    expect(createWorkOrder).toHaveBeenCalledWith({
      workOrderCode: 'WO-002',
      status: 'released',
      quantity: 1,
      dueAt: '2026-04-25T08:00:00Z',
      routeId: 'ROUTE-01',
      urgent: false,
      parentWorkOrderCodes: [],
      materialRisk: 'low'
    })
    expect(wrapper.emitted('update:modelValue')![0]).toEqual([false])
    expect(wrapper.emitted('created')).toBeTruthy()
  })

  it('创建失败时显示错误信息', async () => {
    createWorkOrder.mockRejectedValue(new Error('工单编号已存在'))

    const wrapper = mount(CreateWorkOrderDialog, {
      props: { modelValue: true, 'onUpdate:modelValue': () => {} }
    })

    await wrapper.find('input[aria-label="工单编号"]').setValue('WO-002')
    await wrapper.find('input[aria-label="交期"]').setValue('2026-04-25T08:00:00Z')
    await wrapper.find('[data-testid="submit-create-work-order"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('工单编号已存在')
  })
})

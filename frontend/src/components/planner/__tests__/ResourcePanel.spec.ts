import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ResourcePanel from '@/components/planner/ResourcePanel.vue'

describe('ResourcePanel', () => {
  it('渲染资源列表', () => {
    const wrapper = mount(ResourcePanel, {
      props: {
        resources: [
          { resourceId: 'LINE-A', groupName: '冲压组', defaultPlannerResource: true },
          { resourceId: 'LINE-C', groupName: '装配组', defaultPlannerResource: false }
        ]
      }
    })

    expect(wrapper.text()).toContain('LINE-A')
    expect(wrapper.text()).toContain('冲压组')
    expect(wrapper.text()).toContain('LINE-C')
    expect(wrapper.text()).toContain('装配组')
    expect(wrapper.text()).toContain('是')
    expect(wrapper.text()).toContain('否')
    expect(wrapper.text()).toContain('2 条资源')
  })

  it('空资源时展示空状态', () => {
    const wrapper = mount(ResourcePanel, {
      props: {
        resources: []
      }
    })

    expect(wrapper.text()).toContain('暂无资源数据')
  })

  it('点击编辑进入编辑模式', async () => {
    const wrapper = mount(ResourcePanel, {
      props: {
        resources: [{ resourceId: 'LINE-A', groupName: '冲压组', defaultPlannerResource: true }]
      }
    })

    await wrapper.find('[data-testid="edit-resource"]').trigger('click')

    expect(wrapper.find('[data-testid="save-edit"]').exists()).toBe(true)
    expect(wrapper.find('.resource-panel__input').exists()).toBe(true)
  })

  it('保存编辑时发出 update-resource 事件', async () => {
    const wrapper = mount(ResourcePanel, {
      props: {
        resources: [{ resourceId: 'LINE-A', groupName: '冲压组', defaultPlannerResource: true }]
      }
    })

    await wrapper.find('[data-testid="edit-resource"]').trigger('click')
    const input = wrapper.find('[data-testid="edit-group-name"]')
    await input.setValue('新冲压组')
    await wrapper.find('[data-testid="save-edit"]').trigger('click')

    expect(wrapper.emitted('update-resource')).toHaveLength(1)
    expect(wrapper.emitted('update-resource')![0]).toEqual([
      'LINE-A',
      { groupName: '新冲压组', defaultPlannerResource: true }
    ])
  })

  it('点击删除时发出 delete-resource 事件', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const wrapper = mount(ResourcePanel, {
      props: {
        resources: [{ resourceId: 'LINE-A', groupName: '冲压组', defaultPlannerResource: true }]
      }
    })

    await wrapper.find('[data-testid="delete-resource"]').trigger('click')
    confirmSpy.mockRestore()

    expect(wrapper.emitted('delete-resource')).toHaveLength(1)
    expect(wrapper.emitted('delete-resource')![0]).toEqual(['LINE-A'])
  })

  it('点击新增资源进入添加模式', async () => {
    const wrapper = mount(ResourcePanel, {
      props: {
        resources: []
      }
    })

    await wrapper.find('[data-testid="add-resource"]').trigger('click')

    expect(wrapper.find('[data-testid="save-add"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="add-resource-id"]').exists()).toBe(true)
  })

  it('保存新增时发出 create-resource 事件', async () => {
    const wrapper = mount(ResourcePanel, {
      props: {
        resources: []
      }
    })

    await wrapper.find('[data-testid="add-resource"]').trigger('click')
    await wrapper.find('[data-testid="add-resource-id"]').setValue('LINE-B')
    await wrapper.find('[data-testid="add-group-name"]').setValue('冲压组')
    await wrapper.find('[data-testid="save-add"]').trigger('click')

    expect(wrapper.emitted('create-resource')).toHaveLength(1)
    expect(wrapper.emitted('create-resource')![0]).toEqual([
      { resourceId: 'LINE-B', groupName: '冲压组', defaultPlannerResource: false }
    ])
  })
})

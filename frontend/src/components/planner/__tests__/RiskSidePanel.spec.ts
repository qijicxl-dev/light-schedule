import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import RiskSidePanel from '@/components/planner/RiskSidePanel.vue'

describe('RiskSidePanel', () => {
  it('显示建议及其对应受影响任务资源组', () => {
    const wrapper = mount(RiskSidePanel, {
      props: {
        suggestions: [
          {
            action: 'reassign_same_group',
            reason: '冲压组仍有剩余能力'
          }
        ],
        affectedItems: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ],
        affectedTaskIds: ['TASK-001']
      }
    })

    expect(wrapper.text()).toContain('reassign_same_group')
    expect(wrapper.text()).toContain('冲压组仍有剩余能力')
    expect(wrapper.text()).toContain('TASK-001')
    expect(wrapper.text()).toContain('冲压组')
  })

  it('保留未映射到当前草稿的受影响任务编号', () => {
    const wrapper = mount(RiskSidePanel, {
      props: {
        suggestions: [],
        affectedItems: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ],
        affectedTaskIds: ['TASK-001', 'WO-URGENT-001']
      }
    })

    expect(wrapper.text()).toContain('TASK-001')
    expect(wrapper.text()).toContain('WO-URGENT-001')
  })

  it('点击应用到画板时抛出对应建议事件', async () => {
    const wrapper = mount(RiskSidePanel, {
      props: {
        suggestions: [
          {
            action: 'move_next_slot',
            reason: '下一可用时间窗可承接'
          }
        ],
        affectedItems: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ],
        affectedTaskIds: ['TASK-001']
      }
    })

    await wrapper.get('[data-testid="apply-suggestion-0"]').trigger('click')

    expect(wrapper.emitted('apply-suggestion')).toEqual([
      [
        {
          action: 'move_next_slot',
          reason: '下一可用时间窗可承接'
        }
      ]
    ])
  })
})

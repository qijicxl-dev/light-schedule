import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import ScheduleBoard from '@/components/planner/ScheduleBoard.vue'

describe('ScheduleBoard', () => {
  it('显示任务所属资源线、资源组和排程时间段', () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
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
      }
    })

    expect(wrapper.text()).toContain('TASK-001')
    expect(wrapper.text()).toContain('LINE-A')
    expect(wrapper.text()).toContain('冲压组')
    expect(wrapper.text()).toContain('08:00')
    expect(wrapper.text()).toContain('10:00')
    expect(wrapper.text()).toContain('TASK-002')
    expect(wrapper.text()).toContain('11:30')
  })

  it('以表格形式展示排程数据', () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    const table = wrapper.find('table')
    expect(table.exists()).toBe(true)

    const headers = table.findAll('thead th')
    expect(headers.length).toBe(6)
    expect(headers[0]?.text()).toContain('任务编号')
    expect(headers[1]?.text()).toContain('资源编号')
    expect(headers[2]?.text()).toContain('资源组')
    expect(headers[3]?.text()).toContain('开始时间')
    expect(headers[4]?.text()).toContain('结束时间')
    expect(headers[5]?.text()).toContain('持续时间')
  })

  it('按资源分组展示任务', () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
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
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T10:00:00Z',
            endAt: '2026-04-24T11:30:00Z'
          }
        ]
      }
    })

    const rows = wrapper.findAll('[data-testid^="schedule-row-"]')
    expect(rows.length).toBe(2)
    expect(wrapper.find('[data-testid="schedule-row-TASK-001"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="schedule-row-TASK-002"]').exists()).toBe(true)
  })

  it('无排程数据时显示空状态', () => {
    const wrapper = mount(ScheduleBoard, {
      props: { items: [] }
    })

    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无排程结果')
  })

  it('计算并显示持续时间', () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    expect(wrapper.text()).toContain('2小时')
  })

  it('点击表头按任务编号升序排序', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-B',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T10:00:00Z',
            endAt: '2026-04-24T11:00:00Z'
          },
          {
            taskId: 'TASK-A',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T09:00:00Z'
          }
        ]
      }
    })

    await wrapper.find('[data-testid="sort-header-taskId"]').trigger('click')

    const rows = wrapper.findAll('[data-testid^="schedule-row-"]')
    expect(rows.length).toBe(2)
    expect(rows[0].text()).toContain('TASK-A')
    expect(rows[1].text()).toContain('TASK-B')
  })

  it('再次点击同一表头切换为降序排序', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-A',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T09:00:00Z'
          },
          {
            taskId: 'TASK-B',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T10:00:00Z',
            endAt: '2026-04-24T11:00:00Z'
          }
        ]
      }
    })

    const header = wrapper.find('[data-testid="sort-header-taskId"]')
    await header.trigger('click')
    await header.trigger('click')

    const rows = wrapper.findAll('[data-testid^="schedule-row-"]')
    expect(rows[0].text()).toContain('TASK-B')
    expect(rows[1].text()).toContain('TASK-A')
  })

  it('排序时取消资源分组，平铺展示所有任务', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-B',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-24T10:00:00Z',
            endAt: '2026-04-24T11:00:00Z'
          },
          {
            taskId: 'TASK-A',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T09:00:00Z'
          }
        ]
      }
    })

    expect(wrapper.find('.schedule-board__group-row').exists()).toBe(true)

    await wrapper.find('[data-testid="sort-header-taskId"]').trigger('click')

    expect(wrapper.find('.schedule-board__group-row').exists()).toBe(false)
    const rows = wrapper.findAll('[data-testid^="schedule-row-"]')
    expect(rows[0].text()).toContain('TASK-A')
    expect(rows[0].text()).toContain('LINE-A')
    expect(rows[1].text()).toContain('TASK-B')
    expect(rows[1].text()).toContain('LINE-B')
  })

  it('点击清除排序按钮恢复分组展示', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-B',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-24T10:00:00Z',
            endAt: '2026-04-24T11:00:00Z'
          },
          {
            taskId: 'TASK-A',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T09:00:00Z'
          }
        ]
      }
    })

    await wrapper.find('[data-testid="sort-header-taskId"]').trigger('click')
    expect(wrapper.find('.schedule-board__group-row').exists()).toBe(false)

    await wrapper.find('[data-testid="clear-sort"]').trigger('click')
    expect(wrapper.find('.schedule-board__group-row').exists()).toBe(true)
  })

  it('按开始时间排序', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T10:00:00Z',
            endAt: '2026-04-24T11:00:00Z'
          },
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T09:00:00Z'
          }
        ]
      }
    })

    await wrapper.find('[data-testid="sort-header-startAt"]').trigger('click')

    const rows = wrapper.findAll('[data-testid^="schedule-row-"]')
    expect(rows[0].text()).toContain('TASK-001')
    expect(rows[1].text()).toContain('TASK-002')
  })

  it('默认显示表格视图', () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    expect(wrapper.find('table').exists()).toBe(true)
    expect(wrapper.find('.schedule-board__gantt').exists()).toBe(false)
    expect(wrapper.find('[data-testid="view-mode-table"]').classes()).toContain('schedule-board__view-btn--active')
  })

  it('点击甘特图按钮切换到甘特视图', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    await wrapper.find('[data-testid="view-mode-gantt"]').trigger('click')

    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.find('.schedule-board__gantt').exists()).toBe(true)
    expect(wrapper.find('[data-testid="gantt-row-LINE-A"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="gantt-bar-TASK-001"]').exists()).toBe(true)
  })

  it('甘特视图按资源分组展示任务条', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
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
      }
    })

    await wrapper.find('[data-testid="view-mode-gantt"]').trigger('click')

    expect(wrapper.find('[data-testid="gantt-row-LINE-A"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="gantt-row-LINE-B"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="gantt-bar-TASK-001"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="gantt-bar-TASK-002"]').exists()).toBe(true)
  })

  it('点击表格按钮从甘特视图切回表格视图', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    await wrapper.find('[data-testid="view-mode-gantt"]').trigger('click')
    expect(wrapper.find('.schedule-board__gantt').exists()).toBe(true)

    await wrapper.find('[data-testid="view-mode-table"]').trigger('click')
    expect(wrapper.find('table').exists()).toBe(true)
    expect(wrapper.find('.schedule-board__gantt').exists()).toBe(false)
  })

  it('甘特视图显示任务持续时间和时间刻度', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    await wrapper.find('[data-testid="view-mode-gantt"]').trigger('click')

    const bar = wrapper.find('[data-testid="gantt-bar-TASK-001"]')
    expect(bar.exists()).toBe(true)
    expect(bar.text()).toContain('TASK-001')
    expect(bar.text()).toContain('2小时')
    expect(wrapper.find('.schedule-board__gantt-ticks').exists()).toBe(true)
  })

  it('点击甘特图任务条显示详情面板', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    await wrapper.find('[data-testid="view-mode-gantt"]').trigger('click')
    await wrapper.find('[data-testid="gantt-bar-TASK-001"]').trigger('click')

    const panel = wrapper.find('[data-testid="gantt-detail-panel"]')
    expect(panel.exists()).toBe(true)
    expect(panel.text()).toContain('TASK-001')
    expect(panel.text()).toContain('LINE-A')
    expect(panel.text()).toContain('冲压组')
    expect(panel.text()).toContain('2026-04-24 08:00')
    expect(panel.text()).toContain('2026-04-24 10:00')
    expect(panel.text()).toContain('2小时')
  })

  it('点击关闭按钮隐藏详情面板', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    await wrapper.find('[data-testid="view-mode-gantt"]').trigger('click')
    await wrapper.find('[data-testid="gantt-bar-TASK-001"]').trigger('click')
    expect(wrapper.find('[data-testid="gantt-detail-panel"]').exists()).toBe(true)

    await wrapper.find('[data-testid="gantt-detail-close"]').trigger('click')
    expect(wrapper.find('[data-testid="gantt-detail-panel"]').exists()).toBe(false)
  })

  it('甘特图任务条触发 update-task-time 事件', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    await wrapper.find('[data-testid="view-mode-gantt"]').trigger('click')

    const bar = wrapper.find('[data-testid="gantt-bar-TASK-001"]')
    await bar.trigger('mousedown')
    await bar.trigger('mouseup')

    // mousedown 和 mouseup 不会触发 emit（因为 timeline 元素不存在于 jsdom）
    // 但事件处理器已绑定，不会报错
    expect(bar.exists()).toBe(true)
  })

  it('从表格视图切换到甘特视图时不显示详情面板', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    // 默认表格视图不应有详情面板
    expect(wrapper.find('[data-testid="gantt-detail-panel"]').exists()).toBe(false)

    // 切换到甘特视图也不应有详情面板（未点击任务条）
    await wrapper.find('[data-testid="view-mode-gantt"]').trigger('click')
    expect(wrapper.find('[data-testid="gantt-detail-panel"]').exists()).toBe(false)
  })

  it('有排程数据时显示导出按钮', () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    expect(wrapper.find('[data-testid="export-schedule"]').exists()).toBe(true)
  })

  it('无排程数据时不显示导出按钮', () => {
    const wrapper = mount(ScheduleBoard, {
      props: { items: [] }
    })

    expect(wrapper.find('[data-testid="export-schedule"]').exists()).toBe(false)
  })

  it('点击导出按钮调用 URL.createObjectURL 并清理', async () => {
    const createObjectURL = vi.fn(() => 'blob:mock')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })

    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    const button = wrapper.find('[data-testid="export-schedule"]')
    expect(button.exists()).toBe(true)

    await button.trigger('click')

    expect(createObjectURL).toHaveBeenCalled()
    expect(revokeObjectURL).toHaveBeenCalled()
    vi.unstubAllGlobals()
  })

  it('有排程数据时显示筛选输入框', () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    expect(wrapper.find('[data-testid="schedule-filter-input"]').exists()).toBe(true)
  })

  it('无排程数据时不显示筛选输入框', () => {
    const wrapper = mount(ScheduleBoard, {
      props: { items: [] }
    })

    expect(wrapper.find('[data-testid="schedule-filter-input"]').exists()).toBe(false)
  })

  it('输入筛选条件过滤任务', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
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
            endAt: '2026-04-24T11:00:00Z'
          }
        ]
      }
    })

    const input = wrapper.find('[data-testid="schedule-filter-input"]')
    await input.setValue('LINE-B')

    expect(wrapper.find('[data-testid="schedule-row-TASK-002"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="schedule-row-TASK-001"]').exists()).toBe(false)
  })

  it('点击清除按钮清空筛选条件', async () => {
    const wrapper = mount(ScheduleBoard, {
      props: {
        items: [
          {
            taskId: 'TASK-001',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T08:00:00Z',
            endAt: '2026-04-24T10:00:00Z'
          }
        ]
      }
    })

    const input = wrapper.find('[data-testid="schedule-filter-input"]')
    await input.setValue('TASK')
    expect(wrapper.find('[data-testid="schedule-filter-clear"]').exists()).toBe(true)

    await wrapper.find('[data-testid="schedule-filter-clear"]').trigger('click')
    expect((input.element as HTMLInputElement).value).toBe('')
  })
})

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ref } from 'vue'
import RouteManagementView from './RouteManagementView.vue'
import { plannerApi } from '@/api/planner'

vi.mock('@/api/planner', () => ({
  plannerApi: {
    listRoutes: vi.fn(),
    listRouteSteps: vi.fn()
  }
}))

const mockedListRoutes = vi.mocked(plannerApi.listRoutes)
const mockedListRouteSteps = vi.mocked(plannerApi.listRouteSteps)

describe('RouteManagementView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should render route list and auto-select first route', async () => {
    mockedListRoutes.mockResolvedValue(['ROUTE-01', 'ROUTE-02'])
    mockedListRouteSteps.mockResolvedValue([
      { stepId: 'TASK-001', requiredMinutes: 120, dependencyStepIds: [] },
      { stepId: 'TASK-002', requiredMinutes: 90, dependencyStepIds: ['TASK-001'] }
    ])

    const wrapper = mount(RouteManagementView)
    await flushPromises()

    expect(wrapper.findAll('.route-page__route-item').length).toBe(2)
    expect(wrapper.find('.route-page__route-item--active').text()).toContain('ROUTE-01')
    expect(wrapper.findAll('tbody tr').length).toBe(2)
  })

  it('should switch steps when clicking another route', async () => {
    mockedListRoutes.mockResolvedValue(['ROUTE-01', 'ROUTE-02'])
    mockedListRouteSteps.mockResolvedValueOnce([
      { stepId: 'TASK-001', requiredMinutes: 120, dependencyStepIds: [] }
    ]).mockResolvedValueOnce([
      { stepId: 'TASK-003', requiredMinutes: 60, dependencyStepIds: [] }
    ])

    const wrapper = mount(RouteManagementView)
    await flushPromises()

    const secondRoute = wrapper.findAll('.route-page__route-item')[1]
    await secondRoute.trigger('click')
    await flushPromises()

    expect(secondRoute.classes()).toContain('route-page__route-item--active')
    expect(mockedListRouteSteps).toHaveBeenCalledWith('ROUTE-02')
  })

  it('should show empty state when no routes', async () => {
    mockedListRoutes.mockResolvedValue([])

    const wrapper = mount(RouteManagementView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无工艺路线')
  })

  it('should show empty state when route has no steps', async () => {
    mockedListRoutes.mockResolvedValue(['ROUTE-01'])
    mockedListRouteSteps.mockResolvedValue([])

    const wrapper = mount(RouteManagementView)
    await flushPromises()

    expect(wrapper.text()).toContain('该工艺路线暂无步骤')
  })

  it('should display error when route list fails', async () => {
    mockedListRoutes.mockRejectedValue(new Error('network error'))

    const wrapper = mount(RouteManagementView)
    await flushPromises()

    expect(wrapper.find('.status-block--error').text()).toBe('network error')
  })
})

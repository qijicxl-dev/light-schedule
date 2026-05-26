import { createRouter, createWebHistory } from 'vue-router'
import PlannerWorkbenchView from '@/views/PlannerWorkbenchView.vue'
import BossDashboardView from '@/views/BossDashboardView.vue'
import CapacityAnalysisView from '@/views/CapacityAnalysisView.vue'
import RouteManagementView from '@/views/RouteManagementView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/planner'
    },
    {
      path: '/planner',
      component: PlannerWorkbenchView
    },
    {
      path: '/dashboard',
      component: BossDashboardView
    },
    {
      path: '/capacity-analysis',
      component: CapacityAnalysisView
    },
    {
      path: '/routes',
      component: RouteManagementView
    }
  ]
})

export default router

import { test, expect } from '@playwright/test'

test('老板驾驶舱显示负荷总览', async ({ page }) => {
  await page.route('**/api/dashboard/overview', async (route) => {
    await route.fulfill({
      json: {
        capacitySummary: {
          status: 'tight',
          loadRate: 1
        },
        workOrderStats: {
          total: 12,
          urgentCount: 3,
          riskDistribution: { low: 8, medium: 2, high: 2 }
        },
        resourceStats: {
          total: 5,
          defaultPlannerCount: 3
        }
      }
    })
  })

  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: '老板驾驶舱' })).toBeVisible()
  await expect(page.getByText('资源负荷总览')).toBeVisible()
  await expect(page.getByText('tight', { exact: true })).toBeVisible()
  await expect(page.getByText('100%', { exact: true })).toBeVisible()
})

test('老板驾驶舱在接口失败时显示可读错误', async ({ page }) => {
  await page.route('**/api/dashboard/overview', async (route) => {
    await route.fulfill({
      status: 500,
      json: {
        error: 'internal'
      }
    })
  })

  await page.goto('/dashboard')

  await expect(page.getByRole('heading', { name: '老板驾驶舱' })).toBeVisible()
  await expect(page.getByText('加载驾驶舱概览失败', { exact: true })).toBeVisible()
})

test('老板驾驶舱可以通过后端接口看到负荷摘要', async ({ page }) => {
  await page.route('**/api/dashboard/overview', async (route) => {
    await route.fulfill({
      json: {
        capacitySummary: {
          status: 'feasible',
          loadRate: 0.44
        },
        workOrderStats: {
          total: 12,
          urgentCount: 3,
          riskDistribution: { low: 8, medium: 2, high: 2 }
        },
        resourceStats: {
          total: 5,
          defaultPlannerCount: 3
        }
      }
    })
  })

  await page.goto('/dashboard')

  await expect(page.getByRole('heading', { name: '老板驾驶舱' })).toBeVisible()
  await expect(page.getByText('资源负荷总览')).toBeVisible()
  await expect(page.getByText('feasible', { exact: true })).toBeVisible()
  await expect(page.getByText('44%', { exact: true })).toBeVisible()
})

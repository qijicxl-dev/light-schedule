import { test, expect } from '@playwright/test'

test('能力分析页显示趋势、同组差异和高负荷时段', async ({ page }) => {
  await page.route('**/api/capacity-analysis/overview', async (route) => {
    await route.fulfill({
      json: {
        trends: [
          {
            resourceId: 'LINE-A',
            bucketLabel: '2026-04-25 08:00',
            status: 'tight',
            loadRate: 0.95
          }
        ],
        groupDiffs: [
          {
            groupName: '冲压组',
            gapRate: 0.15
          }
        ],
        peakPeriods: [
          {
            bucketLabel: '2026-04-25 10:00',
            status: 'overloaded',
            loadRate: 1.02
          }
        ]
      }
    })
  })

  await page.goto('/capacity-analysis')
  await expect(page.getByRole('heading', { name: '能力分析' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '资源负荷趋势' })).toBeVisible()
  await expect(page.getByText('LINE-A', { exact: true })).toBeVisible()
  await expect(page.getByText('95%', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '同组差异' })).toBeVisible()
  await expect(page.getByText('冲压组', { exact: true })).toBeVisible()
  await expect(page.getByText('15%', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '高负荷时段' })).toBeVisible()
  await expect(page.locator('.capacity-page__item-title').filter({ hasText: '2026-04-25 10:00' })).toBeVisible()
  await expect(page.getByText('102%', { exact: true })).toBeVisible()
})

test('能力分析页在接口失败时显示可读错误', async ({ page }) => {
  await page.route('**/api/capacity-analysis/overview', async (route) => {
    await route.fulfill({
      status: 500,
      json: {
        error: 'internal'
      }
    })
  })

  await page.goto('/capacity-analysis')

  await expect(page.getByRole('heading', { name: '能力分析' })).toBeVisible()
  await expect(page.getByText('加载能力分析失败')).toBeVisible()
})

test('能力分析页可以通过后端接口看到最小分析结果', async ({ page }) => {
  await page.route('**/api/capacity-analysis/overview', async (route) => {
    await route.fulfill({
      json: {
        trends: [
          {
            resourceId: 'LINE-A',
            bucketLabel: '2026-04-24 08:00',
            status: 'feasible',
            loadRate: 0.44
          }
        ],
        groupDiffs: [
          {
            groupName: '冲压组',
            gapRate: 0.44
          }
        ],
        peakPeriods: [
          {
            bucketLabel: '2026-04-24 08:00',
            status: 'tight',
            loadRate: 1.0
          }
        ]
      }
    })
  })

  await page.goto('/capacity-analysis')

  const panels = page.locator('.capacity-page__panel')
  const trendSection = panels.nth(0)
  const groupSection = panels.nth(1)
  const peakSection = panels.nth(2)

  await expect(page.getByRole('heading', { name: '能力分析' })).toBeVisible()
  await expect(trendSection.getByText('LINE-A', { exact: true })).toBeVisible()
  await expect(trendSection.locator('.capacity-page__item-meta').filter({ hasText: '2026-04-24 08:00' })).toBeVisible()
  await expect(trendSection.locator('.capacity-page__metric').filter({ hasText: '44%' })).toBeVisible()
  await expect(groupSection.getByText('冲压组', { exact: true })).toBeVisible()
  await expect(groupSection.getByText('44%', { exact: true })).toBeVisible()
  await expect(peakSection.locator('.capacity-page__item-title').filter({ hasText: '2026-04-24 08:00' })).toBeVisible()
  await expect(peakSection.getByText('100%', { exact: true })).toBeVisible()
})

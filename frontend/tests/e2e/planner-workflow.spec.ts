import { test, expect } from '@playwright/test'

test('计划员可以通过页签切换工作台面板', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.goto('/planner')
  await expect(page.getByRole('tab', { name: '待排任务' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('tab', { name: '待排任务' })).toHaveAttribute('aria-controls', 'planner-panel-taskPool')
  await expect(page.getByRole('button', { name: '插入急单' })).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()

  await page.getByRole('tab', { name: '排程画板' }).click()
  await expect(page.getByRole('tab', { name: '排程画板' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('button', { name: '插入急单' })).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '待排任务' })).toHaveAttribute('aria-selected', 'false')

  await page.getByRole('tab', { name: '风险与建议' }).click()
  await expect(page.getByRole('tab', { name: '风险与建议' })).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('button', { name: '插入急单' })).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()
})

test('计划员在回写前校验未通过后可重新打开同一结果', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        auditId: 'audit-validation-failed',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-validation-failed', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-validation-failed',
        draftId: 'draft-1',
        status: 'validation_failed',
        writebackStatus: 'TERMINAL_FAILED',
        message: '字段映射不合法',
        retryable: false,
        attemptCount: 3,
        maxAttempts: 3,
        nextRetryAt: null
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()

  const validationFailedDialog = page.getByRole('dialog', { name: '未通过回写前校验' })
  await expect(validationFailedDialog.locator('.publish-dialog__title-anchor')).toHaveText('未通过回写前校验')
  await expect(validationFailedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本停在回写前校验环节')
  await expect(validationFailedDialog.getByText('校验状态：未通过')).toBeVisible()
  await expect(validationFailedDialog.getByText('系统已记录本次回写前校验留痕')).toBeVisible()
  await expect(validationFailedDialog.getByRole('button', { name: '重新提交结果版本' })).toBeVisible()

  await validationFailedDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toBeVisible()

  await page.getByRole('button', { name: '查看回写状态' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '未通过回写前校验' })
  await expect(reopenedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本停在回写前校验环节')
  await expect(reopenedDialog.getByText('回写状态：未执行回写')).toBeVisible()
})

test('计划员在回写请求已提交后可重新打开同一结果', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  let releasePublishRequest = () => {}
  const publishResponseReady = new Promise<void>((resolve) => {
    releasePublishRequest = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await publishResponseReady
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        auditId: 'audit-pending',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-pending', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-pending',
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
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()
  await expect(publishDialog.getByText('加载中')).toBeVisible()

  releasePublishRequest()
  const pendingDialog = page.getByRole('dialog', { name: '回写请求已提交' })
  await expect(pendingDialog.locator('.publish-dialog__title-anchor')).toHaveText('回写请求已提交')
  await expect(pendingDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本的回写请求已提交')
  await expect(pendingDialog.getByText('回写状态：回写请求已提交')).toBeVisible()
  await expect(pendingDialog.getByText('系统已记录本次回写请求留痕')).toBeVisible()
  await expect(pendingDialog.getByRole('button', { name: '回写请求已提交' })).toHaveCount(0)

  await pendingDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toBeVisible()

  await page.getByRole('button', { name: '查看回写状态' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '回写请求已提交' })
  await expect(reopenedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本的回写请求已提交')
  await expect(reopenedDialog.getByText('回写状态：回写请求已提交')).toBeVisible()
})

test('计划员在回写可重试失败后可重新打开同一结果', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        auditId: 'audit-retryable-failed',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-retryable-failed', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-retryable-failed',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'RETRYABLE_FAILED',
        message: '金蝶网关超时',
        retryable: true,
        attemptCount: 2,
        maxAttempts: 3,
        nextRetryAt: '2026-04-24T10:30:00Z'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()

  const retryableFailedDialog = page.getByRole('dialog', { name: '回写重试中' })
  await expect(retryableFailedDialog.locator('.publish-dialog__title-anchor')).toHaveText('回写重试中')
  await expect(retryableFailedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本回写重试中')
  await expect(retryableFailedDialog.getByText('回写状态：回写重试中')).toBeVisible()
  await expect(retryableFailedDialog.getByText('当前结果版本将在后台继续重试回写')).toBeVisible()
  await expect(retryableFailedDialog.getByText('下一次重试：2026-04-24T10:30:00Z')).toBeVisible()
  await expect(retryableFailedDialog.getByRole('button', { name: '回写请求已提交' })).toHaveCount(0)

  await retryableFailedDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toBeVisible()

  await page.getByRole('button', { name: '查看回写状态' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '回写重试中' })
  await expect(reopenedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本回写重试中')
  await expect(reopenedDialog.getByText('回写状态：回写重试中')).toBeVisible()
})

test('计划员可以把风险建议应用到排程画板', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        items: [
          {
            taskId: 'TASK-KEEP',
            resourceId: 'LINE-A',
            resourceGroupName: '冲压组',
            startAt: '2026-04-24T06:00:00Z',
            endAt: '2026-04-24T08:00:00Z'
          },
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
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-URGENT-001',
        affectedTaskIds: ['TASK-001'],
        suggestions: [
          {
            action: 'move_next_slot',
            reason: '下一可用时间窗可承接'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('move_next_slot')).toBeVisible()
  await urgentDialog.getByRole('button', { name: '关闭' }).click()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })
  await expect(riskPanel.getByText('move_next_slot')).toBeVisible()
  await riskPanel.getByRole('button', { name: '应用到画板' }).click()

  await page.getByRole('tab', { name: '排程画板' }).click()
  const scheduleBoard = page.locator('.schedule-board')
  await expect(scheduleBoard).toContainText('WO-URGENT-001')
  await expect(scheduleBoard).toContainText('2026-04-24 06:00')
  await expect(scheduleBoard).toContainText('2026-04-24 06:30')
  await expect(scheduleBoard).toContainText('TASK-001')
  await expect(scheduleBoard).toContainText('2026-04-24 06:30')
  await expect(scheduleBoard).toContainText('2026-04-24 08:30')
  await page.getByRole('tab', { name: '风险与建议' }).click()
  await expect(riskPanel.getByText('move_next_slot')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-001', { exact: true })).toHaveCount(0)
})

test('计划员可以在急单弹窗里直接把建议应用到排程画板', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-001', 'WO-001', 'TASK-002'],
        suggestions: [
          {
            action: 'reassign_same_group',
            reason: '冲压组仍有剩余能力'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await urgentDialog.getByTestId('apply-urgent-suggestion-0').click()

  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('tab', { name: '排程画板' }).click()
  const scheduleBoard = page.locator('.schedule-board')
  await expect(scheduleBoard).toContainText('WO-001')
  await expect(scheduleBoard).toContainText('2026-04-24 08:00')
  await expect(scheduleBoard).toContainText('2026-04-24 08:30')
  await expect(scheduleBoard).toContainText('TASK-001')
  await expect(scheduleBoard).toContainText('2026-04-24 08:30')
  await expect(scheduleBoard).toContainText('2026-04-24 10:30')
})

test('计划员在回写成功后不会继续看到上一轮急单建议', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'URGENT-001',
        affectedTaskIds: ['TASK-001'],
        suggestions: [
          {
            action: '延后 TASK-001',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        auditId: 'audit-1',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-1', async (route) => {
    await route.fulfill({
      json: {
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
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-001')).toBeVisible()
  await urgentDialog.getByRole('button', { name: '关闭' }).click()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  await expect(page.locator('aside').filter({ has: page.getByText('风险与建议') })).toContainText('延后 TASK-001')

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()

  const publishResultDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(publishResultDialog.getByText('结果版本：draft-1', { exact: true })).toBeVisible()
  await expect(publishResultDialog.locator('.publish-dialog__title-anchor')).toHaveText('回写已进入队列')
  await expect(publishResultDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')
  await publishResultDialog.getByRole('button', { name: '关闭' }).click()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })
  await expect(riskPanel.getByText('延后 TASK-001')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-001', { exact: true })).toHaveCount(0)
})

test('计划员离开并返回工作台后不会保留上一轮回写结果状态', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-3',
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
  })

  await page.route('**/api/load-summary', async (route) => {
    await route.fulfill({
      json: {
        overdueCount: 1,
        delayedCount: 2,
        idleCapacityCount: 3,
        exceptionCount: 4
      }
    })
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-3',
        draftId: 'draft-3',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-3', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-3',
        draftId: 'draft-3',
        status: 'validated',
        writebackStatus: 'submitted',
        message: 'queued',
        retryable: false,
        attemptCount: 1,
        maxAttempts: 3,
        nextRetryAt: null
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写已进入队列' }).getByText('结果版本：draft-3', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toBeVisible()

  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: '老板驾驶舱' })).toBeVisible()

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('当前草稿：draft-3')).toBeVisible()
  await expect(publishDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(publishDialog.getByText('结果版本：draft-3')).toHaveCount(0)
})

test('计划员在回写阻塞后离开并返回工作台时不会保留上一轮阻塞状态', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-4',
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
  })

  await page.route('**/api/load-summary', async (route) => {
    await route.fulfill({
      json: {
        overdueCount: 1,
        delayedCount: 2,
        idleCapacityCount: 3,
        exceptionCount: 4
      }
    })
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      status: 409,
      json: {
        error: 'blocking'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('存在阻塞项，无法回写')).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toBeVisible()

  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: '老板驾驶舱' })).toBeVisible()

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('当前草稿：draft-4')).toBeVisible()
  await expect(publishDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(publishDialog.getByText('存在阻塞项，无法回写')).toHaveCount(0)
})

test('计划员在回写提交中离开并返回工作台时不会保留上一轮加载状态', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-5',
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
  })

  await page.route('**/api/load-summary', async (route) => {
    await route.fulfill({
      json: {
        overdueCount: 1,
        delayedCount: 2,
        idleCapacityCount: 3,
        exceptionCount: 4
      }
    })
  })

  let releasePublishRequest = () => {}
  const publishResponseReady = new Promise<void>((resolve) => {
    releasePublishRequest = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await publishResponseReady
    await route.fulfill({
      json: {
        draftId: 'draft-5',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('加载中')).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toBeVisible()

  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: '老板驾驶舱' })).toBeVisible()

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('当前草稿：draft-5')).toBeVisible()
  await expect(publishDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(publishDialog.getByText('加载中')).toHaveCount(0)

  releasePublishRequest()
})

test('计划员在旧回写请求返回后不会看到过期结果重新写回当前页面', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-6',
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
  })

  await page.route('**/api/load-summary', async (route) => {
    await route.fulfill({
      json: {
        overdueCount: 1,
        delayedCount: 2,
        idleCapacityCount: 3,
        exceptionCount: 4
      }
    })
  })

  let releasePublishRequest = () => {}
  let publishResolved = () => {}
  const publishResponseReady = new Promise<void>((resolve) => {
    releasePublishRequest = resolve
  })
  const publishFinished = new Promise<void>((resolve) => {
    publishResolved = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await publishResponseReady
    await route.fulfill({
      json: {
        draftId: 'draft-6',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
    publishResolved()
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('加载中')).toBeVisible()

  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: '老板驾驶舱' })).toBeVisible()

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()

  releasePublishRequest()
  await publishFinished

  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(publishDialog.getByText('结果版本：draft-6')).toHaveCount(0)
})


test('计划员在旧回写阻塞结果返回后不会看到过期错误重新写回当前页面', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-7',
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
  })

  await page.route('**/api/load-summary', async (route) => {
    await route.fulfill({
      json: {
        overdueCount: 1,
        delayedCount: 2,
        idleCapacityCount: 3,
        exceptionCount: 4
      }
    })
  })

  let releasePublishRequest = () => {}
  let publishResolved = () => {}
  const publishResponseReady = new Promise<void>((resolve) => {
    releasePublishRequest = resolve
  })
  const publishFinished = new Promise<void>((resolve) => {
    publishResolved = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await publishResponseReady
    await route.fulfill({
      status: 409,
      json: {
        error: 'blocking'
      }
    })
    publishResolved()
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('加载中')).toBeVisible()

  await page.goto('/dashboard')
  await expect(page.getByRole('heading', { name: '老板驾驶舱' })).toBeVisible()

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()

  releasePublishRequest()
  await publishFinished

  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(publishDialog.getByText('存在阻塞项，无法回写')).toHaveCount(0)
})

test('计划员在非阻塞型回写失败后可通过查看回写状态重新打开同一错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-8',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      status: 500,
      json: {
        error: 'server_error'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  let publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()
  await expect(publishDialog.getByText('发布排程草稿失败')).toBeVisible()

  await publishDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toBeVisible()

  await page.getByRole('button', { name: '查看回写状态' }).click()
  publishDialog = page.getByRole('dialog', { name: '回写确认' })

  await expect(publishDialog.getByText('当前草稿：draft-8')).toBeVisible()
  await expect(publishDialog.getByText('发布排程草稿失败')).toBeVisible()
  await expect(publishDialog.getByText('暂无回写结果')).toHaveCount(0)
})

test('计划员在非阻塞型回写失败时看到可读错误提示', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-8',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      status: 500,
      json: {
        error: 'server_error'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()

  await expect(publishDialog.getByText('发布排程草稿失败')).toBeVisible()
  await expect(publishDialog.getByText('HTTP 500')).toHaveCount(0)
})



test('计划员在回写阻塞时看到可读错误提示', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
            startAt: '2026-04-24T09:30:00Z',
            endAt: '2026-04-24T11:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      status: 409,
      json: {
        error: 'blocking'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()
  await expect(publishDialog.getByText('存在阻塞项，无法回写')).toBeVisible()
})


test('计划员重新打开回写确认弹窗时仍能看到当前草稿标识', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      status: 409,
      json: {
        error: 'blocking'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  let publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()
  await expect(publishDialog.getByText('存在阻塞项，无法回写')).toBeVisible()

  await publishDialog.getByRole('button', { name: '关闭' }).click()
  await page.getByRole('button', { name: '查看回写状态' }).click()
  publishDialog = page.getByRole('dialog', { name: '回写确认' })

  await expect(publishDialog.getByText('当前草稿：draft-1')).toBeVisible()
  await expect(publishDialog.getByText('存在阻塞项，无法回写')).toBeVisible()
  await expect(publishDialog.getByText('暂无回写结果')).toHaveCount(0)
})

test('计划员在回写阻塞后可通过查看回写状态重新打开同一阻塞信息', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      status: 409,
      json: {
        error: 'blocking'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  let publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()
  await expect(publishDialog.getByText('存在阻塞项，无法回写')).toBeVisible()

  await publishDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toBeVisible()

  await page.getByRole('button', { name: '查看回写状态' }).click()
  publishDialog = page.getByRole('dialog', { name: '回写确认' })

  await expect(publishDialog.getByText('当前草稿：draft-1')).toBeVisible()
  await expect(publishDialog.getByText('存在阻塞项，无法回写')).toBeVisible()
  await expect(publishDialog.getByText('暂无回写结果')).toHaveCount(0)
})

test('计划员在回写阻塞后整页重进时不会保留上一轮阻塞状态', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      status: 409,
      json: {
        error: 'blocking'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('存在阻塞项，无法回写')).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toBeVisible()

  await page.reload()
  await expect(page.getByText('待排任务池')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(reopenedDialog.getByText('当前草稿：draft-2')).toBeVisible()
  await expect(reopenedDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(reopenedDialog.getByText('存在阻塞项，无法回写')).toHaveCount(0)
})

test('计划员在回写阻塞后可直接重试并看到成功结果', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  let publishAttempts = 0
  await page.route('**/api/writeback/publish', async (route) => {
    publishAttempts += 1

    if (publishAttempts === 1) {
      await route.fulfill({
        status: 409,
        json: {
          error: 'blocking'
        }
      })
      return
    }

    await route.fulfill({
      json: {
        auditId: 'audit-retry-success',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-retry-success', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-retry-success',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'submitted',
        message: 'queued',
        retryable: false,
        attemptCount: 1,
        maxAttempts: 3,
        nextRetryAt: null
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()

  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.locator('.publish-dialog__title-anchor')).toHaveText('回写前校验')
  await publishDialog.getByTestId('confirm-publish').click()
  await expect(publishDialog.getByText('存在阻塞项，无法回写')).toBeVisible()

  await publishDialog.getByTestId('confirm-publish').click()
  const publishResultDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(publishResultDialog.locator('.publish-dialog__title-anchor')).toHaveText('回写已进入队列')
  await expect(publishResultDialog.locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-1')
  await expect(publishResultDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')
  await expect(publishResultDialog.getByText('存在阻塞项，无法回写')).toHaveCount(0)
})


test('计划员在旧的非阻塞失败返回后不会看到错误覆盖新一次成功结果', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  let publishAttempts = 0
  let releaseFirstPublishRequest = () => {}
  const firstPublishResponseReady = new Promise<void>((resolve) => {
    releaseFirstPublishRequest = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    publishAttempts += 1

    if (publishAttempts === 1) {
      await firstPublishResponseReady
      await route.fulfill({
        status: 500,
        json: {
          error: 'server_error'
        }
      })
      return
    }

    await route.fulfill({
      json: {
        auditId: 'audit-second-success',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-second-success', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-second-success',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'submitted',
        message: 'queued',
        retryable: false,
        attemptCount: 1,
        maxAttempts: 3,
        nextRetryAt: null
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('当前草稿：draft-1')).toBeVisible()

  await page.evaluate(async () => {
    const store = (window as Window & { __scheduleDraftStore: { loadPlannerData: () => Promise<void>; publishPlannerDraft: () => Promise<void> } }).__scheduleDraftStore
    const firstPublish = store.publishPlannerDraft()
    const secondPublish = store.publishPlannerDraft()
    ;(window as Window & { __firstPublishPromise?: Promise<void> }).__firstPublishPromise = firstPublish
    await secondPublish
  })

  let publishResultDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(publishResultDialog.locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-1')
  await expect(publishResultDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')

  releaseFirstPublishRequest()
  await page.evaluate(async () => {
    await (window as Window & { __firstPublishPromise?: Promise<void> }).__firstPublishPromise
  })

  publishResultDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(publishResultDialog.locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-1')
  await expect(publishResultDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')
  await expect(publishResultDialog.getByText('发布排程草稿失败')).toHaveCount(0)
})

test('计划员在旧的成功结果返回后不会看到结果覆盖新一次非阻塞失败', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  let publishAttempts = 0
  let releaseFirstPublishRequest = () => {}
  const firstPublishResponseReady = new Promise<void>((resolve) => {
    releaseFirstPublishRequest = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    publishAttempts += 1

    if (publishAttempts === 1) {
      await firstPublishResponseReady
      await route.fulfill({
        json: {
          draftId: 'draft-1',
          status: 'validated',
          writebackStatus: 'pending'
        }
      })
      return
    }

    await route.fulfill({
      status: 500,
      json: {
        error: 'server_error'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('当前草稿：draft-1')).toBeVisible()

  await page.evaluate(async () => {
    const store = (window as Window & { __scheduleDraftStore: { loadPlannerData: () => Promise<void>; publishPlannerDraft: () => Promise<void> } }).__scheduleDraftStore
    const firstPublish = store.publishPlannerDraft()
    const secondPublish = store.publishPlannerDraft()
    ;(window as Window & { __firstPublishPromise?: Promise<void> }).__firstPublishPromise = firstPublish
    await secondPublish
  })

  let publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('发布排程草稿失败')).toBeVisible()
  await expect(publishDialog.getByText('结果版本：draft-1')).toHaveCount(0)

  releaseFirstPublishRequest()
  await page.evaluate(async () => {
    await (window as Window & { __firstPublishPromise?: Promise<void> }).__firstPublishPromise
  })

  publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('发布排程草稿失败')).toBeVisible()
  await expect(publishDialog.getByText('结果版本：draft-1')).toHaveCount(0)
})

test('计划员在页面内重新加载工作台数据后不会被旧回写成功结果重新污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releasePublishRequest = () => {}
  const publishResponseReady = new Promise<void>((resolve) => {
    releasePublishRequest = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await publishResponseReady
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('加载中')).toBeVisible()

  await page.evaluate(async () => {
    const store = (window as Window & { __scheduleDraftStore: { loadPlannerData: () => Promise<void>; publishPlannerDraft: () => Promise<void> } }).__scheduleDraftStore
    await store.loadPlannerData()
  })

  await expect(page.getByRole('button', { name: '确认回写' }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  releasePublishRequest()
  await page.waitForTimeout(50)

  await expect(page.getByRole('button', { name: '确认回写' }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).first().click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('当前草稿：draft-2')).toBeVisible()
  await expect(publishDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(publishDialog.getByText('结果版本：draft-1')).toHaveCount(0)
})

test('计划员在页面内重新加载工作台数据后不会被旧回写阻塞结果重新污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releasePublishRequest = () => {}
  const publishResponseReady = new Promise<void>((resolve) => {
    releasePublishRequest = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await publishResponseReady
    await route.fulfill({
      status: 409,
      json: {
        error: 'blocking'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('加载中')).toBeVisible()

  await page.evaluate(async () => {
    const store = (window as Window & { __scheduleDraftStore: { loadPlannerData: () => Promise<void>; publishPlannerDraft: () => Promise<void> } }).__scheduleDraftStore
    await store.loadPlannerData()
  })

  await expect(page.getByRole('button', { name: '确认回写' }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  releasePublishRequest()
  await page.waitForTimeout(50)

  await expect(page.getByRole('button', { name: '确认回写' }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).first().click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('当前草稿：draft-2')).toBeVisible()
  await expect(publishDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(publishDialog.getByText('存在阻塞项，无法回写')).toHaveCount(0)
})

test('计划员在页面内重新加载工作台数据后不会被旧回写非阻塞错误重新污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releasePublishRequest = () => {}
  const publishResponseReady = new Promise<void>((resolve) => {
    releasePublishRequest = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await publishResponseReady
    await route.fulfill({
      status: 500,
      json: {
        error: 'server_error'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('加载中')).toBeVisible()

  await page.evaluate(async () => {
    const store = (window as Window & { __scheduleDraftStore: { loadPlannerData: () => Promise<void>; publishPlannerDraft: () => Promise<void> } }).__scheduleDraftStore
    await store.loadPlannerData()
  })

  await expect(page.getByRole('button', { name: '确认回写' }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  releasePublishRequest()
  await page.waitForTimeout(50)

  await expect(page.getByRole('button', { name: '确认回写' }).first()).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).first().click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('当前草稿：draft-2')).toBeVisible()
  await expect(publishDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(publishDialog.getByText('发布排程草稿失败')).toHaveCount(0)
})

test('计划员在页面内重新加载工作台数据后不会被旧急单建议重新污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  await page.getByRole('dialog', { name: '急单插入' }).getByTestId('submit-urgent-replan').click()
  await expect(page.getByRole('dialog', { name: '急单插入' }).getByText('加载中')).toBeVisible()

  await page.evaluate(async () => {
    const store = (window as Window & { __scheduleDraftStore: { loadPlannerData: () => Promise<void>; publishPlannerDraft: () => Promise<void> } }).__scheduleDraftStore
    await store.loadPlannerData()
  })

  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)
  await page.getByRole('tab', { name: '风险与建议' }).click()
  await expect(page.locator('aside').filter({ has: page.getByText('风险与建议') }).getByText('延后 TASK-002')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)
  await expect(page.locator('aside').filter({ has: page.getByText('风险与建议') }).getByText('延后 TASK-002')).toHaveCount(0)
})

test('计划员在急单加载中修改插单资源后不会看到旧在途建议回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('加载中')).toBeVisible()

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('加载中')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
})

test('计划员在急单加载中修改插单时间窗后不会看到旧在途建议回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('加载中')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('加载中')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
})

test('计划员关闭并重开急单弹窗后不会被旧的重排结果重新污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('加载中')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('加载中')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(page.locator('aside').filter({ has: page.getByText('风险与建议') }).getByText('延后 TASK-002')).toHaveCount(0)
})

test('计划员在急单加载中修改插单资源后不会看到旧在途失败回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'HTTP 500' })
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('加载中')).toBeVisible()

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('加载中')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
})

test('计划员在急单加载中修改插单时间窗后不会看到旧在途失败回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'HTTP 500' })
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('加载中')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('加载中')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
})

test('计划员关闭并重开急单弹窗后不会被旧的重排失败重新污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      status: 500,
      json: {
        error: 'server_error'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('加载中')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('加载中')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('加载急单重排建议失败')).toHaveCount(0)
})

test('计划员关闭并重开急单弹窗后会清空上一轮已完成建议', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(urgentDialog.getByText('TASK-002', { exact: true })).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(urgentDialog.getByText('TASK-002')).toHaveCount(0)
})

test('计划员关闭并重开急单弹窗后风险侧栏会清空上一轮已完成建议', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' }).getByText('暂无重排建议')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
})

test('计划员在急单本地校验失败时会清空上一轮风险侧栏建议', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
})

test('计划员在急单已有建议后再次提交时会先清空风险侧栏旧建议再进入加载态', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let urgentRequestCount = 0
  let releaseSecondUrgentRequest = () => {}
  const secondUrgentResponseReady = new Promise<void>((resolve) => {
    releaseSecondUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        json: {
          urgentTaskId: 'WO-001',
          affectedTaskIds: ['TASK-002'],
          suggestions: [
            {
              action: '延后 TASK-002',
              reason: '急单优先'
            }
          ]
        }
      })
      return
    }

    await secondUrgentResponseReady
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '改派 TASK-002',
            reason: '二次重排'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T09:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('加载中')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)

  releaseSecondUrgentRequest()
  await expect(urgentDialog.getByText('改派 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('改派 TASK-002')).toBeVisible()
})

test('计划员在急单第一次成功后第二次失败时不会回退到第一轮建议', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let urgentRequestCount = 0

  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        json: {
          urgentTaskId: 'WO-001',
          affectedTaskIds: ['TASK-002'],
          suggestions: [
            {
              action: '延后 TASK-002',
              reason: '急单优先'
            }
          ]
        }
      })
      return
    }

    await route.fulfill({
      status: 500,
      json: {
        error: 'server_error'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T09:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('HTTP 500')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(urgentDialog.getByText('TASK-002', { exact: true })).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
})

test('计划员在急单第二次失败后第三次重试成功时不会被旧失败态覆盖', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let urgentRequestCount = 0

  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        json: {
          urgentTaskId: 'WO-001',
          affectedTaskIds: ['TASK-002'],
          suggestions: [
            {
              action: '延后 TASK-002',
              reason: '急单优先'
            }
          ]
        }
      })
      return
    }

    if (urgentRequestCount === 2) {
      await route.fulfill({
        status: 500,
        json: {
          error: 'server_error'
        }
      })
      return
    }

    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '改派 TASK-002',
            reason: '三次重试成功'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })
  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })

  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T09:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('HTTP 500')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T10:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T10:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('改派 TASK-002')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
  await expect(riskPanel.getByText('改派 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
})

test('计划员在急单第二次失败后关闭并重开再提交成功时不会被旧失败态污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let urgentRequestCount = 0

  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        json: {
          urgentTaskId: 'WO-001',
          affectedTaskIds: ['TASK-002'],
          suggestions: [
            {
              action: '延后 TASK-002',
              reason: '急单优先'
            }
          ]
        }
      })
      return
    }

    if (urgentRequestCount === 2) {
      await route.fulfill({
        status: 500,
        json: {
          error: 'server_error'
        }
      })
      return
    }

    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '改派 TASK-002',
            reason: '重开后重试成功'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })
  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })

  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T09:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('HTTP 500')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T10:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T10:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('改派 TASK-002')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
  await expect(riskPanel.getByText('改派 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
})

test('计划员在旧成功后关闭并重开急单弹窗时不会在新失败过程中看到旧建议回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let urgentRequestCount = 0
  let releaseSecondUrgentRequest = () => {}
  const secondUrgentResponseReady = new Promise<void>((resolve) => {
    releaseSecondUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        json: {
          urgentTaskId: 'WO-001',
          affectedTaskIds: ['TASK-002'],
          suggestions: [
            {
              action: '延后 TASK-002',
              reason: '急单优先'
            }
          ]
        }
      })
      return
    }

    await secondUrgentResponseReady
    await route.fulfill({
      status: 500,
      json: {
        error: 'server_error'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T09:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('加载中')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  releaseSecondUrgentRequest()
  await expect(urgentDialog.getByText('HTTP 500')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
})

test('计划员在旧成功后重开急单弹窗并触发本地校验失败时不会看到旧建议回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
})

test('计划员在旧成功后重开急单弹窗并只修改表单时不会误刷新风险侧栏', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')
  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T09:45:00Z')

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
})

test('计划员在旧成功后重开急单弹窗并只切换资源时不会让旧建议回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  await expect(urgentDialog.getByLabel('插入开始时间')).toHaveValue('2026-04-25T08:00:00Z')
  await expect(urgentDialog.getByLabel('插入结束时间')).toHaveValue('2026-04-25T08:30:00.000Z')

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')

  await expect(urgentDialog.getByLabel('插入开始时间')).toHaveValue('2026-04-25T08:00:00Z')
  await expect(urgentDialog.getByLabel('插入结束时间')).toHaveValue('2026-04-25T08:30:00.000Z')
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
})

test('计划员在旧成功后重开急单弹窗并切换资源后提交时只会显示新资源对应建议', async ({ page }) => {
  let urgentRequestCount = 0

  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        json: {
          urgentTaskId: 'WO-001',
          affectedTaskIds: ['TASK-002'],
          suggestions: [
            {
              action: '延后 TASK-002',
              reason: '急单优先'
            }
          ]
        }
      })
      return
    }

    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-003'],
        suggestions: [
          {
            action: '改派 TASK-003',
            reason: '切换到焊接组'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('改派 TASK-003')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('改派 TASK-003')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-003', { exact: true })).toBeVisible()
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
})

test('计划员在旧成功后重开急单弹窗并切换资源后提交失败时不会看到旧建议回流', async ({ page }) => {
  let urgentRequestCount = 0

  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        json: {
          urgentTaskId: 'WO-001',
          affectedTaskIds: ['TASK-002'],
          suggestions: [
            {
              action: '延后 TASK-002',
              reason: '急单优先'
            }
          ]
        }
      })
      return
    }

    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'HTTP 500' })
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('HTTP 500')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-003', { exact: true })).toHaveCount(0)
})

test('计划员在急单建议成功后修改插单资源时会清空已失效的旧建议', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
})

test('计划员在急单建议成功后修改插单时间窗时会清空已失效的旧建议', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')

  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('TASK-002', { exact: true })).toHaveCount(0)
})

test('计划员在急单失败提示出现后修改插单资源时会清空已失效的旧错误', async ({ page }) => {
  let urgentRequestCount = 0

  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'HTTP 500' })
      })
      return
    }

    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-003'],
        suggestions: [
          {
            action: '改派 TASK-003',
            reason: '切换到焊接组'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('HTTP 500')).toBeVisible()

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')

  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
})

test('计划员在急单失败提示出现后修改插单时间窗时会清空已失效的旧错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'HTTP 500' })
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('HTTP 500')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')

  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
})


test('计划员在本地时间校验报错后切换插入资源时不会误清空校验提示', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T10:30:00Z',
            endAt: '2026-04-25T12:00:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T08:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()
})

test('计划员在本地校验提示时间窗不完整后补填时间时会立即清空旧提示', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })

  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
})

test('计划员在本地校验提示时间窗不完整后切换插入资源时不会误清空旧提示', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T10:30:00Z',
            endAt: '2026-04-25T12:00:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })

  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()
})

test('计划员关闭并重开急单弹窗后会清空上一轮本地校验错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
  await expect(urgentDialog.getByText('暂无重排建议')).toBeVisible()
})

test('计划员在旧成功后触发本地校验并修正后再次提交时不会看到第一轮旧建议回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  let urgentRequestCount = 0
  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        json: {
          urgentTaskId: 'WO-001',
          affectedTaskIds: ['TASK-002'],
          suggestions: [
            {
              action: '延后 TASK-002',
              reason: '急单优先'
            }
          ]
        }
      })
      return
    }

    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-003'],
        suggestions: [
          {
            action: '改派 TASK-003',
            reason: '修正后重新排程'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')
  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T10:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T09:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T10:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)
  await expect(urgentDialog.getByText('改派 TASK-003')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('改派 TASK-003')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
})

test('计划员在旧成功后触发时间窗不完整校验并修正后再次提交时不会看到第一轮旧建议回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  let urgentRequestCount = 0
  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        json: {
          urgentTaskId: 'WO-001',
          affectedTaskIds: ['TASK-002'],
          suggestions: [
            {
              action: '延后 TASK-002',
              reason: '急单优先'
            }
          ]
        }
      })
      return
    }

    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-003'],
        suggestions: [
          {
            action: '改派 TASK-003',
            reason: '修正后重新排程'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toBeVisible()

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')
  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T10:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T10:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
  await expect(urgentDialog.getByText('改派 TASK-003')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('改派 TASK-003')).toBeVisible()
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
})

test('计划员在旧失败后触发时间窗不完整校验并修正后再次提交时不会看到第一轮旧失败回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  let urgentRequestCount = 0
  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          message: '重排服务暂不可用'
        })
      })
      return
    }

    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-003'],
        suggestions: [
          {
            action: '改派 TASK-003',
            reason: '修正后重新排程'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('HTTP 500')).toBeVisible()

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')
  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
  await expect(riskPanel.getByText('HTTP 500')).toHaveCount(0)

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T10:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T10:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
  await expect(urgentDialog.getByText('改派 TASK-003')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
  await expect(riskPanel.getByText('改派 TASK-003')).toBeVisible()
  await expect(riskPanel.getByText('HTTP 500')).toHaveCount(0)
})

test('计划员在急单加载中触发时间窗不完整校验后不会被旧在途建议重新污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('加载中')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
})

test('计划员在急单加载中触发本地校验后不会被旧在途建议重新污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('加载中')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T10:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T09:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()
  await expect(urgentDialog.getByText('延后 TASK-002')).toHaveCount(0)
  await expect(riskPanel.getByText('延后 TASK-002')).toHaveCount(0)
})

test('计划员在急单加载中触发时间窗不完整校验后不会被旧在途失败重新污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({
        message: 'HTTP 500'
      })
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('加载中')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
  await expect(riskPanel.getByText('HTTP 500')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
  await expect(riskPanel.getByText('HTTP 500')).toHaveCount(0)
})

test('计划员在急单加载中触发本地校验后不会被旧在途失败重新污染', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  let releaseUrgentRequest = () => {}
  const urgentResponseReady = new Promise<void>((resolve) => {
    releaseUrgentRequest = resolve
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await urgentResponseReady
    await route.fulfill({
      status: 500,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'HTTP 500' })
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('加载中')).toBeVisible()

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T10:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T09:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)

  releaseUrgentRequest()
  await page.waitForTimeout(50)

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
})

test('计划员在旧失败后触发本地校验并修正后再次提交时不会看到第一轮旧失败回流', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  let urgentRequestCount = 0
  await page.route('**/api/replans/urgent', async (route) => {
    urgentRequestCount += 1

    if (urgentRequestCount === 1) {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'HTTP 500' })
      })
      return
    }

    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-003'],
        suggestions: [
          {
            action: '改派 TASK-003',
            reason: '修正后重新排程'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('tab', { name: '风险与建议' }).click()
  const riskPanel = page.locator('aside').filter({ has: page.getByText('风险与建议') })

  await page.getByRole('button', { name: '插入急单' }).click()
  const urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('HTTP 500')).toBeVisible()

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')
  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T10:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T09:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)

  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T10:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)
  await expect(urgentDialog.getByText('HTTP 500')).toHaveCount(0)
  await expect(urgentDialog.getByText('改派 TASK-003')).toBeVisible()
  await expect(riskPanel.getByText('改派 TASK-003')).toBeVisible()
})

test('计划员在本地校验失败后关闭并重开再直接提交时不会被旧错误阻塞', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T08:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
})

test('计划员在本地校验失败后关闭并重开再修改时间时不会恢复旧错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T08:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:15:00Z')

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)
})

test('计划员在本地校验失败后关闭并重开再切换资源提交时不会带回旧错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-003'],
        suggestions: [
          {
            action: '改派 TASK-003',
            reason: '换线重排'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T08:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)
  await expect(urgentDialog.getByText('改派 TASK-003')).toBeVisible()
})

test('计划员在本地校验失败后关闭并重开再修改结束时间提交时不会带回旧错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T08:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)

  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T08:45:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
})

test('计划员在本地校验失败后关闭并重开再修改开始时间提交时不会带回旧错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:00:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T08:30:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T07:45:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toHaveCount(0)
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
})

test('计划员在时间窗不完整校验失败后关闭并重开再直接提交时不会被旧错误阻塞', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)

  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
})

test('计划员在时间窗不完整校验失败后关闭并重开再修改时间时不会恢复旧错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:15:00Z')

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
})

test('计划员在时间窗不完整校验失败后关闭并重开再切换资源时不会恢复旧错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T10:30:00Z',
            endAt: '2026-04-25T12:00:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
})

test('计划员在时间窗不完整校验失败后关闭并重开再修改开始时间提交时会进入当前时间顺序校验', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)

  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-25T09:15:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
  await expect(urgentDialog.getByText('插入结束时间不能早于开始时间')).toBeVisible()
})

test('计划员在时间窗不完整校验失败后关闭并重开再修改结束时间提交时不会带回旧错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-002'],
        suggestions: [
          {
            action: '延后 TASK-002',
            reason: '急单优先'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)

  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T08:45:00Z')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
  await expect(urgentDialog.getByText('延后 TASK-002')).toBeVisible()
})

test('计划员在时间窗不完整校验失败后关闭并重开再切换资源提交时不会带回旧错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T10:30:00Z',
            endAt: '2026-04-25T12:00:00Z'
          }
        ]
      }
    })
  })

  await page.route('**/api/replans/urgent', async (route) => {
    await route.fulfill({
      json: {
        urgentTaskId: 'WO-001',
        affectedTaskIds: ['TASK-003'],
        suggestions: [
          {
            action: '改派 TASK-003',
            reason: '换线重排'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)

  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')
  await urgentDialog.getByTestId('submit-urgent-replan').click()

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
  await expect(urgentDialog.getByText('改派 TASK-003')).toBeVisible()
})

test('计划员在时间窗不完整校验失败后关闭并重开再修改结束时间时不会恢复旧错误', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入开始时间').fill('')
  await urgentDialog.getByLabel('插入结束时间').fill('')
  await urgentDialog.getByTestId('submit-urgent-replan').click()
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toBeVisible()

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)

  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-25T08:45:00Z')

  await expect(urgentDialog.getByText('请填写完整的插入时间窗')).toHaveCount(0)
})

test('计划员关闭并重开急单弹窗后会恢复默认资源与时间窗', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: true,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          },
          {
            taskId: 'TASK-003',
            resourceId: 'LINE-C',
            resourceGroupName: '焊接组',
            startAt: '2026-04-25T11:00:00Z',
            endAt: '2026-04-25T12:30:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '插入急单' }).click()
  let urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await urgentDialog.getByLabel('插入资源').selectOption('LINE-C')
  await urgentDialog.getByLabel('插入开始时间').fill('2026-04-26T09:15:00Z')
  await urgentDialog.getByLabel('插入结束时间').fill('2026-04-26T10:00:00Z')

  await urgentDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '急单插入' })).toHaveCount(0)

  await page.getByRole('button', { name: '插入急单' }).click()
  urgentDialog = page.getByRole('dialog', { name: '急单插入' })
  await expect(urgentDialog.getByLabel('插入资源')).toHaveValue('LINE-B')
  await expect(urgentDialog.getByLabel('插入开始时间')).toHaveValue('2026-04-25T08:00:00Z')
  await expect(urgentDialog.getByLabel('插入结束时间')).toHaveValue('2026-04-25T08:30:00.000Z')
})

test('计划员在页面内重新加载工作台数据后不会继续看到旧的回写弹窗', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
        items: [
          {
            taskId: 'TASK-002',
            resourceId: 'LINE-B',
            resourceGroupName: '装配组',
            startAt: '2026-04-25T08:00:00Z',
            endAt: '2026-04-25T10:00:00Z'
          }
        ]
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await expect(page.getByRole('dialog', { name: '回写确认' }).getByText('当前草稿：draft-2')).toBeVisible()

  await page.evaluate(async () => {
    const store = (window as Window & { __scheduleDraftStore: { loadPlannerData: () => Promise<void>; publishPlannerDraft: () => Promise<void> } }).__scheduleDraftStore
    await store.loadPlannerData()
  })

  await expect(page.getByRole('dialog', { name: '回写确认' })).toHaveCount(0)
  await expect(page.getByRole('dialog', { name: '回写请求状态' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '确认回写' }).first()).toBeVisible()
})

test('计划员在回写提交中关闭后可通过查看回写状态重新打开加载态', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  let releasePublishRequest = () => {}
  const publishResponseReady = new Promise<void>((resolve) => {
    releasePublishRequest = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await publishResponseReady
    await route.fulfill({
      json: {
        auditId: 'audit-loading-reopen',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-loading-reopen', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-loading-reopen',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'submitted',
        message: 'queued',
        retryable: false,
        attemptCount: 1,
        maxAttempts: 3,
        nextRetryAt: null
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  let publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()
  await expect(publishDialog.getByText('加载中')).toBeVisible()

  await publishDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toBeVisible()

  await page.getByRole('button', { name: '查看回写状态' }).click()
  publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(publishDialog.getByText('加载中')).toBeVisible()

  const reopenedLoadingDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(reopenedLoadingDialog.getByText('加载中')).toBeVisible()

  releasePublishRequest()
  const publishResultDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(publishResultDialog.locator('.publish-dialog__title-anchor')).toHaveText('回写已进入队列')
  await expect(publishResultDialog.getByText('结果版本：draft-1', { exact: true })).toBeVisible()
})

test('计划员在回写提交中会看到禁用的确认按钮', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  let releasePublishRequest = () => {}
  const publishResponseReady = new Promise<void>((resolve) => {
    releasePublishRequest = resolve
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await publishResponseReady
    await route.fulfill({
      json: {
        auditId: 'audit-disabled-button',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-disabled-button', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-disabled-button',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'submitted',
        message: 'queued',
        retryable: false,
        attemptCount: 1,
        maxAttempts: 3,
        nextRetryAt: null
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  const confirmButton = publishDialog.getByTestId('confirm-publish')

  await confirmButton.click()
  await expect(publishDialog.getByText('加载中')).toBeVisible()
  await expect(confirmButton).toBeDisabled()

  releasePublishRequest()
  const publishResultDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(publishResultDialog.locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-1')
})

test('计划员关闭回写结果后可通过查看回写状态重新打开同一结果', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        auditId: 'audit-1',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-1', async (route) => {
    await route.fulfill({
      json: {
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
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()

  const publishResultDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(publishResultDialog.getByText('结果版本：draft-1', { exact: true })).toBeVisible()

  await publishResultDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '回写已进入队列' })).toHaveCount(0)

  await page.getByRole('button', { name: '查看回写状态' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(reopenedDialog.locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-1')
  await expect(reopenedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')
})

test('计划员在回写状态刷新失败后仍保留上一轮摘要', async ({ page }) => {
  let writebackStatusRequestCount = 0

  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        auditId: 'audit-refresh-failed',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-refresh-failed', async (route) => {
    writebackStatusRequestCount += 1
    if (writebackStatusRequestCount === 1) {
      await route.fulfill({
        json: {
          auditId: 'audit-refresh-failed',
          draftId: 'draft-1',
          status: 'validated',
          writebackStatus: 'submitted',
          message: 'queued',
          retryable: false,
          attemptCount: 1,
          maxAttempts: 3,
          nextRetryAt: null
        }
      })
      return
    }

    await route.fulfill({
      status: 500,
      json: {
        error: 'server_error'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()

  const publishResultDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(publishResultDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')

  await publishResultDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '回写已进入队列' })).toHaveCount(0)

  await page.getByRole('button', { name: '查看回写状态' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(reopenedDialog.locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-1')
  await expect(reopenedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')
  await expect(reopenedDialog.getByText('加载回写状态失败')).toBeVisible()
  await expect(reopenedDialog.getByText('系统已记录本次回写队列状态留痕')).toBeVisible()
})

test('计划员重新打开回写状态时会看到后端推进后的最新结果', async ({ page }) => {
  let writebackStatusRequestCount = 0

  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        auditId: 'audit-refresh-success',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-refresh-success', async (route) => {
    writebackStatusRequestCount += 1
    if (writebackStatusRequestCount === 1) {
      await route.fulfill({
        json: {
          auditId: 'audit-refresh-success',
          draftId: 'draft-1',
          status: 'validated',
          writebackStatus: 'submitted',
          message: 'queued',
          retryable: false,
          attemptCount: 1,
          maxAttempts: 3,
          nextRetryAt: null
        }
      })
      return
    }

    await route.fulfill({
      json: {
        auditId: 'audit-refresh-success',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'SUCCEEDED',
        message: 'writeback_succeeded',
        retryable: false,
        attemptCount: 1,
        maxAttempts: 3,
        nextRetryAt: null
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()

  const queuedDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(queuedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')

  await queuedDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '回写已进入队列' })).toHaveCount(0)

  await page.getByRole('button', { name: '查看回写状态' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '回写已完成' })
  await expect(reopenedDialog.locator('.publish-dialog__title-anchor')).toHaveText('回写已完成')
  await expect(reopenedDialog.locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-1')
  await expect(reopenedDialog.locator('.publish-dialog__summary-title')).toHaveText('回写已完成')
  await expect(reopenedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本回写已完成')
  await expect(reopenedDialog.getByText('回写状态：回写成功')).toBeVisible()
})

test('计划员重新打开回写状态时会看到后端推进后的终态失败结果', async ({ page }) => {
  let writebackStatusRequestCount = 0

  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        auditId: 'audit-refresh-terminal-failed',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-refresh-terminal-failed', async (route) => {
    writebackStatusRequestCount += 1
    if (writebackStatusRequestCount === 1) {
      await route.fulfill({
        json: {
          auditId: 'audit-refresh-terminal-failed',
          draftId: 'draft-1',
          status: 'validated',
          writebackStatus: 'submitted',
          message: 'queued',
          retryable: false,
          attemptCount: 1,
          maxAttempts: 3,
          nextRetryAt: null
        }
      })
      return
    }

    await route.fulfill({
      json: {
        auditId: 'audit-refresh-terminal-failed',
        draftId: 'draft-1',
        status: 'validated',
        writebackStatus: 'TERMINAL_FAILED',
        message: 'writeback_failed',
        retryable: false,
        attemptCount: 3,
        maxAttempts: 3,
        nextRetryAt: null
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()

  const queuedDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(queuedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')

  await queuedDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '回写已进入队列' })).toHaveCount(0)

  await page.getByRole('button', { name: '查看回写状态' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '回写失败' })
  await expect(reopenedDialog.locator('.publish-dialog__title-anchor')).toHaveText('回写失败')
  await expect(reopenedDialog.locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-1')
  await expect(reopenedDialog.locator('.publish-dialog__summary-title')).toHaveText('回写失败')
  await expect(reopenedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本回写失败')
  await expect(reopenedDialog.getByText('回写状态：回写失败')).toBeVisible()
  await expect(reopenedDialog.getByText('回写说明：writeback_failed')).toBeVisible()
})

test('计划员在回写失败状态刷新失败后仍保留上一轮失败摘要', async ({ page }) => {
  let writebackStatusRequestCount = 0

  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-1',
        auditId: 'audit-refresh-terminal-failed',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-refresh-terminal-failed', async (route) => {
    writebackStatusRequestCount += 1
    if (writebackStatusRequestCount === 1) {
      await route.fulfill({
        json: {
          auditId: 'audit-refresh-terminal-failed',
          draftId: 'draft-1',
          status: 'validated',
          writebackStatus: 'submitted',
          message: 'queued',
          retryable: false,
          attemptCount: 1,
          maxAttempts: 3,
          nextRetryAt: null
        }
      })
      return
    }

    await route.fulfill({
      status: 500,
      json: {
        error: 'server_error'
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  const publishDialog = page.getByRole('dialog', { name: '回写确认' })
  await publishDialog.getByTestId('confirm-publish').click()

  const queuedDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(queuedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')

  await queuedDialog.getByRole('button', { name: '关闭' }).click()
  await expect(page.getByRole('dialog', { name: '回写已进入队列' })).toHaveCount(0)

  await page.getByRole('button', { name: '查看回写状态' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '回写已进入队列' })
  await expect(reopenedDialog.locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-1')
  await expect(reopenedDialog.locator('.publish-dialog__summary-detail')).toHaveText('当前结果版本已进入回写队列')
  await expect(reopenedDialog.getByText('加载回写状态失败')).toBeVisible()
  await expect(reopenedDialog.getByText('系统已记录本次回写队列状态留痕')).toBeVisible()
})

test('计划员通过 SPA 路由离开并返回工作台后不会看到上一轮回写结果残留', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
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
  })

  await page.route('**/api/load-summary', async (route) => {
    await route.fulfill({
      json: {
        overdueCount: 1,
        delayedCount: 2,
        idleCapacityCount: 3,
        exceptionCount: 4
      }
    })
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-spa-return',
        draftId: 'draft-2',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-spa-return', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-spa-return',
        draftId: 'draft-2',
        status: 'validated',
        writebackStatus: 'submitted',
        message: 'queued',
        retryable: false,
        attemptCount: 1,
        maxAttempts: 3,
        nextRetryAt: null
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写已进入队列' }).locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-2')

  await page.evaluate(() => {
    window.history.pushState({}, '', '/dashboard')
    window.dispatchEvent(new PopStateEvent('popstate'))
  })
  await expect(page.getByRole('heading', { name: '老板驾驶舱' })).toBeVisible()

  await page.evaluate(() => {
    window.history.pushState({}, '', '/planner')
    window.dispatchEvent(new PopStateEvent('popstate'))
  })
  await expect(page.getByText('待排任务池')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(reopenedDialog.getByText('当前草稿：draft-2')).toBeVisible()
  await expect(reopenedDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(reopenedDialog.getByText('结果版本：draft-2')).toHaveCount(0)
})

test('计划员重新进入工作台后不会看到上一轮回写结果残留', async ({ page }) => {
  await page.route('**/api/task-pool', async (route) => {
    await route.fulfill({
      json: [
        {
          workOrderCode: 'WO-001',
          dueAt: '2026-04-24T08:00:00Z',
          urgent: false,
          materialRisk: 'low',
          readiness: 'ready'
        }
      ]
    })
  })

  await page.route('**/api/schedules/draft', async (route) => {
    await route.fulfill({
      json: {
        draftId: 'draft-2',
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
  })

  await page.route('**/api/writeback/publish', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-reenter-planner',
        draftId: 'draft-2',
        status: 'validated',
        writebackStatus: 'pending'
      }
    })
  })

  await page.route('**/api/writeback/audit-reenter-planner', async (route) => {
    await route.fulfill({
      json: {
        auditId: 'audit-reenter-planner',
        draftId: 'draft-2',
        status: 'validated',
        writebackStatus: 'submitted',
        message: 'queued',
        retryable: false,
        attemptCount: 1,
        maxAttempts: 3,
        nextRetryAt: null
      }
    })
  })

  await page.goto('/planner')
  await expect(page.getByText('待排任务池')).toBeVisible()

  await page.getByRole('button', { name: '确认回写' }).click()
  await page.getByRole('dialog', { name: '回写确认' }).getByTestId('confirm-publish').click()
  await expect(page.getByRole('dialog', { name: '回写已进入队列' }).locator('.publish-dialog__summary-draft')).toHaveText('结果版本：draft-2')

  await page.reload()
  await expect(page.getByText('待排任务池')).toBeVisible()
  await expect(page.getByRole('button', { name: '确认回写' })).toBeVisible()
  await expect(page.getByRole('button', { name: '查看回写状态' })).toHaveCount(0)

  await page.getByRole('button', { name: '确认回写' }).click()
  const reopenedDialog = page.getByRole('dialog', { name: '回写确认' })
  await expect(reopenedDialog.getByText('当前草稿：draft-2')).toBeVisible()
  await expect(reopenedDialog.getByText('暂无回写结果')).toBeVisible()
  await expect(reopenedDialog.getByText('结果版本：draft-2')).toHaveCount(0)
})

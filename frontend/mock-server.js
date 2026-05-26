import { createServer } from 'http'

const server = createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json')
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS')
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type')

  if (req.method === 'OPTIONS') {
    res.writeHead(204)
    res.end()
    return
  }

  const send = (data, status = 200) => {
    res.writeHead(status)
    res.end(JSON.stringify(data))
  }

  if (req.url === '/api/task-pool') {
    send([
      { workOrderCode: 'WO-001', dueAt: '2026-04-24T08:00:00Z', urgent: false, materialRisk: 'low', readiness: 'ready' },
      { workOrderCode: 'WO-002', dueAt: '2026-04-25T08:00:00Z', urgent: true, materialRisk: 'high', readiness: 'ready' }
    ])
  } else if (req.url === '/api/schedules/draft') {
    send({
      draftId: 'draft-demo-1',
      items: [
        { taskId: 'TASK-001', resourceId: 'LINE-A', resourceGroupName: '冲压组', startAt: '2026-04-24T08:00:00Z', endAt: '2026-04-24T10:00:00Z' },
        { taskId: 'TASK-002', resourceId: 'LINE-A', resourceGroupName: '冲压组', startAt: '2026-04-24T10:00:00Z', endAt: '2026-04-24T11:30:00Z' }
      ]
    })
  } else if (req.url === '/api/dashboard/overview') {
    send({ workOrderCount: 12, urgentCount: 3, capacityStatus: 'feasible' })
  } else if (req.url === '/api/dashboard/capacity-summary') {
    send({ status: 'feasible', loadRate: 0.44 })
  } else if (req.url === '/api/capacity-analysis/overview') {
    send({
      trends: [{ resourceId: 'LINE-A', bucketLabel: '2026-04-24 08:00', status: 'feasible', loadRate: 0.44 }],
      groupDiffs: [{ groupName: '冲压组', gapRate: 0.44 }],
      peakPeriods: [{ bucketLabel: '2026-04-24 08:00', status: 'tight', loadRate: 1.0 }]
    })
  } else if (req.url === '/api/resources') {
    send([
      { resourceId: 'LINE-A', groupName: '冲压组', defaultPlannerResource: true },
      { resourceId: 'LINE-B', groupName: '冲压组', defaultPlannerResource: true },
      { resourceId: 'LINE-C', groupName: '装配组', defaultPlannerResource: false }
    ])
  } else if (req.url === '/api/routes') {
    send(['ROUTE-01', 'ROUTE-02'])
  } else if (req.url.startsWith('/api/routes/') && req.url.endsWith('/steps')) {
    send([
      { stepId: 'TASK-001', requiredMinutes: 120, dependencyStepIds: [] },
      { stepId: 'TASK-002', requiredMinutes: 90, dependencyStepIds: ['TASK-001'] }
    ])
  } else if (req.url.startsWith('/api/writeback/')) {
    send({ auditId: 'audit-1', draftId: 'draft-1', status: 'validated', writebackStatus: 'submitted', message: 'queued', retryable: false, attemptCount: 1, maxAttempts: 3, nextRetryAt: null })
  } else if (req.url === '/api/writeback/publish') {
    send({ draftId: 'draft-1', auditId: 'audit-1', status: 'validated', writebackStatus: 'pending' })
  } else if (req.url === '/api/replans/urgent') {
    send({ urgentTaskId: 'WO-URGENT-001', affectedTaskIds: ['TASK-001'], suggestions: [{ action: 'move_next_slot', reason: 'line overloaded' }] })
  } else if (req.url.startsWith('/api/work-orders/') && req.method === 'DELETE') {
    res.writeHead(204)
    res.end()
  } else if (req.url.startsWith('/api/work-orders/') && req.method === 'PUT') {
    send({ workOrderCode: 'WO-001', status: 'released', quantity: 20, dueAt: '2026-04-24T08:00:00Z', routeId: 'ROUTE-01', urgent: false, parentWorkOrderCodes: [], materialRisk: 'low' })
  } else if (req.url === '/api/work-orders') {
    send({ workOrderCode: 'WO-003', status: 'released', quantity: 10, dueAt: '2026-04-26T08:00:00Z', routeId: 'ROUTE-01', urgent: false, parentWorkOrderCodes: [], materialRisk: 'low' })
  } else {
    send({ error: 'not found' }, 404)
  }
})

server.listen(8080, () => console.log('Mock server running on http://localhost:8080'))

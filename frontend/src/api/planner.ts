import { http } from '@/api/http'
import type {
  PublishResult,
  ScheduledItem,
  Suggestion,
  TaskPoolItem,
  WritebackStatus
} from '@/stores/scheduleDraft'

export interface ScheduleDraftResponse {
  draftId: string
  items: ScheduledItem[]
}

export interface UrgentReplanRequest {
  urgentTaskId: string
  urgentResourceId: string
  urgentStartAt: string
  urgentEndAt: string
  items: ScheduledItem[]
}

export interface UrgentReplanResponse {
  urgentTaskId: string
  affectedTaskIds: string[]
  suggestions: Suggestion[]
}

export interface PublishDraftRequest {
  draftId: string
  items: ScheduledItem[]
}

export interface CreateWorkOrderRequest {
  workOrderCode: string
  status: string
  quantity: number
  dueAt: string
  routeId: string
  urgent: boolean
  parentWorkOrderCodes: string[]
  materialRisk: string
}

export interface ResourceDefinition {
  resourceId: string
  groupName: string
  defaultPlannerResource: boolean
}

async function listTaskPool() {
  return http.get<TaskPoolItem[]>('/api/task-pool')
}

async function loadScheduleDraft() {
  return http.post<ScheduleDraftResponse>('/api/schedules/draft', {})
}

async function replanUrgent(request: UrgentReplanRequest) {
  return http.post<UrgentReplanResponse>('/api/replans/urgent', request)
}

async function publishScheduleDraft(request: PublishDraftRequest) {
  try {
    return await http.post<PublishResult>('/api/writeback/publish', request)
  } catch (error) {
    if (error instanceof Error && error.message === 'HTTP 409') {
      throw new Error('存在阻塞项，无法回写')
    }
    throw new Error('发布排程草稿失败')
  }
}

async function loadWritebackStatus(auditId: string) {
  try {
    return await http.get<WritebackStatus>(`/api/writeback/${auditId}`)
  } catch {
    throw new Error('加载回写状态失败')
  }
}

async function createWorkOrder(request: CreateWorkOrderRequest) {
  return http.post<TaskPoolItem>('/api/work-orders', request)
}

async function updateWorkOrder(workOrderCode: string, request: CreateWorkOrderRequest) {
  return http.put<TaskPoolItem>(`/api/work-orders/${workOrderCode}`, request)
}

async function deleteWorkOrder(workOrderCode: string) {
  return http.delete<void>(`/api/work-orders/${workOrderCode}`)
}

export interface ResourceSaveRequest {
  resourceId: string
  groupName: string
  defaultPlanner: boolean
}

async function listResources() {
  return http.get<ResourceDefinition[]>('/api/resources')
}

async function createResource(request: ResourceSaveRequest) {
  return http.post<ResourceDefinition>('/api/resources', request)
}

async function updateResource(resourceId: string, request: ResourceSaveRequest) {
  return http.put<ResourceDefinition>(`/api/resources/${resourceId}`, request)
}

async function deleteResource(resourceId: string) {
  return http.delete<void>(`/api/resources/${resourceId}`)
}

export interface RouteStep {
  stepId: string
  requiredMinutes: number
  dependencyStepIds: string[]
}

async function listRoutes() {
  return http.get<string[]>('/api/routes')
}

async function listRouteSteps(routeId: string) {
  return http.get<RouteStep[]>(`/api/routes/${routeId}/steps`)
}

export const plannerApi = {
  listTaskPool,
  loadScheduleDraft,
  replanUrgent,
  publishScheduleDraft,
  loadWritebackStatus,
  createWorkOrder,
  updateWorkOrder,
  deleteWorkOrder,
  listResources,
  createResource,
  updateResource,
  deleteResource,
  listRoutes,
  listRouteSteps
}

<template>
  <div v-if="modelValue" class="modal-backdrop">
    <div role="dialog" :aria-label="dialogTitle" class="modal create-work-order-dialog">
      <div class="modal__header">
        <div class="modal__title-group">
          <div class="modal__title">{{ dialogTitle }}</div>
          <div class="modal__description">{{ dialogDescription }}</div>
        </div>
        <button class="button button--ghost" type="button" @click="close">关闭</button>
      </div>

      <div class="modal__body">
        <div class="create-work-order-form">
          <label class="create-work-order-dialog__field">
            <span class="create-work-order-dialog__label">工单编号</span>
            <input v-model="form.workOrderCode" class="form-control" aria-label="工单编号" type="text" :disabled="isEdit" />
          </label>
          <label class="create-work-order-dialog__field">
            <span class="create-work-order-dialog__label">状态</span>
            <select v-model="form.status" class="form-control" aria-label="状态">
              <option value="released">已释放</option>
              <option value="planned">已计划</option>
            </select>
          </label>
          <label class="create-work-order-dialog__field">
            <span class="create-work-order-dialog__label">数量</span>
            <input v-model.number="form.quantity" class="form-control" aria-label="数量" type="number" />
          </label>
          <label class="create-work-order-dialog__field">
            <span class="create-work-order-dialog__label">交期</span>
            <input v-model="form.dueAt" class="form-control" aria-label="交期" type="text" placeholder="2026-04-24T08:00:00Z" />
          </label>
          <label class="create-work-order-dialog__field">
            <span class="create-work-order-dialog__label">工艺路线</span>
            <select v-model="form.routeId" class="form-control" aria-label="工艺路线">
              <option v-for="route in routeOptions" :key="route" :value="route">{{ route }}</option>
            </select>
          </label>
          <label class="create-work-order-dialog__field">
            <span class="create-work-order-dialog__label">物料风险</span>
            <select v-model="form.materialRisk" class="form-control" aria-label="物料风险">
              <option value="low">低</option>
              <option value="medium">中</option>
              <option value="high">高</option>
            </select>
          </label>
          <label class="create-work-order-dialog__field create-work-order-dialog__field--inline">
            <input v-model="form.urgent" type="checkbox" aria-label="急单" />
            <span>急单</span>
          </label>
          <button
            class="button button--primary"
            type="button"
            :data-testid="isEdit ? 'submit-update-work-order' : 'submit-create-work-order'"
            :disabled="submitting"
            @click="submit"
          >
            {{ submitLabel }}
          </button>
        </div>

        <div v-if="error" class="status-block status-block--error create-work-order-dialog__status">{{ error }}</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.create-work-order-form {
  display: grid;
  gap: 12px;
}

.create-work-order-dialog__field {
  display: grid;
  gap: 4px;
}

.create-work-order-dialog__field--inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.create-work-order-dialog__label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.create-work-order-dialog__status {
  margin-top: 12px;
}
</style>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { plannerApi } from '@/api/planner'
import type { TaskPoolItem } from '@/stores/scheduleDraft'

const props = defineProps<{
  modelValue: boolean
  mode?: 'create' | 'edit'
  initialData?: TaskPoolItem | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: []
  updated: []
}>()

const isEdit = computed(() => props.mode === 'edit')
const dialogTitle = computed(() => isEdit.value ? '编辑工单' : '新增工单')
const dialogDescription = computed(() => isEdit.value ? '修改工单信息。' : '录入新工单信息，加入待排任务池。')
const submitLabel = computed(() => {
  if (submitting.value) return '提交中...'
  return isEdit.value ? '确认修改' : '确认创建'
})

const submitting = ref(false)
const error = ref('')
const routeOptions = ref<string[]>(['ROUTE-01'])

const defaultForm = () => ({
  workOrderCode: '',
  status: 'released',
  quantity: 1,
  dueAt: '',
  routeId: routeOptions.value[0] ?? 'ROUTE-01',
  urgent: false,
  materialRisk: 'low'
})

const form = reactive(defaultForm())

function resetForm() {
  Object.assign(form, defaultForm())
}

function fillForm(data: TaskPoolItem) {
  form.workOrderCode = data.workOrderCode
  form.status = 'released'
  form.quantity = 1
  form.dueAt = data.dueAt
  form.routeId = 'ROUTE-01'
  form.urgent = data.urgent
  form.materialRisk = data.materialRisk
}

function close() {
  emit('update:modelValue', false)
}

watch(() => props.modelValue, async (visible) => {
  if (visible) {
    error.value = ''
    try {
      const routes = await plannerApi.listRoutes()
      if (routes.length > 0) {
        routeOptions.value = routes
      }
    } catch {
      // 保持默认选项
    }
    if (isEdit.value && props.initialData) {
      fillForm(props.initialData)
    } else {
      resetForm()
    }
  }
})

async function submit() {
  if (!form.workOrderCode || !form.dueAt) {
    error.value = '请填写工单编号和交期'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    const payload = {
      workOrderCode: form.workOrderCode,
      status: form.status,
      quantity: form.quantity,
      dueAt: form.dueAt,
      routeId: form.routeId,
      urgent: form.urgent,
      parentWorkOrderCodes: [] as string[],
      materialRisk: form.materialRisk
    }
    if (isEdit.value) {
      await plannerApi.updateWorkOrder(form.workOrderCode, payload)
      emit('updated')
    } else {
      await plannerApi.createWorkOrder(payload)
      emit('created')
    }
    emit('update:modelValue', false)
  } catch (e) {
    error.value = e instanceof Error ? e.message : (isEdit.value ? '修改工单失败' : '创建工单失败')
  } finally {
    submitting.value = false
  }
}
</script>

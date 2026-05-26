<template>
  <div v-if="modelValue" class="modal-backdrop">
    <div role="dialog" aria-label="急单插入" class="modal urgent-dialog">
      <div class="modal__header">
        <div class="modal__title-group">
          <div class="modal__title">急单插入</div>
          <div class="modal__description">选择插入资源与时间窗，生成一组新的重排建议。</div>
        </div>
        <button class="button button--ghost" type="button" @click="emit('update:modelValue', false)">关闭</button>
      </div>

      <div class="modal__body">
        <div class="urgent-insert-form">
          <label class="urgent-dialog__field">
            <span class="urgent-dialog__label">插入资源</span>
            <select v-model="selectedResourceId" class="form-control" @change="invalidateUrgentResultIfPresent">
              <option v-for="option in resourceOptions" :key="option.resourceId" :value="option.resourceId">
                {{ option.resourceId }}
              </option>
            </select>
          </label>
          <label class="urgent-dialog__field">
            <span class="urgent-dialog__label">插入开始时间</span>
            <input
              v-model="selectedStartAt"
              class="form-control"
              aria-label="插入开始时间"
              type="text"
              @input="handleUrgentTimeInput"
            />
          </label>
          <label class="urgent-dialog__field">
            <span class="urgent-dialog__label">插入结束时间</span>
            <input
              v-model="selectedEndAt"
              class="form-control"
              aria-label="插入结束时间"
              type="text"
              @input="handleUrgentTimeInput"
            />
          </label>
          <button class="button button--primary" type="button" data-testid="submit-urgent-replan" @click="submitUrgentReplan">
            生成重排建议
          </button>
        </div>

        <div v-if="validationError" class="status-block status-block--error urgent-dialog__status">{{ validationError }}</div>
        <div v-else-if="scheduleDraftState.urgentLoading" class="status-block status-block--loading urgent-dialog__status">加载中</div>
        <div v-else-if="scheduleDraftState.urgentError" class="status-block status-block--error urgent-dialog__status">{{ scheduleDraftState.urgentError }}</div>
        <template v-else>
          <section class="urgent-dialog__section">
            <div class="urgent-dialog__section-title">受影响任务</div>
            <ul v-if="affectedTaskEntries.length > 0 || unmappedAffectedTaskIds.length > 0" class="urgent-dialog__list">
              <li v-for="item in affectedTaskEntries" :key="item.taskId" class="urgent-dialog__item">
                <span class="urgent-dialog__task">{{ item.taskId }}</span>
                <span class="urgent-dialog__meta">{{ item.resourceGroupName }}</span>
              </li>
              <li v-for="taskId in unmappedAffectedTaskIds" :key="taskId" class="urgent-dialog__item">
                <span class="urgent-dialog__task">{{ taskId }}</span>
              </li>
            </ul>
            <div v-else class="urgent-dialog__empty">暂无受影响任务</div>
          </section>

          <section class="urgent-dialog__section">
            <div class="urgent-dialog__section-title">重排建议</div>
            <ul v-if="scheduleDraftState.suggestions.length > 0" class="urgent-dialog__list">
              <li
                v-for="(suggestion, index) in scheduleDraftState.suggestions"
                :key="`${suggestion.action}-${suggestion.reason}`"
                class="urgent-dialog__item"
              >
                <span class="badge badge--warning">{{ suggestion.action }}</span>
                <span class="urgent-dialog__meta">{{ suggestion.reason }}</span>
                <button
                  class="button button--secondary"
                  type="button"
                  :data-testid="`apply-urgent-suggestion-${index}`"
                  @click="applySuggestion(suggestion)"
                >
                  应用到画板
                </button>
              </li>
            </ul>
            <div v-else class="urgent-dialog__empty">暂无重排建议</div>
          </section>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.urgent-dialog {
  width: min(780px, 100%);
}

.urgent-insert-form {
  display: grid;
  gap: 14px;
}

.urgent-dialog__field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.urgent-dialog__label,
.urgent-dialog__section-title {
  color: var(--text-muted);
  font-size: 0.84rem;
  font-weight: 700;
}

.urgent-dialog__status {
  text-align: left;
}

.urgent-dialog__section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.urgent-dialog__list {
  display: grid;
  gap: 10px;
}

.urgent-dialog__item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 16px;
  background: var(--bg-card-muted);
  border: 1px solid rgba(219, 227, 240, 0.8);
}

.urgent-dialog__task {
  font-weight: 700;
}

.urgent-dialog__meta {
  color: var(--text-secondary);
  line-height: 1.5;
}

.urgent-dialog__empty {
  padding: 18px;
  border-radius: 16px;
  border: 1px dashed var(--border-strong);
  color: var(--text-muted);
  text-align: center;
}
</style>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { resetUrgentReplanResult, scheduleDraftState } from '@/stores/scheduleDraft'

interface ResourceOption {
  resourceId: string
  resourceGroupName: string
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    resourceOptions?: ResourceOption[]
    defaultUrgentResourceId?: string
    defaultUrgentStartAt?: string
    defaultUrgentEndAt?: string
  }>(),
  {
    resourceOptions: () => [],
    defaultUrgentResourceId: '',
    defaultUrgentStartAt: '',
    defaultUrgentEndAt: ''
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [payload: { resourceId: string; startAt: string; endAt: string }]
  'apply-suggestion': [suggestion: { action: string; reason: string }]
}>()

const selectedResourceId = ref('')
const selectedStartAt = ref('')
const selectedEndAt = ref('')
const validationError = ref('')

watch(
  () => [props.modelValue, props.defaultUrgentResourceId, props.defaultUrgentStartAt, props.defaultUrgentEndAt],
  () => {
    if (!props.modelValue) {
      return
    }
    selectedResourceId.value = props.defaultUrgentResourceId
    selectedStartAt.value = props.defaultUrgentStartAt
    selectedEndAt.value = props.defaultUrgentEndAt
    validationError.value = ''
  },
  { immediate: true }
)

const affectedTaskIds = computed(() => scheduleDraftState.affectedTaskIds ?? [])

const affectedTaskEntries = computed(() =>
  affectedTaskIds.value
    .map((taskId) => scheduleDraftState.scheduledItems.find((item) => item.taskId === taskId))
    .filter((item): item is (typeof scheduleDraftState.scheduledItems)[number] => Boolean(item))
)

const unmappedAffectedTaskIds = computed(() => {
  const mappedTaskIds = new Set(affectedTaskEntries.value.map((item) => item.taskId))
  return affectedTaskIds.value.filter((taskId) => !mappedTaskIds.has(taskId))
})

function invalidateUrgentResultIfPresent() {
  if (
    scheduleDraftState.suggestions.length === 0 &&
    affectedTaskIds.value.length === 0 &&
    !scheduleDraftState.urgentInsertion &&
    !scheduleDraftState.urgentError &&
    !scheduleDraftState.urgentLoading
  ) {
    return
  }

  resetUrgentReplanResult()
}

function handleUrgentTimeInput() {
  validationError.value = ''
  invalidateUrgentResultIfPresent()
}

function submitUrgentReplan() {
  if (!selectedStartAt.value || !selectedEndAt.value) {
    validationError.value = '请填写完整的插入时间窗'
    return
  }
  if (new Date(selectedEndAt.value).getTime() < new Date(selectedStartAt.value).getTime()) {
    validationError.value = '插入结束时间不能早于开始时间'
    return
  }

  validationError.value = ''
  emit('submit', {
    resourceId: selectedResourceId.value,
    startAt: selectedStartAt.value,
    endAt: selectedEndAt.value
  })
}

function applySuggestion(suggestion: { action: string; reason: string }) {
  emit('apply-suggestion', suggestion)
  emit('update:modelValue', false)
}
</script>


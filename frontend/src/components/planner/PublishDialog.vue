<template>
  <div v-if="modelValue" class="modal-backdrop">
    <div role="dialog" :aria-label="dialogAriaLabel" class="modal publish-dialog">
      <div class="publish-dialog__title-anchor">{{ dialogTitle }}</div>
      <div class="modal__header publish-dialog__header">
        <div class="modal__title-group">
          <div class="modal__description">按当前结果版本查看回写前校验、执行状态与后续处理说明。</div>
        </div>
        <button class="button button--ghost" type="button" @click="emit('update:modelValue', false)">关闭</button>
      </div>

      <div class="modal__body">
        <section v-if="currentDraftId && !scheduleDraftState.publishResult" class="publish-dialog__draft panel-card panel-card--muted">
          <div class="publish-dialog__draft-value">当前草稿：{{ currentDraftId }}</div>
        </section>

        <button
          v-if="showConfirmButton"
          class="button button--primary publish-dialog__confirm"
          type="button"
          data-testid="confirm-publish"
          :disabled="scheduleDraftState.publishLoading"
          @click="emit('confirm')"
        >
          {{ confirmButtonLabel }}
        </button>

        <div v-if="scheduleDraftState.publishLoading && !scheduleDraftState.publishResult" class="status-block status-block--loading">加载中</div>
        <div v-else-if="scheduleDraftState.publishError && !scheduleDraftState.publishResult" class="status-block status-block--error">{{ scheduleDraftState.publishError }}</div>
        <template v-else-if="scheduleDraftState.publishResult">
          <div v-if="scheduleDraftState.publishLoading" class="status-block status-block--loading">加载中</div>
          <div v-if="scheduleDraftState.publishError" class="status-block status-block--error">{{ scheduleDraftState.publishError }}</div>
          <section class="publish-dialog__summary panel-card panel-card--muted">
            <div class="publish-dialog__summary-title">{{ publishRequestTitle }}</div>
            <div class="publish-dialog__summary-draft">结果版本：{{ scheduleDraftState.publishResult.draftId }}</div>
            <div class="publish-dialog__summary-detail">{{ publishRequestDetail }}</div>
          </section>

          <section class="info-list">
            <div class="info-row">
              <span class="info-row__value">{{ confirmStatusLabelPrefix }}：{{ confirmStatusLabel }}</span>
              <span class="publish-dialog__detail">{{ confirmStatusDetail }}</span>
            </div>
            <div class="info-row">
              <span class="info-row__value">{{ executionModeLabelPrefix }}：{{ executionModeLabel }}</span>
            </div>
            <div class="info-row">
              <span class="info-row__value">回写前校验：{{ publishStatusLabel }}</span>
              <span class="publish-dialog__detail">{{ publishStatusDetail }}</span>
            </div>
            <div class="info-row">
              <span class="info-row__value">回写状态：{{ writebackStatusLabel }}</span>
              <span class="publish-dialog__detail">{{ writebackStatusDetail }}</span>
            </div>
            <div v-if="publishMessageLabel" class="info-row">
              <span class="info-row__value">{{ publishMessageLabelPrefix }}：{{ publishMessageLabel }}</span>
            </div>
            <div v-if="retryAttemptLabel" class="info-row">
              <span class="info-row__value">重试次数：{{ retryAttemptLabel }}</span>
            </div>
            <div v-if="nextRetryAtLabel" class="info-row">
              <span class="info-row__value">下一次重试：{{ nextRetryAtLabel }}</span>
            </div>
          </section>

          <section class="publish-dialog__notes">
            <div class="info-row"><span class="info-row__value">{{ checklistMessageLabel }}</span></div>
            <div class="info-row"><span class="info-row__value">{{ executionMessageLabel }}</span></div>
            <div class="info-row"><span class="info-row__value">{{ traceabilityMessageLabel }}</span></div>
            <div class="info-row"><span class="info-row__value">{{ auditTrailMessageLabel }}</span></div>
            <div class="info-row"><span class="info-row__value">{{ followupMessageLabel }}</span></div>
            <div class="info-row"><span class="info-row__value">{{ destinationMessageLabel }}</span></div>
          </section>
        </template>
        <div v-else class="status-block">暂无回写结果</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.publish-dialog {
  width: min(760px, 100%);
}

.publish-dialog__title-anchor {
  padding: 24px 28px 0;
  font-size: 1.35rem;
  font-weight: 700;
}

.publish-dialog__header {
  padding-top: 12px;
}

.publish-dialog__draft,
.publish-dialog__summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.publish-dialog__draft-label {
  color: var(--text-muted);
  font-size: 0.84rem;
}

.publish-dialog__draft-value,
.publish-dialog__summary-title {
  font-size: 1.1rem;
  font-weight: 700;
}

.publish-dialog__summary-draft,
.publish-dialog__summary-detail,
.publish-dialog__detail {
  color: var(--text-secondary);
  line-height: 1.55;
}

.publish-dialog__confirm {
  align-self: flex-start;
}

.publish-dialog__notes {
  display: grid;
  gap: 12px;
}
</style>

<script setup lang="ts">
import { computed } from 'vue'
import { scheduleDraftState } from '@/stores/scheduleDraft'

defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()

const dialogAriaLabel = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '未通过回写前校验'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return '回写已完成'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '回写失败'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'RETRYABLE_FAILED') {
    return '回写重试中'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return '回写已进入队列'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'pending') {
    return '回写请求已提交'
  }
  if (scheduleDraftState.publishResult) {
    return '回写请求状态'
  }
  return '回写确认'
})

const dialogTitle = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '未通过回写前校验'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return '回写已完成'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '回写失败'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'RETRYABLE_FAILED') {
    return '回写重试中'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return '回写已进入队列'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'pending') {
    return '回写请求已提交'
  }
  if (scheduleDraftState.publishResult) {
    return '回写请求状态'
  }
  return '回写前校验'
})

const currentDraftId = computed(() => scheduleDraftState.publishResult?.draftId || scheduleDraftState.draftId)

const publishRequestTitle = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '未通过回写前校验'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return '回写已完成'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '回写失败'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'RETRYABLE_FAILED') {
    return '回写重试中'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return '回写已进入队列'
  }
  return '回写请求已提交'
})

const publishRequestDetail = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '当前结果版本停在回写前校验环节'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return '当前结果版本回写已完成'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '当前结果版本回写失败'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'RETRYABLE_FAILED') {
    return '当前结果版本回写重试中'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return '当前结果版本已进入回写队列'
  }
  return '当前结果版本的回写请求已提交'
})

const showConfirmButton = computed(
  () => !scheduleDraftState.publishResult || scheduleDraftState.publishResult.writebackStatus === 'TERMINAL_FAILED'
)

const confirmButtonLabel = computed(() => {
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    if (scheduleDraftState.publishResult.status === 'validation_failed') {
      return '重新提交结果版本'
    }
    return '重新发起回写'
  }
  if (scheduleDraftState.publishResult) {
    return '回写请求已提交'
  }
  return '确认回写'
})

const executionModeLabelPrefix = computed(() => {
  if (scheduleDraftState.publishResult?.status === 'validation_failed') {
    return '处理结果'
  }
  return '执行方式'
})

const executionModeLabel = computed(() => {
  if (scheduleDraftState.publishResult?.status === 'validation_failed') {
    return '未执行回写'
  }
  return '批量回写'
})

const confirmStatusLabelPrefix = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '校验状态'
  }
  return '确认状态'
})

const confirmStatusLabel = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '未通过'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return '已完成'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '已失败'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'RETRYABLE_FAILED') {
    return '重试中'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return '已进入队列'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'pending') {
    return '回写请求已提交'
  }
  if (scheduleDraftState.publishResult?.status === 'validated') {
    return '已确认，待回写'
  }
  return '待处理'
})

const confirmStatusDetail = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '当前结果版本停在回写前校验环节'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return '当前结果版本确认状态已完成'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '当前结果版本确认状态已失败'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'RETRYABLE_FAILED') {
    return '当前结果版本确认状态重试中'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return '当前结果版本确认状态已进入回写队列'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'pending') {
    return '当前结果版本确认状态为回写请求已提交，等待进入回写队列'
  }
  if (scheduleDraftState.publishResult?.status === 'validated') {
    return '当前结果版本已确认，待回写'
  }
  return '当前结果版本确认状态待处理'
})

const publishStatusLabel = computed(() => {
  if (scheduleDraftState.publishResult?.status === 'validated') {
    return '已通过'
  }
  return '未通过'
})

const publishStatusDetail = computed(() => {
  if (scheduleDraftState.publishResult?.status === 'validated') {
    return '当前结果版本已通过回写前校验'
  }
  return '当前结果版本停在回写前校验环节'
})

const writebackStatusLabel = computed(() => {
  if (scheduleDraftState.publishResult?.status === 'validation_failed') {
    return '未执行回写'
  }
  switch (scheduleDraftState.publishResult?.writebackStatus) {
    case 'pending':
      return '回写请求已提交'
    case 'submitted':
      return '已进入回写队列'
    case 'SUCCEEDED':
      return '回写成功'
    case 'RETRYABLE_FAILED':
      return '回写重试中'
    case 'TERMINAL_FAILED':
      return '回写失败'
    default:
      return scheduleDraftState.publishResult?.writebackStatus ?? ''
  }
})

const writebackStatusDetail = computed(() => {
  if (scheduleDraftState.publishResult?.status === 'validation_failed') {
    return '当前结果版本停在回写前校验环节，未执行回写'
  }
  switch (scheduleDraftState.publishResult?.writebackStatus) {
    case 'pending':
      return '当前结果版本的回写请求已提交，等待进入回写队列'
    case 'submitted':
      return '当前结果版本已进入回写队列，等待执行回写'
    case 'SUCCEEDED':
      return '当前结果版本已完成回写'
    case 'RETRYABLE_FAILED':
      return '当前结果版本将在后台继续重试回写'
    case 'TERMINAL_FAILED':
      return '当前结果版本回写失败，需人工处理'
    default:
      if (scheduleDraftState.publishResult?.retryable) {
        return '当前结果版本将在后台继续重试回写'
      }
      return '系统将继续跟踪当前结果版本的回写状态'
  }
})

const checklistMessageLabel = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '当前结果版本停在回写前校验环节，未生成执行清单'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '当前结果版本未生成可执行回写清单'
  }
  return '系统已生成当前结果版本的回写清单'
})

const executionMessageLabel = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '当前结果版本停在回写前校验环节，未进入回写执行环节'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '当前结果版本不会继续执行自动回写'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'RETRYABLE_FAILED') {
    return '当前结果版本将按重试策略继续回写'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return '当前结果版本已按回写清单完成回写'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return '当前结果版本已进入回写队列，等待执行回写'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'pending') {
    return '当前结果版本回写请求已提交，等待进入回写队列后执行'
  }
  return '系统将按当前结果版本执行回写'
})

const traceabilityMessageLabel = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return `可按结果版本 ${scheduleDraftState.publishResult.draftId} 追溯本次回写前校验结果`
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'pending') {
    return `可按结果版本 ${scheduleDraftState.publishResult.draftId} 追溯本次回写请求`
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return `可按结果版本 ${scheduleDraftState.publishResult.draftId} 追溯本次回写队列状态`
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return `可按结果版本 ${scheduleDraftState.publishResult.draftId} 追溯本次回写结果`
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return `可按结果版本 ${scheduleDraftState.publishResult.draftId} 追溯本次回写失败结果`
  }
  return `可按结果版本 ${scheduleDraftState.publishResult?.draftId ?? ''} 追溯本次回写`
})

const followupMessageLabel = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '请处理回写前校验未通过原因后重新提交结果版本'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '请处理失败原因后重新发起回写'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return '当前结果版本回写结果已生成'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'RETRYABLE_FAILED') {
    return '可关闭弹窗并等待下一次自动重试'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'pending') {
    return '可关闭弹窗并等待回写请求进入队列'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return '可关闭弹窗并等待队列执行回写'
  }
  return '可关闭弹窗并等待当前结果版本的回写结果'
})

const auditTrailMessageLabel = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '系统已记录本次回写前校验留痕'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'pending') {
    return '系统已记录本次回写请求留痕'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return '系统已记录本次回写队列状态留痕'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return '系统已记录本次回写结果留痕'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '系统已记录本次回写失败留痕'
  }
  return '系统已记录本次回写留痕'
})

const destinationMessageLabel = computed(() => {
  if (scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED') {
    return '当前结果版本未回写至金蝶云星空'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'SUCCEEDED') {
    return '当前结果版本已回写至金蝶云星空'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'RETRYABLE_FAILED') {
    return '当前结果版本仍将回写至金蝶云星空'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'pending') {
    return '当前结果版本将在进入回写队列后回写至金蝶云星空'
  }
  if (scheduleDraftState.publishResult?.writebackStatus === 'submitted') {
    return '当前结果版本排队回写至金蝶云星空'
  }
  return '当前结果版本将回写至金蝶云星空'
})

const publishMessageLabelPrefix = computed(() => {
  if (
    scheduleDraftState.publishResult?.writebackStatus === 'TERMINAL_FAILED' &&
    scheduleDraftState.publishResult?.status === 'validation_failed'
  ) {
    return '校验说明'
  }
  return '回写说明'
})

const publishMessageLabel = computed(() => scheduleDraftState.publishResult?.message ?? '')

const retryAttemptLabel = computed(() => {
  if (scheduleDraftState.publishResult?.status === 'validation_failed') {
    return ''
  }
  const attemptCount = scheduleDraftState.publishResult?.attemptCount
  const maxAttempts = scheduleDraftState.publishResult?.maxAttempts
  if (attemptCount == null || maxAttempts == null) {
    return ''
  }
  return `${attemptCount}/${maxAttempts}`
})

const nextRetryAtLabel = computed(() => {
  if (scheduleDraftState.publishResult?.status === 'validation_failed') {
    return ''
  }
  return scheduleDraftState.publishResult?.nextRetryAt ?? ''
})
</script>

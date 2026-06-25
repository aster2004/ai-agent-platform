<template>
  <div class="thinking-block">
    <button class="thinking-header" @click="expanded = !expanded">
      <span class="thinking-icon">💡</span>
      <span class="thinking-title">{{ title }}</span>
      <DownOutlined class="chevron" :class="{ expanded }" />
    </button>
    <div v-show="expanded" class="thinking-body">
      <div v-if="steps.length" class="step-list">
        <div v-for="(step, idx) in steps" :key="step.code" class="step-item">
          <span class="step-dot" :class="stepStatusClass(step.code, idx)" />
          <div class="step-content">
            <span class="step-label">{{ step.label }}</span>
            <span v-if="stepDescriptions[step.code]" class="step-desc">
              {{ stepDescriptions[step.code] }}
            </span>
          </div>
        </div>
      </div>
      <div v-if="summary" class="thinking-summary">
        <p v-if="summary"><strong>需求摘要：</strong>{{ summary }}</p>
        <p v-if="strategy"><strong>策略：</strong>{{ strategy }}</p>
        <p v-if="validated != null">
          <strong>校验：</strong>
          <span :class="validated ? 'ok' : 'fail'">{{ validated ? '通过' : '未通过' }}</span>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { DownOutlined } from '@ant-design/icons-vue'
import type { WorkflowStepCode } from '@/types/codegen'

const props = defineProps<{
  running?: boolean
  failed?: boolean
  activeStep: WorkflowStepCode
  stepDescriptions?: Partial<Record<WorkflowStepCode, string>>
  summary?: string
  strategy?: string
  validated?: boolean
  durationMs?: number
}>()

const expanded = ref(true)

const steps = [
  { code: 'analyze' as const, label: '分析需求' },
  { code: 'strategy' as const, label: '选择策略' },
  { code: 'generate' as const, label: '生成代码' },
  { code: 'validate' as const, label: '校验结果' },
  { code: 'done' as const, label: '完成' },
]

const stepDescriptions = computed(() => props.stepDescriptions ?? {})

const title = computed(() => {
  if (props.running) return '工作流执行中...'
  const sec = props.durationMs ? (props.durationMs / 1000).toFixed(1) : '2'
  return `已深度思考（用时 ${sec} 秒）`
})

const activeIndex = computed(() => {
  const idx = steps.findIndex((s) => s.code === props.activeStep)
  return idx >= 0 ? idx : 0
})

function stepStatusClass(code: WorkflowStepCode, idx: number): string {
  if (props.failed && idx === activeIndex.value) return 'error'
  if (idx < activeIndex.value) return 'done'
  if (idx === activeIndex.value && !props.running) return 'done'
  if (idx === activeIndex.value && props.running) return 'active'
  return 'pending'
}
</script>

<style scoped>
.thinking-block {
  margin-bottom: 12px;
  border-radius: 10px;
  background: #f7f8fa;
  border: 1px solid #eceef2;
}

.thinking-header {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: #333;
}

.thinking-icon {
  font-size: 16px;
}

.thinking-title {
  flex: 1;
  font-weight: 500;
}

.chevron {
  font-size: 12px;
  color: #999;
  transition: transform 0.2s;
}

.chevron.expanded {
  transform: rotate(180deg);
}

.thinking-body {
  padding: 0 14px 14px 14px;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.step-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.step-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
}

.step-dot.done {
  background: #1677ff;
}

.step-dot.active {
  background: #1677ff;
  box-shadow: 0 0 0 3px rgba(22, 119, 255, 0.2);
}

.step-dot.pending {
  background: #d9d9d9;
}

.step-dot.error {
  background: #ff4d4f;
}

.step-content {
  flex: 1;
}

.step-label {
  font-size: 13px;
  color: #333;
  font-weight: 500;
}

.step-desc {
  display: block;
  font-size: 12px;
  color: #888;
  margin-top: 2px;
}

.thinking-summary {
  font-size: 13px;
  color: #555;
  line-height: 1.7;
}

.thinking-summary p {
  margin: 4px 0;
}

.ok {
  color: #52c41a;
}

.fail {
  color: #ff4d4f;
}
</style>

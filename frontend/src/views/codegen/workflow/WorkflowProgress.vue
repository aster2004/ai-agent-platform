<template>
  <a-steps :current="currentIndex" :status="stepStatus" size="small">
    <a-step v-for="step in steps" :key="step.code" :title="step.label" :description="stepDescriptions[step.code]" />
  </a-steps>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowStepCode } from '@/types/codegen'

const props = defineProps<{
  activeStep: WorkflowStepCode
  failed?: boolean
  stepDescriptions?: Partial<Record<WorkflowStepCode, string>>
}>()

const steps = [
  { code: 'analyze' as const, label: '分析需求' },
  { code: 'prd' as const, label: '生成需求文档' },
  { code: 'strategy' as const, label: '选择策略' },
  { code: 'generate' as const, label: '生成代码' },
  { code: 'validate' as const, label: '校验结果' },
  { code: 'done' as const, label: '完成' },
]

const stepDescriptions = computed(() => props.stepDescriptions ?? {})

const currentIndex = computed(() => {
  const idx = steps.findIndex((s) => s.code === props.activeStep)
  return idx >= 0 ? idx : 0
})

const stepStatus = computed(() => (props.failed ? 'error' : 'process'))
</script>

<template>
  <div class="workflow-msg">
    <ThinkingBlock
      :running="state.running"
      :failed="state.failed"
      :active-step="state.activeStep"
      :step-descriptions="state.stepDescriptions"
      :summary="state.summary"
      :strategy="state.strategy"
      :validated="state.validated"
      :duration-ms="state.durationMs"
    />

    <div v-if="state.error" class="error-tip">{{ state.error }}</div>

    <CodeBlock
      v-for="file in state.files"
      :key="file.path"
      :content="file.content"
      :filename="file.path"
      :lang="detectLang(file.path, file.content)"
    />
  </div>
</template>

<script setup lang="ts">
import ThinkingBlock from './ThinkingBlock.vue'
import CodeBlock from './CodeBlock.vue'
import type { WorkflowMessageState } from '@/types/codegen'

defineProps<{
  state: WorkflowMessageState
}>()

function detectLang(path: string, content: string): string {
  if (path.includes('.')) return path.split('.').pop()?.toLowerCase() ?? 'text'
  if (content.includes('<html') || content.includes('<!DOCTYPE')) return 'html'
  if (content.includes('<template>')) return 'vue'
  return 'text'
}
</script>

<style scoped>
.workflow-msg {
  margin: 16px 0;
  max-width: 100%;
}

.error-tip {
  margin-top: 8px;
  font-size: 13px;
  color: #ff4d4f;
}
</style>

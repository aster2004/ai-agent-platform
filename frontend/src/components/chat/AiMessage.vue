<template>
  <div class="ai-msg">
    <ThinkingBlock
      v-if="parsed.type === 'workflow'"
      :running="false"
      :failed="parsed.validated === false"
      :active-step="parsed.activeStep ?? 'done'"
      :step-descriptions="parsed.stepDescriptions"
      :summary="parsed.summary"
      :strategy="parsed.strategy"
      :validated="parsed.validated"
      :duration-ms="parsed.durationMs"
    />

    <div v-if="parsed.plainText && parsed.type === 'text'" class="text-content">
      {{ parsed.plainText }}
    </div>

    <CodeBlock
      v-for="file in parsed.files"
      :key="file.path"
      :content="file.content"
      :filename="file.path"
      :lang="detectLang(file.path, file.content)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ThinkingBlock from './ThinkingBlock.vue'
import CodeBlock from './CodeBlock.vue'
import { parseAiMessage } from '@/utils/parseAiMessage'

const props = defineProps<{
  content: string
}>()

const parsed = computed(() => parseAiMessage(props.content))

function detectLang(path: string, content: string): string {
  if (path.includes('.')) {
    const ext = path.split('.').pop()?.toLowerCase()
    return ext ?? 'text'
  }
  if (content.includes('<html') || content.includes('<!DOCTYPE')) return 'html'
  if (content.includes('<template>')) return 'vue'
  return 'text'
}
</script>

<style scoped>
.ai-msg {
  margin: 16px 0;
  max-width: 100%;
}

.text-content {
  font-size: 14px;
  line-height: 1.7;
  color: #333;
  padding: 4px 0;
}
</style>

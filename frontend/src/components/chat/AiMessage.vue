<template>
  <div class="ai-msg">
    <p v-if="parsed.type === 'workflow' && statusText" class="status-text">{{ statusText }}</p>

    <WorkflowTaskList
      v-if="parsed.type === 'workflow' && parsed.tasks?.length"
      :tasks="parsed.tasks"
    />

    <ThinkingBlock
      v-if="parsed.type === 'workflow' && showThinking"
      :running="false"
      :failed="parsed.validated === false"
      :active-step="parsed.activeStep ?? 'done'"
      :step-descriptions="parsed.stepDescriptions"
      :summary="parsed.summary"
      :strategy="parsed.strategy"
      :validated="parsed.validated"
      :duration-ms="parsed.durationMs"
    />

    <PrdDocumentCard
      v-if="parsed.type === 'workflow' && parsed.prdContent"
      @open="$emit('open-prd', parsed.prdContent!, parsed.generateId)"
      @download="downloadPrd(parsed.prdContent!)"
    />

    <div
      v-if="parsed.type === 'workflow' && parsed.phase === 'await_confirm'"
      class="action-bar"
    >
      <a-button @click="$emit('open-prd', parsed.prdContent!, parsed.generateId)">
        编辑需求文档
      </a-button>
      <a-button type="primary" @click="$emit('generate', parsed.generateId, parsed.prdContent)">
        立即创作
      </a-button>
    </div>

    <div
      v-if="parsed.type === 'workflow' && parsed.phase === 'done' && parsed.files.length"
      class="action-bar"
    >
      <a-button type="primary" @click="$emit('preview', parsed.files)">
        查看项目
      </a-button>
    </div>

    <div v-if="parsed.plainText && parsed.type === 'text'" class="text-content">
      {{ parsed.plainText }}
    </div>

    <CodeBlock
      v-for="file in parsed.files"
      :key="file.path"
      :content="file.content"
      :filename="file.path"
      :lang="detectLang(file.path, file.content)"
      :all-files="parsed.files"
      @preview="$emit('preview', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ThinkingBlock from './ThinkingBlock.vue'
import WorkflowTaskList from './WorkflowTaskList.vue'
import PrdDocumentCard from './PrdDocumentCard.vue'
import CodeBlock from './CodeBlock.vue'
import { parseAiMessage } from '@/utils/parseAiMessage'

const props = defineProps<{
  content: string
}>()

defineEmits<{
  'open-prd': [content: string, generateId?: number]
  generate: [generateId?: number, prdContent?: string]
  preview: [files: import('@/types/codegen').CodeFile[]]
}>()

const parsed = computed(() => parseAiMessage(props.content))

const showThinking = computed(() =>
  parsed.value.phase === 'generating' || parsed.value.phase === 'done' || parsed.value.files.length > 0,
)

const statusText = computed(() => {
  if (parsed.value.phase === 'await_confirm') return '已根据您的需求生成产品文档。'
  if (parsed.value.phase === 'done') return '应用已生成完成。'
  return ''
})

function detectLang(path: string, content: string): string {
  if (path.includes('.')) {
    const ext = path.split('.').pop()?.toLowerCase()
    return ext ?? 'text'
  }
  if (content.includes('<html') || content.includes('<!DOCTYPE')) return 'html'
  if (content.includes('<template>')) return 'vue'
  return 'text'
}

function downloadPrd(content: string) {
  const blob = new Blob([content], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '需求文档.md'
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.ai-msg {
  margin: 16px 0;
  max-width: 100%;
}

.status-text {
  font-size: 14px;
  color: #333;
  margin-bottom: 12px;
  line-height: 1.6;
}

.action-bar {
  display: flex;
  gap: 10px;
  margin: 12px 0;
}

.text-content {
  font-size: 14px;
  line-height: 1.7;
  color: #333;
  padding: 4px 0;
}
</style>


<template>

  <div class="workflow-msg">

    <p v-if="statusText" class="status-text">{{ statusText }}</p>



    <WorkflowTaskList :tasks="state.tasks" />



    <ThinkingBlock

      v-if="showThinking"

      :running="state.running && state.phase === 'generating'"

      :failed="state.failed"

      :active-step="state.activeStep"

      :step-descriptions="state.stepDescriptions"

      :summary="state.summary"

      :strategy="state.strategy"

      :validated="state.validated"

      :duration-ms="state.durationMs"

    />



    <PrdDocumentCard

      v-if="state.prdContent && state.phase !== 'analyzing'"

      @open="$emit('open-prd')"

      @download="downloadPrd"

    />



    <div v-if="state.phase === 'await_confirm' && !state.running" class="action-bar">

      <a-button @click="$emit('open-prd')">编辑需求文档</a-button>

      <a-button type="primary" @click="$emit('generate')">立即创作</a-button>

    </div>



    <div v-if="state.phase === 'generating' && state.running" class="generating-banner">

      <a-spin size="small" />

      <span>应用生成中...</span>

    </div>



    <div v-if="state.error" class="error-tip">{{ state.error }}</div>



    <div v-if="state.phase === 'done' && state.files.length" class="action-bar">
      <a-button type="primary" @click="$emit('preview', state.files)">查看项目</a-button>
    </div>



    <CodeBlock

      v-for="file in state.files"

      :key="file.path"

      :content="file.content"

      :filename="file.path"

      :lang="detectLang(file.path, file.content)"
      :all-files="state.files"
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

import type { WorkflowMessageState } from '@/types/codegen'



const props = defineProps<{

  state: WorkflowMessageState

}>()



defineEmits<{
  'open-prd': []
  generate: []
  preview: [files: import('@/types/codegen').CodeFile[]]
}>()



const showThinking = computed(() =>

  props.state.phase === 'generating' || props.state.phase === 'done',

)



const statusText = computed(() => {

  if (props.state.phase === 'analyzing' && props.state.running) {

    return '好的，正在为你分析需求并生成产品文档，请稍候~'

  }

  if (props.state.phase === 'await_confirm' && !props.state.running) {

    return '已根据您的需求生成产品文档。'

  }

  if (props.state.phase === 'generating' && props.state.running) {

    return '正在根据需求文档生成应用...'

  }

  if (props.state.phase === 'done' && !props.state.failed) {

    return '应用已生成完成。'

  }

  return ''

})



function detectLang(path: string, content: string): string {

  if (path.includes('.')) return path.split('.').pop()?.toLowerCase() ?? 'text'

  if (content.includes('<html') || content.includes('<!DOCTYPE')) return 'html'

  if (content.includes('<template>')) return 'vue'

  return 'text'

}



function downloadPrd() {

  if (!props.state.prdContent) return

  const blob = new Blob([props.state.prdContent], { type: 'text/markdown' })

  const url = URL.createObjectURL(blob)

  const a = document.createElement('a')

  a.href = url

  a.download = '需求文档.md'

  a.click()

  URL.revokeObjectURL(url)

}

</script>



<style scoped>

.workflow-msg {

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



.generating-banner {

  display: flex;

  align-items: center;

  gap: 8px;

  padding: 12px 16px;

  margin: 12px 0;

  border-radius: 10px;

  background: #f0f5ff;

  color: #1677ff;

  font-size: 14px;

}



.error-tip {

  margin-top: 8px;

  font-size: 13px;

  color: #ff4d4f;

}

</style>


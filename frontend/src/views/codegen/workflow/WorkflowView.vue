<template>
  <div class="workflow-page">
    <a-row :gutter="24">
      <a-col :span="10">
        <a-card title="AI 工作流生成">
          <a-alert
            type="info"
            show-icon
            message="LangGraph4j 四节点编排"
            description="分析 → 策略 → 生成 → 校验，支持 Vue 多文件工程与 Tool Calling"
            style="margin-bottom: 16px"
          />
          <a-textarea
            v-model:value="prompt"
            placeholder="例如：创建一个 Vue 3 待办事项应用，包含添加、删除、完成功能..."
            :rows="10"
            :maxlength="5000"
            show-count
          />
          <div class="actions">
            <a-button type="primary" :loading="running" @click="handleRunStream">
              启动工作流
            </a-button>
            <a-button :loading="running" @click="handleRunSync">同步执行</a-button>
            <a-button @click="handleClear">清空</a-button>
          </div>
        </a-card>

        <a-card title="执行进度" style="margin-top: 16px">
          <WorkflowProgress
            :active-step="activeStep"
            :failed="hasError"
            :step-descriptions="stepDescriptions"
          />
        </a-card>
      </a-col>

      <a-col :span="14">
        <a-card title="生成结果">
          <template v-if="summary">
            <a-descriptions bordered size="small" :column="1" style="margin-bottom: 16px">
              <a-descriptions-item label="需求摘要">{{ summary }}</a-descriptions-item>
              <a-descriptions-item label="策略">{{ strategy }}</a-descriptions-item>
              <a-descriptions-item label="校验">
                <a-tag :color="validated ? 'success' : 'error'">
                  {{ validated ? '通过' : '未通过' }}
                </a-tag>
              </a-descriptions-item>
            </a-descriptions>
          </template>

          <a-tabs v-if="files.length">
            <a-tab-pane v-for="file in files" :key="file.path" :tab="file.path">
              <pre class="file-content">{{ file.content }}</pre>
            </a-tab-pane>
          </a-tabs>
          <a-empty v-else description="工作流完成后在此展示生成的文件" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import WorkflowProgress from './WorkflowProgress.vue'
import { executeWorkflow, executeWorkflowStream } from '@/api/codegen'
import type { CodeFile, WorkflowStepCode, WorkflowStepEvent } from '@/types/codegen'

const prompt = ref('创建一个 Vue 3 计数器页面，带加减按钮和当前数值展示')
const running = ref(false)
const activeStep = ref<WorkflowStepCode>('analyze')
const stepDescriptions = reactive<Partial<Record<WorkflowStepCode, string>>>({})
const summary = ref('')
const strategy = ref('')
const validated = ref(false)
const hasError = ref(false)
const files = ref<CodeFile[]>([])

function resetState() {
  activeStep.value = 'analyze'
  summary.value = ''
  strategy.value = ''
  validated.value = false
  hasError.value = false
  files.value = []
  Object.keys(stepDescriptions).forEach((k) => delete stepDescriptions[k as WorkflowStepCode])
}

function applyResult(data: {
  summary?: string
  strategy?: string
  validated?: boolean
  error?: string
  codeFiles?: CodeFile[]
}) {
  if (data.summary) summary.value = data.summary
  if (data.strategy) strategy.value = data.strategy
  if (data.validated != null) validated.value = data.validated
  if (data.codeFiles) files.value = data.codeFiles
  if (data.error) {
    hasError.value = true
    message.error(data.error)
  }
}

function handleEvent(event: WorkflowStepEvent) {
  if (event.step) {
    activeStep.value = event.step
  }
  if (event.message && event.step) {
    stepDescriptions[event.step] = event.message
  }
  if (event.type === 'done' && event.data && 'codeFiles' in event.data) {
    applyResult(event.data as import('@/types/codegen').WorkflowResult)
    activeStep.value = 'done'
    message.success('工作流执行完成')
  }
  if (event.type === 'error') {
    hasError.value = true
    message.error(event.message || '工作流执行失败')
  }
}

async function handleRunStream() {
  if (!prompt.value.trim()) {
    message.warning('请输入需求描述')
    return
  }
  running.value = true
  resetState()
  try {
    const response = await executeWorkflowStream({ prompt: prompt.value, appId: 1 })
    const reader = response.body?.getReader()
    const decoder = new TextDecoder()
    if (!reader) throw new Error('无法读取流式响应')

    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const chunks = buffer.split('\n\n')
      buffer = chunks.pop() ?? ''
      for (const chunk of chunks) {
        parseSseChunk(chunk)
      }
    }
    if (buffer.trim()) {
      parseSseChunk(buffer)
    }
  } catch (e: any) {
    hasError.value = true
    message.error(e.message || '工作流执行失败')
  } finally {
    running.value = false
  }
}

function parseSseChunk(chunk: string) {
  const dataLine = chunk.split('\n').find((line) => line.startsWith('data:'))
  if (!dataLine) return
  const json = dataLine.replace(/^data:\s?/, '')
  try {
    handleEvent(JSON.parse(json))
  } catch {
    // ignore malformed chunk
  }
}

async function handleRunSync() {
  if (!prompt.value.trim()) {
    message.warning('请输入需求描述')
    return
  }
  running.value = true
  resetState()
  try {
    const res = await executeWorkflow({ prompt: prompt.value, appId: 1 })
    applyResult(res.data)
    activeStep.value = 'done'
    stepDescriptions.done = '同步执行完成'
    message.success('工作流执行完成')
  } catch (e: any) {
    hasError.value = true
    message.error(e.message || '工作流执行失败')
  } finally {
    running.value = false
  }
}

function handleClear() {
  prompt.value = ''
  resetState()
}
</script>

<style scoped>
.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}

.file-content {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  max-height: 520px;
  overflow: auto;
}
</style>

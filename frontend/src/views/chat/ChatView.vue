<template>
  <div class="chat-container">
    <div v-if="!showSidebar" class="floating-toolbar">
      <a-tooltip placement="bottom" title="展开边栏" color="#1f1f1f">
        <button class="floating-btn" @click="showSidebar = true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="3" ry="3"></rect>
            <line x1="9" y1="3" x2="9" y2="21"></line>
          </svg>
        </button>
      </a-tooltip>
      <a-tooltip placement="bottom" title="开启新对话" color="#1f1f1f">
        <button class="floating-btn" @click="handleCreateSession">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="16"></line>
            <line x1="8" y1="12" x2="16" y2="12"></line>
          </svg>
        </button>
      </a-tooltip>
    </div>

    <div class="sidebar-wrapper" :class="{ 'sidebar-collapse': !showSidebar }">
      <SessionSidebar
          :session-list="sessionList"
          :current-session-id="activeSession"
          @change-session="switchSession"
          @create-session="handleCreateSession"
          @delete-session="handleDeleteSession"
          @toggle-sidebar="showSidebar = false"
      />
    </div>

    <div
      class="main-area"
      v-if="activeSession"
      :class="{ 'workspace-mode': workspaceMode, 'with-preview': previewVisible && !workspaceMode }"
      ref="mainAreaRef"
    >
      <template v-if="workspaceMode">
        <div class="col-chat" :style="{ width: `${columnWidths.chat}px` }">
          <div class="msg-list workspace-msg-list" ref="msgListRef">
            <div v-if="loading" class="loading-tip">加载中...</div>
            <template v-for="item in msgList" :key="item.id">
              <ChatMessage
                v-if="item.messageType === 'user'"
                :msg="item"
                @resend="handleResend"
              />
              <AiMessage
                v-else
                :content="item.content"
                @open-prd="openPrdEditor"
                @generate="handleGenerateFromHistory"
                @preview="openWorkspace"
              />
            </template>
            <WorkflowMessage
              v-if="workflowState"
              :state="workflowState"
              @open-prd="openPrdEditor(workflowState.prdContent ?? '', workflowState.generateId)"
              @generate="handleGenerateApp"
              @preview="openWorkspace"
            />
            <StreamingMessage v-if="streamingContent" :content="streamingContent" />
            <div v-if="generating && !streamingContent && !workflowState" class="loading-tip">正在生成...</div>
          </div>
          <div class="chat-input-wrap">
            <ChatInput ref="chatInputRef" compact @send="handleSend" :is-home="false" />
          </div>
        </div>

        <ColumnResizer @start="beginResize(0, $event)" />

        <ProjectWorkspace
          :app-id="APP_ID"
          :files="previewFiles"
          :title="previewTitle"
          :tree-width="columnWidths.tree"
          :generating="generating || (workflowState?.phase === 'generating' && workflowState.running)"
          @resize-tree-start="beginResize(1, $event)"
        />
      </template>

      <template v-else>
        <div class="msg-wrap">
          <div class="msg-list" ref="msgListRef">
            <div v-if="loading" class="loading-tip">加载中...</div>
            <template v-for="item in msgList" :key="item.id">
              <ChatMessage
                v-if="item.messageType === 'user'"
                :msg="item"
                @resend="handleResend"
              />
              <AiMessage
                v-else
                :content="item.content"
                @open-prd="openPrdEditor"
                @generate="handleGenerateFromHistory"
                @preview="openWorkspace"
              />
            </template>
            <WorkflowMessage
              v-if="workflowState"
              :state="workflowState"
              @open-prd="openPrdEditor(workflowState.prdContent ?? '', workflowState.generateId)"
              @generate="handleGenerateApp"
              @preview="openWorkspace"
            />
            <StreamingMessage v-if="streamingContent" :content="streamingContent" />
            <div v-if="generating && !streamingContent && !workflowState" class="loading-tip">正在生成...</div>
          </div>
          <ChatInput ref="chatInputRef" @send="handleSend" :is-home="false" />
        </div>

        <WorkflowPreviewPanel
          v-if="previewVisible"
          embedded
          :app-id="APP_ID"
          :files="previewFiles"
          :title="previewTitle"
          @close="previewVisible = false"
        />
      </template>
    </div>

    <div class="home-wrap" v-else>
      <h2 class="home-title">AI对话助手</h2>
      <ChatInput ref="homeInputRef" @send="handleHomeSend" :is-home="true" />
    </div>

    <PrdEditorPanel
      v-if="prdEditorVisible"
      :content="prdEditorContent"
      :saving="prdSaving"
      :generating="generating"
      @close="prdEditorVisible = false"
      @save="handleSavePrd"
      @generate="handleGenerateFromEditor"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import SessionSidebar from '@/components/chat/SessionSidebar.vue'
import ChatMessage from '@/components/chat/ChatMessage.vue'
import AiMessage from '@/components/chat/AiMessage.vue'
import WorkflowMessage from '@/components/chat/WorkflowMessage.vue'
import StreamingMessage from '@/components/chat/StreamingMessage.vue'
import PrdEditorPanel from '@/components/chat/PrdEditorPanel.vue'
import WorkflowPreviewPanel from '@/components/chat/WorkflowPreviewPanel.vue'
import ProjectWorkspace from '@/components/chat/ProjectWorkspace.vue'
import ColumnResizer from '@/components/chat/ColumnResizer.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { useColumnResize } from '@/composables/useColumnResize'
import { getSessionList, getHistoryMsg, saveChatMessage, saveAiMessage, deleteSession, createSession } from '@/api/chat'
import {
  generateCode,
  generateCodeStream,
  analyzeWorkflowStream,
  continueWorkflowStream,
  updateWorkflowPrd,
} from '@/api/codegen'
import { readSseStream } from '@/utils/codegenStream'
import { buildWorkflowContent, buildCodeContent } from '@/utils/parseAiMessage'
import type { ChatSaveReq, ChatSession, ChatMessage as ChatMessageType } from '@/types/chat'
import type {
  GenerationOutput,
  WorkflowMessageState,
  WorkflowStepEvent,
  WorkflowTask,
  WorkflowResult,
  CodeFile,
} from '@/types/codegen'

const sessionList = ref<ChatSession[]>([])
const activeSession = ref<number | null>(null)
const msgList = ref<ChatMessageType[]>([])
const loading = ref(false)
const generating = ref(false)
const msgListRef = ref<HTMLDivElement | null>(null)
const chatInputRef = ref<InstanceType<typeof ChatInput> | null>(null)
const homeInputRef = ref<InstanceType<typeof ChatInput> | null>(null)
const APP_ID = 1
const DEFAULT_GENERATE_TYPE = 'HTML' as const

const showSidebar = ref(true)
const pendingNewSessionId = ref<number | null>(null)
const workflowState = ref<WorkflowMessageState | null>(null)
const streamingContent = ref('')

const prdEditorVisible = ref(false)
const prdEditorContent = ref('')
const prdEditorGenerateId = ref<number | undefined>()
const prdSaving = ref(false)

const previewVisible = ref(false)
const workspaceMode = ref(false)
const previewFiles = ref<CodeFile[]>([])
const previewTitle = ref('应用预览')
const mainAreaRef = ref<HTMLElement | null>(null)

const MIN_CHAT_WIDTH = 280
const MIN_TREE_WIDTH = 180
const MIN_PREVIEW_WIDTH = 320

const { widths: columnWidths, beginResize } = useColumnResize(
  () => mainAreaRef.value,
  { chat: 360, tree: 240 },
  { chat: MIN_CHAT_WIDTH, tree: MIN_TREE_WIDTH },
  MIN_PREVIEW_WIDTH,
)

onMounted(async () => {
  const res = await getSessionList()
  if (res.code === 200) {
    sessionList.value = res.data
  }
})

async function switchSession(id: number) {
  pendingNewSessionId.value = null
  activeSession.value = id
  msgList.value = []
  workflowState.value = null
  streamingContent.value = ''
  prdEditorVisible.value = false
  workspaceMode.value = false
  previewVisible.value = false
  await loadMsg(id)
}

async function loadMsg(sessionId: number) {
  loading.value = true
  try {
    const res = await getHistoryMsg(sessionId)
    if (res.code === 200) {
      msgList.value = res.data.list
      await scrollToBottom()
    }
  } finally {
    loading.value = false
  }
}

async function scrollToBottom() {
  await nextTick()
  if (msgListRef.value) {
    msgListRef.value.scrollTop = msgListRef.value.scrollHeight
  }
}

function createWorkflowState(): WorkflowMessageState {
  return {
    running: true,
    phase: 'analyzing',
    activeStep: 'analyze',
    stepDescriptions: {},
    failed: false,
    files: [],
    tasks: [],
  }
}

function handleWorkflowEvent(state: WorkflowMessageState, event: WorkflowStepEvent) {
  if (event.type === 'task' && event.data) {
    const task = event.data as WorkflowTask
    state.tasks = [...state.tasks, task]
    return
  }

  if (event.step) state.activeStep = event.step
  if (event.message && event.step) state.stepDescriptions[event.step] = event.message

  if (event.type === 'prd_ready' && event.data) {
    const data = event.data as WorkflowResult
    state.running = false
    state.phase = 'await_confirm'
    state.activeStep = 'prd_ready'
    state.summary = data.summary
    state.prdContent = data.prdContent
    state.generateId = data.generateId
    state.durationMs = data.durationMs
    if (data.tasks) state.tasks = data.tasks
    state.stepDescriptions.prd_ready = '已根据您的需求生成产品文档'
    message.success('需求文档已生成，请确认后点击「立即创作」')
    return
  }

  if (event.type === 'step' && state.phase === 'generating') {
    return
  }

  if (event.type === 'done' && event.data) {
    const data = event.data as WorkflowResult
    state.running = false
    state.phase = 'done'
    if (data.summary) state.summary = data.summary
    if (data.strategy) state.strategy = data.strategy
    if (data.validated != null) state.validated = data.validated
    const codeFiles = data.codeFiles ?? []
    if (codeFiles.length) {
      state.files = codeFiles
      if (workspaceMode.value) {
        previewFiles.value = codeFiles
      }
    }
    if (data.durationMs) state.durationMs = data.durationMs
    if (data.prdContent) state.prdContent = data.prdContent
    state.activeStep = 'done'
    state.stepDescriptions.done = '应用生成完成'
    message.success('应用生成完成')
  }

  if (event.type === 'error') {
    state.running = false
    state.failed = true
    state.error = event.message || '工作流执行失败'
    message.error(state.error)
  }
}

function openPrdEditor(content: string, generateId?: number) {
  prdEditorContent.value = content
  prdEditorGenerateId.value = generateId ?? workflowState.value?.generateId
  prdEditorVisible.value = true
}

async function handleSavePrd(content: string) {
  prdEditorContent.value = content
  const generateId = prdEditorGenerateId.value ?? workflowState.value?.generateId
  if (!generateId) {
    prdEditorContent.value = content
    if (workflowState.value) workflowState.value.prdContent = content
    message.success('已保存到本地')
    return
  }
  prdSaving.value = true
  try {
    await updateWorkflowPrd(generateId, content)
    prdEditorContent.value = content
    if (workflowState.value) workflowState.value.prdContent = content
    message.success('需求文档已保存')
  } catch (e: any) {
    message.error(formatError(e))
  } finally {
    prdSaving.value = false
  }
}

async function persistWorkflowMessage(sessionId: number, state: WorkflowMessageState) {
  const payload = buildWorkflowContent({
    phase: state.phase,
    generateId: state.generateId,
    summary: state.summary,
    prdContent: state.prdContent,
    strategy: state.strategy,
    validated: state.validated,
    error: state.error,
    durationMs: state.durationMs,
    activeStep: state.activeStep,
    stepDescriptions: state.stepDescriptions,
    tasks: state.tasks,
    files: state.files,
  })
  await saveAiMessage(sessionId, APP_ID, payload)
}

async function runDeepAnalyze(sessionId: number, content: string) {
  workflowState.value = createWorkflowState()
  streamingContent.value = ''
  await scrollToBottom()

  try {
    const response = await analyzeWorkflowStream({
      prompt: content,
      sessionId,
      appId: APP_ID,
      generateType: 'WORKFLOW',
    })
    await readSseStream(response, (eventName, data) => {
      if (eventName !== 'workflow') return
      try {
        handleWorkflowEvent(workflowState.value!, JSON.parse(data) as WorkflowStepEvent)
        scrollToBottom()
      } catch {
        // ignore malformed chunk
      }
    })

    const state = workflowState.value!
    if (state.phase === 'await_confirm') {
      await persistWorkflowMessage(sessionId, state)
      const listRes = await getSessionList()
      if (listRes.code === 200) sessionList.value = listRes.data
    }
  } catch (e: any) {
    if (workflowState.value) {
      workflowState.value.running = false
      workflowState.value.failed = true
      workflowState.value.error = formatError(e)
    }
    message.error(formatError(e))
  }
}

async function runContinueGenerate(generateId: number, sessionId: number) {
  if (!workflowState.value) {
    workflowState.value = {
      running: true,
      phase: 'generating',
      activeStep: 'strategy',
      stepDescriptions: {},
      failed: false,
      files: [],
      tasks: [],
      generateId,
      prdContent: prdEditorContent.value || undefined,
    }
  } else {
    workflowState.value.running = true
    workflowState.value.phase = 'generating'
    workflowState.value.activeStep = 'strategy'
    workflowState.value.failed = false
    workflowState.value.error = undefined
  }

  enterWorkspace([])
  prdEditorVisible.value = false
  await scrollToBottom()

  try {
    const response = await continueWorkflowStream(generateId)
    await readSseStream(response, (eventName, data) => {
      if (eventName !== 'workflow') return
      try {
        handleWorkflowEvent(workflowState.value!, JSON.parse(data) as WorkflowStepEvent)
        scrollToBottom()
      } catch {
        // ignore
      }
    })

    const state = workflowState.value!
    if (state.files.length) {
      previewFiles.value = state.files
    }
    await persistWorkflowMessage(sessionId, state)
    await loadMsg(sessionId)
    if (state.files.length) {
      openWorkspace(state.files)
    }
  } catch (e: any) {
    if (workflowState.value) {
      workflowState.value.running = false
      workflowState.value.failed = true
      workflowState.value.error = formatError(e)
    }
    message.error(formatError(e))
  } finally {
    workflowState.value = null
  }
}

function enterWorkspace(files: CodeFile[] = []) {
  showSidebar.value = false
  workspaceMode.value = true
  previewVisible.value = false
  previewFiles.value = files
  previewTitle.value = '应用预览'
}

function openWorkspace(files: CodeFile[]) {
  if (!files.length) return
  enterWorkspace(files)
}

async function resolveGenerateId(): Promise<number | undefined> {
  return prdEditorGenerateId.value ?? workflowState.value?.generateId
}

async function syncPrdBeforeGenerate(generateId: number) {
  const prd = prdEditorContent.value || workflowState.value?.prdContent
  if (!prd?.trim()) return
  await updateWorkflowPrd(generateId, prd)
}

async function handleGenerateApp() {
  const generateId = await resolveGenerateId()
  if (!generateId || !activeSession.value) {
    message.warning('请先生成需求文档')
    return
  }
  showSidebar.value = false
  generating.value = true
  try {
    await syncPrdBeforeGenerate(generateId)
    prdEditorVisible.value = false
    await runContinueGenerate(generateId, activeSession.value)
  } finally {
    generating.value = false
  }
}

async function handleGenerateFromEditor() {
  await handleGenerateApp()
}

async function handleGenerateFromHistory(generateId?: number, prdContent?: string) {
  if (!generateId || !activeSession.value) {
    message.warning('无法继续生成，请重新发起深度分析')
    return
  }
  prdEditorGenerateId.value = generateId
  if (prdContent) {
    prdEditorContent.value = prdContent
  }
  showSidebar.value = false
  generating.value = true
  try {
    await syncPrdBeforeGenerate(generateId)
    await runContinueGenerate(generateId, activeSession.value)
  } finally {
    generating.value = false
  }
}

async function runQuickGenerationSync(sessionId: number, content: string) {
  workflowState.value = null
  streamingContent.value = ''
  await scrollToBottom()

  try {
    const res = await generateCode({
      prompt: content,
      sessionId,
      appId: APP_ID,
      generateType: DEFAULT_GENERATE_TYPE,
    })
    const code = res.data?.codeContent
    if (code) {
      await saveAiMessage(sessionId, APP_ID, buildCodeContent(code))
    }
    await loadMsg(sessionId)
  } catch (e: any) {
    message.error(formatError(e))
  }
}

async function runQuickGeneration(sessionId: number, content: string) {
  workflowState.value = null
  streamingContent.value = ''
  await scrollToBottom()

  try {
    const response = await generateCodeStream({
      prompt: content,
      sessionId,
      appId: APP_ID,
      generateType: DEFAULT_GENERATE_TYPE,
    })
    await readSseStream(response, (eventName, data) => {
      if (eventName === 'code_chunk') {
        streamingContent.value += data
        scrollToBottom()
      }
      if (eventName === 'error') {
        throw new Error(data)
      }
    })
    if (streamingContent.value) {
      const payload = buildCodeContent(streamingContent.value)
      await saveAiMessage(sessionId, APP_ID, payload)
    }
    await loadMsg(sessionId)
  } catch (e: any) {
    message.error(formatError(e))
  } finally {
    streamingContent.value = ''
  }
}

function formatError(e: any): string {
  const msg = e?.message || ''
  if (msg.includes('Insufficient Balance')) return 'DeepSeek 账户余额不足，请充值后重试'
  return msg || '生成失败'
}

async function processGeneration(
  sessionId: number,
  content: string,
  mode: 'fast' | 'deep',
  output: GenerationOutput,
) {
  generating.value = true
  try {
    if (mode === 'deep') {
      await runDeepAnalyze(sessionId, content)
    } else if (output === 'sync') {
      await runQuickGenerationSync(sessionId, content)
    } else {
      await runQuickGeneration(sessionId, content)
    }
    const listRes = await getSessionList()
    if (listRes.code === 200) sessionList.value = listRes.data
  } finally {
    generating.value = false
  }
}

async function sendUserMessage(sessionId: number, content: string) {
  const params: ChatSaveReq = {
    sessionId,
    appId: APP_ID,
    messageType: 'user',
    content,
  }
  loading.value = true
  try {
    await saveChatMessage(params)
    await loadMsg(sessionId)
    const listRes = await getSessionList()
    if (listRes.code === 200) sessionList.value = listRes.data
  } finally {
    loading.value = false
  }
}

async function handleSend({ content, mode, output }: { content: string; mode: 'fast' | 'deep'; output: GenerationOutput }) {
  if (!activeSession.value || loading.value || generating.value) return
  await sendUserMessage(activeSession.value, content)
  await processGeneration(activeSession.value, content, mode, output)
}

async function handleHomeSend({ content, mode, output }: { content: string; mode: 'fast' | 'deep'; output: GenerationOutput }) {
  if (loading.value || generating.value) return

  loading.value = true
  try {
    let newId: number
    if (pendingNewSessionId.value) {
      newId = pendingNewSessionId.value
      pendingNewSessionId.value = null
    } else {
      const sessRes = await createSession(APP_ID)
      newId = sessRes.data.id
    }
    await saveChatMessage({
      sessionId: newId,
      appId: APP_ID,
      messageType: 'user',
      content,
    })
    activeSession.value = newId
    msgList.value = []
    await loadMsg(newId)
    const listRes = await getSessionList()
    if (listRes.code === 200) sessionList.value = listRes.data
  } finally {
    loading.value = false
  }

  await processGeneration(activeSession.value!, content, mode, output)
}

async function handleResend(content: string) {
  if (!activeSession.value || loading.value || generating.value) return
  const mode = chatInputRef.value?.getMode?.() ?? 'deep'
  const output = chatInputRef.value?.getOutput?.() ?? 'stream'
  await sendUserMessage(activeSession.value, content)
  await processGeneration(activeSession.value, content, mode, output)
}

async function handleCreateSession() {
  if (pendingNewSessionId.value) {
    const pending = sessionList.value.find(s => s.id === pendingNewSessionId.value)
    if (pending && pending.messageCount === 0) {
      activeSession.value = null
      msgList.value = []
      workflowState.value = null
      streamingContent.value = ''
      prdEditorVisible.value = false
      return
    }
  }

  const emptySession = sessionList.value.find(
    s => (!s.sessionTitle || s.sessionTitle === '新对话') && s.messageCount === 0,
  )
  if (emptySession) {
    pendingNewSessionId.value = emptySession.id
    activeSession.value = null
    msgList.value = []
    workflowState.value = null
    streamingContent.value = ''
    prdEditorVisible.value = false
    return
  }

  const res = await createSession(APP_ID)
  if (res.code === 200) {
    pendingNewSessionId.value = res.data.id
    activeSession.value = null
    msgList.value = []
    workflowState.value = null
    streamingContent.value = ''
    prdEditorVisible.value = false
    const listRes = await getSessionList()
    if (listRes.code === 200) sessionList.value = listRes.data
  }
}

async function handleDeleteSession(delId: number) {
  if (sessionList.value.length <= 1) {
    alert('至少保留一个会话，无法删除')
    return
  }
  await deleteSession(delId)
  sessionList.value = sessionList.value.filter(s => s.id !== delId)

  if (activeSession.value === delId) {
    activeSession.value = null
    msgList.value = []
    workflowState.value = null
    streamingContent.value = ''
    prdEditorVisible.value = false
  }
}
</script>

<style scoped>
.chat-container {
  display: flex;
  height: 79vh;
  overflow: hidden;
  position: relative;
  padding-left: 0;
  background: #fff;
}

.floating-toolbar {
  position: absolute;
  top: 16px;
  left: 16px;
  display: flex;
  align-items: center;
  gap: 4px;
  z-index: 99;
}

.floating-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  background: #f7f8fa;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #555;
  transition: background 0.15s;
}

.floating-btn:hover {
  background: #eceef2;
}

.sidebar-wrapper {
  width: 260px;
  flex-shrink: 0;
  transition: width 0.25s ease;
  overflow: hidden;
}

.sidebar-collapse {
  width: 0;
}

.msg-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 79vh;
  overflow: hidden;
  background-color: #ffffff;
  align-items: center;
  min-width: 0;
}

.main-area {
  flex: 1;
  display: flex;
  min-width: 0;
  height: 79vh;
}

.main-area.workspace-mode {
  align-items: stretch;
}

.col-chat {
  flex-shrink: 0;
  min-width: 280px;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: #fff;
  border-right: 1px solid #eceef2;
  box-sizing: border-box;
}

.msg-list.workspace-msg-list {
  flex: 1;
  width: 100%;
  max-width: none;
  padding: 16px 12px;
  overflow-y: auto;
  box-sizing: border-box;
}

.chat-input-wrap {
  flex-shrink: 0;
  padding: 0 12px 12px;
  width: 100%;
  box-sizing: border-box;
}

.workspace-msg-list :deep(.ai-msg),
.workspace-msg-list :deep(.workflow-msg),
.workspace-msg-list :deep(.code-block),
.workspace-msg-list :deep(.user-msg-wrap) {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

.workspace-msg-list :deep(.code-body-wrap) {
  overflow-x: auto;
}

.workspace-msg-list :deep(.code-body) {
  word-break: break-word;
  overflow-wrap: anywhere;
}

.main-area.with-preview .msg-wrap {
  width: 48%;
  align-items: stretch;
}

.main-area.with-preview .msg-list {
  width: 100%;
  max-width: none;
}

.msg-list {
  flex: 1;
  width: 72%;
  max-width: 900px;
  padding: 24px 16px;
  overflow-y: auto;
}

.loading-tip {
  text-align: center;
  color: #888;
  padding: 10px;
}

.home-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 79vh;
  gap: 32px;
  background: linear-gradient(135deg, #fcfdff 0%, #ffffff 100%);
}

.home-title {
  font-size: 42px;
  font-weight: 600;
  background: linear-gradient(90deg, #222222, #5248e5);
  -webkit-background-clip: text;
  color: transparent;
  margin: 0;
}
</style>

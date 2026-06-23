<template>
  <div class="chat-container">
    <!-- 边栏收起时显示顶部工具栏（展开 + 新建） -->
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

    <div class="msg-wrap" v-if="activeSession">
      <div class="msg-list" ref="msgListRef">
        <div v-if="loading" class="loading-tip">加载中...</div>
        <template v-for="item in msgList" :key="item.id">
          <ChatMessage
            v-if="item.messageType === 'user'"
            :msg="item"
            @resend="handleResend"
          />
          <AiMessage v-else :content="item.content" />
        </template>
        <WorkflowMessage v-if="workflowState" :state="workflowState" />
        <StreamingMessage v-if="streamingContent" :content="streamingContent" />
      </div>
      <ChatInput ref="chatInputRef" @send="handleSend" :is-home="false" />
    </div>

    <div class="home-wrap" v-else>
      <h2 class="home-title">AI对话助手</h2>
      <ChatInput ref="homeInputRef" @send="handleHomeSend" :is-home="true" />
    </div>
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
import ChatInput from '@/components/chat/ChatInput.vue'
import { getSessionList, getHistoryMsg, saveChatMessage, saveAiMessage, deleteSession, createSession } from '@/api/chat'
import { generateCodeStream, executeWorkflowStream } from '@/api/codegen'
import { readSseStream } from '@/utils/codegenStream'
import { buildWorkflowContent, buildCodeContent } from '@/utils/parseAiMessage'
import type { ChatSaveReq, ChatSession, ChatMessage as ChatMessageType } from '@/types/chat'
import type { WorkflowMessageState, WorkflowStepEvent } from '@/types/codegen'

const sessionList = ref<ChatSession[]>([])
const activeSession = ref<number | null>(null)
const msgList = ref<ChatMessageType[]>([])
const loading = ref(false)
const generating = ref(false)
const msgListRef = ref<HTMLDivElement | null>(null)
const chatInputRef = ref<InstanceType<typeof ChatInput> | null>(null)
const homeInputRef = ref<InstanceType<typeof ChatInput> | null>(null)
const CURRENT_USER_ID = 1
const APP_ID = 1

const showSidebar = ref(true)
const pendingNewSessionId = ref<number | null>(null)
const workflowState = ref<WorkflowMessageState | null>(null)
const streamingContent = ref('')

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
    activeStep: 'analyze',
    stepDescriptions: {},
    failed: false,
    files: [],
  }
}

function handleWorkflowEvent(state: WorkflowMessageState, event: WorkflowStepEvent) {
  if (event.step) state.activeStep = event.step
  if (event.message && event.step) state.stepDescriptions[event.step] = event.message
  if (event.type === 'done' && event.data) {
    state.running = false
    if (event.data.summary) state.summary = event.data.summary
    if (event.data.strategy) state.strategy = event.data.strategy
    if (event.data.validated != null) state.validated = event.data.validated
    if (event.data.codeFiles) state.files = event.data.codeFiles
    if (event.data.durationMs) state.durationMs = event.data.durationMs
    state.activeStep = 'done'
    state.stepDescriptions.done = '工作流执行完成'
    message.success('工作流执行完成')
  }
  if (event.type === 'error') {
    state.running = false
    state.failed = true
    state.error = event.message || '工作流执行失败'
    message.error(state.error)
  }
}

async function runWorkflow(sessionId: number, content: string) {
  workflowState.value = createWorkflowState()
  streamingContent.value = ''
  await scrollToBottom()

  try {
    const response = await executeWorkflowStream({ prompt: content, sessionId, appId: APP_ID })
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
    const payload = buildWorkflowContent({
      summary: state.summary,
      strategy: state.strategy,
      validated: state.validated,
      error: state.error,
      durationMs: state.durationMs,
      activeStep: state.activeStep,
      stepDescriptions: state.stepDescriptions,
      files: state.files,
    })
    await saveAiMessage(sessionId, APP_ID, payload)
    await loadMsg(sessionId)
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

async function runQuickGeneration(sessionId: number, content: string) {
  workflowState.value = null
  streamingContent.value = ''
  await scrollToBottom()

  try {
    const response = await generateCodeStream({ prompt: content, sessionId, appId: APP_ID })
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

async function processGeneration(sessionId: number, content: string, mode: 'fast' | 'deep') {
  generating.value = true
  try {
    if (mode === 'deep') {
      await runWorkflow(sessionId, content)
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
  } finally {
    loading.value = false
  }
}

async function handleSend({ content, mode }: { content: string; mode: 'fast' | 'deep' }) {
  if (!activeSession.value || loading.value || generating.value) return
  await sendUserMessage(activeSession.value, content)
  await processGeneration(activeSession.value, content, mode)
}

async function handleHomeSend({ content, mode }: { content: string; mode: 'fast' | 'deep' }) {
  if (loading.value || generating.value) return

  loading.value = true
  try {
    let newId: number
    if (pendingNewSessionId.value) {
      newId = pendingNewSessionId.value
      pendingNewSessionId.value = null
    } else {
      const sessRes = await createSession(CURRENT_USER_ID, APP_ID, '新对话')
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

  await processGeneration(activeSession.value!, content, mode)
}

async function handleResend(content: string) {
  if (!activeSession.value || loading.value || generating.value) return
  const mode = chatInputRef.value?.getMode?.() ?? 'deep'
  await sendUserMessage(activeSession.value, content)
  await processGeneration(activeSession.value, content, mode)
}

async function handleCreateSession() {
  if (pendingNewSessionId.value) {
    const pending = sessionList.value.find(s => s.id === pendingNewSessionId.value)
    if (pending && pending.messageCount === 0) {
      activeSession.value = null
      msgList.value = []
      workflowState.value = null
      streamingContent.value = ''
      return
    }
  }

  const emptySession = sessionList.value.find(
    s => s.sessionTitle === '新对话' && s.messageCount === 0,
  )
  if (emptySession) {
    pendingNewSessionId.value = emptySession.id
    activeSession.value = null
    msgList.value = []
    workflowState.value = null
    streamingContent.value = ''
    return
  }

  const res = await createSession(CURRENT_USER_ID, APP_ID, '新对话')
  if (res.code === 200) {
    pendingNewSessionId.value = res.data.id
    activeSession.value = null
    msgList.value = []
    workflowState.value = null
    streamingContent.value = ''
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

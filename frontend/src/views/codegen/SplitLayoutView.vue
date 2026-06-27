<template>
  <div class="chat-session">
    <!-- ====== 左侧边栏 ====== -->
    <div class="sidebar-wrapper" :class="{ 'sidebar-collapse': !showSidebar }">
      <SessionSidebar
          :session-list="sessionList"
          :current-session-id="activeSessionId"
          @change-session="switchSession"
          @create-session="handleCreateSession"
          @delete-session="handleDeleteSession"
          @toggle-sidebar="showSidebar = false"
      />
    </div>

    <!-- ====== 侧边栏折叠按钮 ====== -->
    <div v-if="!showSidebar" class="floating-toolbar">
      <a-tooltip title="展开边栏">
        <button class="floating-btn" @click="showSidebar = true">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="3" ry="3" /><line x1="9" y1="3" x2="9" y2="21" />
          </svg>
        </button>
      </a-tooltip>
      <a-tooltip title="新对话">
        <button class="floating-btn" @click="handleCreateSession">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="16" /><line x1="8" y1="12" x2="16" y2="12" />
          </svg>
        </button>
      </a-tooltip>
    </div>

    <!-- ====== 主聊天区域 ====== -->
    <div class="main-chat" :class="{ 'with-preview': previewVisible }">
      <!-- 消息列表 -->
      <div class="msg-list" ref="msgListRef">
        <div v-if="loadingHistory" class="loading-tip">加载中...</div>

        <template v-for="msg in msgList" :key="msg.id">
          <!-- 用户消息 -->
          <ChatMessage
              v-if="msg.messageType === 'user'"
              :msg="msg"
              @resend="handleResend"
          />

          <!-- AI 消息 —— 已完成，点击切换预览 -->
          <div
              v-else
              class="ai-msg-selectable"
              :class="{ 'preview-active': activePreviewMsgId === msg.id || (activePreviewMsgId == null && isLastAiMsg(msg.id)) }"
              @click="selectPreview(msg.id)"
              title="点击在右侧预览面板查看此代码"
          >
            <AiStreamMessage
                :content="msg.content"
                :is-streaming="false"
                @retry="handleRetryAi(msg)"
            />
          </div>
        </template>

        <!-- 流式生成中的 AI 消息 -->
        <AiStreamMessage
            v-if="streamingContent"
            :content="streamingContent"
            :is-streaming="true"
        />

        <!-- 思考中（还没收到文字） -->
        <AiStreamMessage
            v-if="generating && !streamingContent"
            content=""
            :thinking="true"
            :is-streaming="true"
        />

        <div v-if="generatingError" class="error-tip">
          {{ generatingError }}
          <a-button size="small" @click="retryLast()">重试</a-button>
        </div>
      </div>

      <!-- 停止生成按钮行 -->
      <div v-if="generating" class="action-row">
        <button class="stop-btn" @click="stopGeneration">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <rect x="4" y="4" width="16" height="16" rx="2" />
          </svg>
          <span>停止生成</span>
        </button>
      </div>

      <!-- 底部输入 -->
      <div class="input-wrap">
        <ChatInput ref="chatInputRef" compact @send="handleSend" :is-home="false" />
      </div>
    </div>

    <!-- ====== 右侧预览面板（内置拖拽把手 + 折叠按钮） ====== -->
    <ChatPreviewPanel
        :content="previewContent"
        :visible="previewVisible"
        :width="previewWidth"
        :collapsed="collapsedPreview"
        @resize-start="startResize"
        @toggle="togglePreview"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import SessionSidebar from '@/components/chat/SessionSidebar.vue'
import ChatMessage from '@/components/chat/ChatMessage.vue'
import AiStreamMessage from '@/components/chat/AiStreamMessage.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import ChatPreviewPanel from '@/components/chat/ChatPreviewPanel.vue'
import { getSessionList, getHistoryMsg, saveChatMessage, saveAiMessage, deleteSession, createSession } from '@/api/chat'
import { generateCodeStream, analyzeWorkflowStream, generateCode } from '@/api/codegen'
import { readSseStream } from '@/utils/codegenStream'
import type { ChatSaveReq, ChatSession, ChatMessage as ChatMessageType } from '@/types/chat'

const route = useRoute()
const router = useRouter()

// ========== 状态 ==========
const showSidebar = ref(true)
const sessionList = ref<ChatSession[]>([])
const activeSessionId = ref<number | null>(null)
const msgList = ref<ChatMessageType[]>([])
const loadingHistory = ref(false)
const generating = ref(false)
const streamingContent = ref('')
const generatingError = ref('')
const lastPrompt = ref('')
const lastMode = ref<'fast' | 'deep'>('fast')
const lastFormat = ref('HTML')
const abortController = ref<AbortController | null>(null)
const previewVisible = ref(false)
const collapsedPreview = ref(false)
const getDefaultPreviewWidth = () => Math.max(360, Math.round(window.innerWidth * 0.50))
const getMaxPreviewWidth = () => Math.max(400, Math.round(window.innerWidth * 0.50))
const previewWidth = ref(getDefaultPreviewWidth())
const isDragging = ref(false)
const msgListRef = ref<HTMLDivElement | null>(null)
const chatInputRef = ref<InstanceType<typeof ChatInput> | null>(null)

/** 当前预览面板对应的消息 ID（null = 自动跟随最新） */
const activePreviewMsgId = ref<number | null>(null)

/** 预览内容：优先取流式内容，其次取选中/最新的 AI 消息 */
const previewContent = computed(() => {
  if (streamingContent.value) return streamingContent.value
  // 用户手动选中了某条消息
  if (activePreviewMsgId.value != null) {
    const selected = msgList.value.find(m => m.id === activePreviewMsgId.value)
    if (selected && selected.messageType === 'ai') return selected.content
  }
  // 默认：最后一条 AI 消息
  for (let i = msgList.value.length - 1; i >= 0; i--) {
    if (msgList.value[i].messageType === 'ai') {
      return msgList.value[i].content
    }
  }
  return ''
})

/** 当前会话已绑定的应用 ID */
function resolveCodegenAppId(): number | undefined {
  if (!activeSessionId.value) return undefined
  const session = sessionList.value.find(s => s.id === activeSessionId.value)
  const appId = session?.appId
  return appId != null && appId > 0 ? appId : undefined
}

/** 是否有可预览的内容（含 HTML 代码块） */
const hasPreviewContent = computed(() => {
  const c = previewContent.value
  if (!c) return false
  return /```(html|vue)```|```\s*\n[^`]*<|<!DOCTYPE|<html|<template/i.test(c)
})

// ========== 初始化 ==========
onMounted(async () => {
  // 加载会话列表
  const sessRes = await getSessionList()
  if (sessRes.code === 200 && sessRes.data) {
    sessionList.value = sessRes.data
  }

  // 从 URL 参数获取会话 ID
  const idParam = Number(route.params.sessionId)
  if (idParam && !isNaN(idParam)) {
    activeSessionId.value = idParam
    await loadMessages(idParam)

    // 如果有 prompt 参数（从首页跳转过来），触发 AI 生成
    const prompt = route.query.prompt as string
    const mode = (route.query.mode as string) || 'fast'
    const output = (route.query.output as string) || 'stream'
    const format = (route.query.format as string) || 'HTML'
    if (prompt && msgList.value.length <= 1) {
      lastPrompt.value = prompt
      lastMode.value = mode as 'fast' | 'deep'
      lastFormat.value = format
      await triggerAiGenerate(prompt, mode as 'fast' | 'deep', output, format)
    }

    // 清除 URL 中的 query 参数（保留干净路径）
    router.replace({ path: `/chat/session/${idParam}` })

    // 检查历史消息中是否有 HTML 可预览
    if (hasPreviewContent.value) {
      previewVisible.value = true
    }
  }
})

// 监听路由变化（切换会话）
watch(() => route.params.sessionId, async (newId) => {
  const id = Number(newId)
  if (id && !isNaN(id) && id !== activeSessionId.value) {
    // 中断当前正在进行的生成
    abortController.value?.abort()
    abortController.value = null

    activeSessionId.value = id
    msgList.value = []
    streamingContent.value = ''
    generating.value = false
    generatingError.value = ''
    previewVisible.value = false
    collapsedPreview.value = false
    activePreviewMsgId.value = null
    await loadMessages(id)
  }
})

// ========== 会话操作 ==========
async function loadMessages(sessionId: number) {
  loadingHistory.value = true
  try {
    const res = await getHistoryMsg(sessionId)
    if (res.code === 200 && res.data) {
      msgList.value = res.data.list ?? []
      await scrollToBottom()
    }
  } catch {
    msgList.value = []
  } finally {
    loadingHistory.value = false
  }
}

async function switchSession(id: number) {
  router.push(`/chat/session/${id}`)
}

async function handleCreateSession() {
  const res = await createSession()
  if (res.code === 200 && res.data) {
    sessionList.value = [res.data, ...sessionList.value]
    router.push(`/chat/session/${res.data.id}`)
  }
}

async function handleDeleteSession(delId: number) {
  if (sessionList.value.length <= 1) {
    message.warning('至少保留一个会话')
    return
  }
  await deleteSession(delId)
  sessionList.value = sessionList.value.filter(s => s.id !== delId)

  if (activeSessionId.value === delId) {
    const first = sessionList.value[0]
    if (first) router.push(`/chat/session/${first.id}`)
  }
}

// ========== 发送消息 ==========
async function handleSend({ content, mode, output, format }: { content: string; mode: 'fast' | 'deep'; output: string; format?: string }) {
  if (!activeSessionId.value || generating.value) return

  // 1. 保存用户消息
  generatingError.value = ''
  const params: ChatSaveReq = {
    sessionId: activeSessionId.value,
    appId: null,
    messageType: 'user',
    content,
  }
  try {
    await saveChatMessage(params)
    await loadMessages(activeSessionId.value)
    // 刷新侧边栏
    const sessRes = await getSessionList()
    if (sessRes.code === 200 && sessRes.data) sessionList.value = sessRes.data
  } catch (e: any) {
    message.error('消息保存失败')
    return
  }

  // 2. 触发 AI 生成
  lastPrompt.value = content
  lastMode.value = mode
  lastFormat.value = format || 'HTML'
  await triggerAiGenerate(content, mode, output, format || 'HTML')
}

async function handleResend(content: string) {
  if (!activeSessionId.value || generating.value) return
  generatingError.value = ''
  lastPrompt.value = content
  await triggerAiGenerate(content, lastMode.value, 'stream', lastFormat.value)
}

function handleRetryAi(_msg: ChatMessageType) {
  if (!lastPrompt.value) return
  handleResend(lastPrompt.value)
}

// ========== AI 生成核心 ==========
/** 判断是否为最后一条 AI 消息（默认预览目标） */
function isLastAiMsg(msgId: number): boolean {
  for (let i = msgList.value.length - 1; i >= 0; i--) {
    if (msgList.value[i].messageType === 'ai') {
      return msgList.value[i].id === msgId
    }
  }
  return false
}

/** 手动选择某条 AI 消息显示在预览面板 */
function selectPreview(msgId: number) {
  activePreviewMsgId.value = msgId
}

async function triggerAiGenerate(prompt: string, mode: 'fast' | 'deep', output = 'stream', format = 'HTML') {
  if (!activeSessionId.value) return
  generating.value = true
  streamingContent.value = ''
  generatingError.value = ''
  activePreviewMsgId.value = null  // 新生成时自动跟随最新

  // 创建新的 AbortController（仅流式需要）
  const controller = new AbortController()
  abortController.value = controller

  await scrollToBottom()

  try {
    if (mode === 'deep') {
      await runDeepAnalyze(activeSessionId.value, prompt, controller.signal)
    } else if (output === 'sync') {
      await runFastGenerateSync(activeSessionId.value, prompt, format)
    } else {
      await runFastGenerate(activeSessionId.value, prompt, format, controller.signal)
    }
    // 生成完成后自动弹出预览（如果有 HTML 内容）
    if (hasPreviewContent.value) {
      previewVisible.value = true
    }
  } catch (e: any) {
    // 用户主动停止 → 不是错误，静默处理
    if (e instanceof DOMException && e.name === 'AbortError') {
      // 正常停止，不显示错误
    } else {
      const msg = e?.message || '生成失败'
      generatingError.value = msg.includes('Insufficient Balance')
          ? 'DeepSeek 账户余额不足，请充值后重试'
          : msg
    }
  } finally {
    generating.value = false
    abortController.value = null
    streamingContent.value = ''
  }

  // 生成完成后刷新会话列表
  try {
    const sessRes = await getSessionList()
    if (sessRes.code === 200 && sessRes.data) sessionList.value = sessRes.data
  } catch { /* ignore */ }
}

// ========== 预览面板：拖拽 + 折叠 ==========
function togglePreview() {
  if (previewVisible.value) {
    previewVisible.value = false
    collapsedPreview.value = true
  } else {
    previewVisible.value = true
    collapsedPreview.value = false
  }
}

function startResize(e: MouseEvent) {
  e.preventDefault()
  e.stopPropagation()
  isDragging.value = true
  document.body.classList.add('resizing-preview')
  document.body.style.userSelect = 'none'

  const startX = e.clientX
  const startWidth = previewWidth.value
  const maxWidth = getMaxPreviewWidth()
  const minWidth = 200

  const onMove = (ev: MouseEvent) => {
    const delta = startX - ev.clientX
    const newWidth = Math.min(maxWidth, Math.max(minWidth, startWidth + delta))
    previewWidth.value = newWidth
  }

  const onUp = () => cleanup()

  const cleanup = () => {
    isDragging.value = false
    document.body.classList.remove('resizing-preview')
    document.body.style.userSelect = ''
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

// 自动打开预览（检测到 HTML 内容时）
watch(hasPreviewContent, (val) => {
  if (val && !previewVisible.value && !collapsedPreview.value) {
    previewVisible.value = true
  }
})

/** 用户点击停止按钮 */
async function stopGeneration() {
  const controller = abortController.value
  if (!controller) return

  // 先保存已接收的部分内容
  const partialContent = streamingContent.value
  const sessionId = activeSessionId.value

  // 触发 abort
  controller.abort()

  // 保存部分内容
  if (partialContent && sessionId) {
    try {
      await saveAiMessage(sessionId, null, partialContent)
      await loadMessages(sessionId)
      // 停止后也检查是否有可预览内容
      if (hasPreviewContent.value) {
        previewVisible.value = true
      }
    } catch { /* ignore */ }
  }
}

/**
 * 快速生成模式：流式调用 /api/codegen/stream
 */
async function runFastGenerate(sessionId: number, prompt: string, format = 'HTML', signal?: AbortSignal) {
  const response = await generateCodeStream({
    prompt,
    sessionId,
    appId: resolveCodegenAppId(),
    generateType: format,
  }, signal)

  let receivedContent = ''

  await readSseStream(response, (eventName, data) => {
    if (eventName === 'code_chunk') {
      receivedContent += data
      streamingContent.value = receivedContent
      scrollToBottom()
    } else if (eventName === 'error') {
      throw new Error(data)
    }
  })

  // 保存 AI 回复
  if (receivedContent) {
    await saveAiMessage(sessionId, null, receivedContent)
  }
  // 重新加载消息列表显示完整记录
  await loadMessages(sessionId)
  streamingContent.value = ''
}

/**
 * 快速生成 - 同步模式：一次性返回完整结果
 */
async function runFastGenerateSync(sessionId: number, prompt: string, format = 'HTML') {
  streamingContent.value = '正在生成...'

  const res = await generateCode({
    prompt,
    sessionId,
    appId: resolveCodegenAppId(),
    generateType: format,
  })

  const code = res.data?.codeContent
  if (code) {
    streamingContent.value = code
    await saveAiMessage(sessionId, null, code)
  } else if (res.data?.errorMsg) {
    // 后端返回了错误信息
    generatingError.value = res.data.errorMsg
    return
  } else {
    // 空响应
    generatingError.value = '模型未返回有效内容，请重试'
    return
  }
  await loadMessages(sessionId)
  streamingContent.value = ''
}

/**
 * 深度分析模式：流式调用 /api/codegen/workflow/analyze/stream
 */
async function runDeepAnalyze(sessionId: number, prompt: string, signal?: AbortSignal) {
  const response = await analyzeWorkflowStream({
    prompt,
    sessionId,
    appId: resolveCodegenAppId(),
  }, signal)

  let receivedContent = ''
  let phaseText = ''

  await readSseStream(response, (eventName, data) => {
    try {
      const event = JSON.parse(data)
      if (event.type === 'step' && event.message) {
        phaseText += `**${event.message}**\n\n`
        streamingContent.value = phaseText + receivedContent
        scrollToBottom()
      } else if (event.type === 'prd_ready' && event.data) {
        const result = event.data
        receivedContent = `## 📋 需求分析结果\n\n`
        if (result.summary) receivedContent += `### 需求摘要\n${result.summary}\n\n`
        if (result.prdContent) receivedContent += `### 产品文档\n${result.prdContent}\n\n`
        if (result.strategy) receivedContent += `### 推荐策略\n${result.strategy}\n\n`
        if (result.durationMs) receivedContent += `---\n⏱ 耗时：${(result.durationMs / 1000).toFixed(1)}s\n`
        streamingContent.value = receivedContent
        scrollToBottom()
      } else if (event.type === 'done' && event.data) {
        const result = event.data
        let content = '## ✅ 深度分析完成\n\n'
        if (result.summary) content += `### 分析摘要\n${result.summary}\n\n`
        if (result.strategy) content += `### 策略\n${result.strategy}\n\n`
        if (result.validated != null) content += `### 校验结果\n${result.validated ? '✅ 通过' : '❌ 未通过'}\n\n`
        if (result.durationMs) content += `---\n⏱ 耗时：${(result.durationMs / 1000).toFixed(1)}s\n`
        streamingContent.value = content
        receivedContent = content
        scrollToBottom()
      } else if (event.type === 'task') {
        const task = event.data
        if (task) {
          phaseText += `- ${task.label ?? '执行任务中...'}\n`
          streamingContent.value = phaseText
          scrollToBottom()
        }
      }
    } catch {
      receivedContent += data
      streamingContent.value = receivedContent
      scrollToBottom()
    }
  })

  const final = streamingContent.value || receivedContent || phaseText
  if (final) {
    await saveAiMessage(sessionId, null, final)
  }
  await loadMessages(sessionId)
  streamingContent.value = ''
}

async function retryLast() {
  if (!lastPrompt.value) return
  generatingError.value = ''
  if (!activeSessionId.value) return
  await triggerAiGenerate(lastPrompt.value, lastMode.value, 'stream', lastFormat.value)
}

// ========== 工具函数 ==========
async function scrollToBottom() {
  await nextTick()
  if (msgListRef.value) {
    msgListRef.value.scrollTop = msgListRef.value.scrollHeight
  }
}
</script>

<style scoped>
.chat-session {
  display: flex;
  height: calc(100vh - 64px - 48px);
  overflow: hidden;
  background: #fff;
  position: relative;
}

/* 侧边栏 */
.sidebar-wrapper {
  width: 260px;
  flex-shrink: 0;
  transition: width 0.25s ease;
  overflow: hidden;
}

.sidebar-collapse {
  width: 0;
}

/* 侧边栏折叠后的浮动按钮 */
.floating-toolbar {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  z-index: 30;
  background: #fff;
  padding: 6px;
  border-radius: 12px;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.08);
  border: 1px solid #eef0f2;
}

.floating-btn {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
  transition: all 0.15s;
}

.floating-btn:hover {
  background: #f0f5ff;
  color: #1677ff;
}

/* 主聊天区 */
.main-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  background: #fff;
}

/* 开启预览时，聊天区不设固定最大宽度，由 flex 自然分配 */
.main-chat.with-preview .msg-list {
  max-width: none;
}

.main-chat.with-preview .input-wrap,
.main-chat.with-preview .action-row {
  max-width: none;
}

/* 消息列表 */
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}

.msg-list::-webkit-scrollbar {
  width: 4px;
}

.msg-list::-webkit-scrollbar-thumb {
  background: #ddd;
  border-radius: 2px;
}

/* ====== AI 消息可点击切换预览 ====== */
.ai-msg-selectable {
  cursor: pointer;
  border-radius: 10px;
  padding: 2px 6px;
  margin: 0 -6px;
  transition: background 0.15s;
  position: relative;
}

.ai-msg-selectable:hover {
  background: #f5f7fb;
}

.ai-msg-selectable.preview-active {
  background: #eef3ff;
  box-shadow: inset 3px 0 0 #1677ff;
}

/* 选中指示小圆点 */
.ai-msg-selectable.preview-active::before {
  content: '';
  position: absolute;
  left: -2px;
  top: 50%;
  transform: translateY(-50%);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #1677ff;
  opacity: 0;
}

.loading-tip {
  text-align: center;
  color: #aaa;
  padding: 40px 0;
}

.error-tip {
  text-align: center;
  color: #f5222d;
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

/* 操作按钮行（停止 + 打开预览） */
.action-row {
  display: flex;
  justify-content: center;
  gap: 10px;
  padding: 0 24px 8px;
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}

.stop-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  border-radius: 20px;
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #555;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.stop-btn:hover {
  background: #fef2f2;
  border-color: #fca5a5;
  color: #ef4444;
}

.stop-btn svg {
  color: #ef4444;
}



/* 底部输入 */
.input-wrap {
  flex-shrink: 0;
  padding: 0 24px 16px;
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}
</style>
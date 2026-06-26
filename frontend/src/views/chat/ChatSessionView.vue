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
          @rename-session="handleRenameSession"
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

    <!-- ====== 主聊天区域（深度分析三栏 / 普通双栏） ====== -->
    <div
      class="main-chat"
      :class="{ 'with-preview': previewVisible && !workspaceMode, 'workspace-mode': workspaceMode }"
      ref="workspaceRef"
    >
      <div
        class="col-chat"
        :class="{ 'col-chat-fixed': workspaceMode }"
        :style="workspaceMode ? { width: `${columnWidths.chat}px` } : undefined"
      >
      <!-- 消息列表 -->
      <div class="msg-list" ref="msgListRef">
        <div v-if="loadingHistory" class="loading-tip">加载中...</div>

        <template v-for="msg in msgList" :key="msg.id">
          <div
              class="msg-row"
              :class="{ 'msg-selected': selectedMsgIds.has(msg.id) }"
          >
            <!-- 多选复选框 -->
            <div
                v-if="selectMode"
                class="msg-checkbox"
                @click.stop="toggleMessageSelect(msg.id)"
            >
              <div class="checkbox-circle" :class="{ checked: selectedMsgIds.has(msg.id) }">
                <svg v-if="selectedMsgIds.has(msg.id)" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
              </div>
            </div>

            <!-- 用户消息 -->
            <ChatMessage
                v-if="msg.messageType === 'user'"
                class="msg-body"
                :msg="msg"
                @resend="handleResend"
                @delete="handleDeleteMessage(msg.id)"
            />

            <!-- AI 消息 -->
            <div
                v-else
                class="ai-msg-selectable msg-body"
                :class="{ 'preview-active': activePreviewMsgId === msg.id || (activePreviewMsgId == null && isLastAiMsg(msg.id)) }"
                @click="!selectMode && selectPreview(msg.id)"
                title="点击在右侧预览面板查看此代码"
            >
              <AiStreamMessage
                  :content="msg.content"
                  :is-streaming="false"
                  @retry="handleRetryAi(msg)"
                  @delete="handleDeleteMessage(msg.id)"
              />
            </div>
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

        <!-- 回到底部按钮 — sticky 定位，始终在消息列表可视区底部中央 -->
        <transition name="scroll-btn-fade">
          <button
              v-show="showScrollToBottom"
              class="scroll-to-bottom-btn"
              @click="scrollToBottomSmooth"
              title="回到底部"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </button>
        </transition>
      </div>

      <!-- 多选删除底部操作栏 -->
      <transition name="batch-bar-fade">
        <div v-if="selectMode" class="batch-action-bar">
          <span class="batch-count">已选 {{ selectedMsgIds.size }} 条</span>
          <div class="batch-right">
            <button
                class="batch-del-btn"
                :disabled="selectedMsgIds.size === 0"
                @click="batchDeleteMessages"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
              </svg>
              <span>删除</span>
            </button>
            <button class="batch-cancel-btn" @click="cancelSelectMode">取消</button>
          </div>
        </div>
      </transition>

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

      <template v-if="workspaceMode">
        <ColumnResizer @start="beginResize(0, $event)" />
        <DeepWorkspace
          :files="workspaceFiles"
          :generating="generating"
          :tree-width="columnWidths.tree"
          @resize-tree-start="beginResize(1, $event)"
        />
      </template>
    </div>

    <!-- ====== 右侧预览面板（快速生成模式） ====== -->
    <ChatPreviewPanel
        v-if="!workspaceMode"
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
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import SessionSidebar from '@/components/chat/SessionSidebar.vue'
import ChatMessage from '@/components/chat/ChatMessage.vue'
import AiStreamMessage from '@/components/chat/AiStreamMessage.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import ChatPreviewPanel from '@/components/chat/ChatPreviewPanel.vue'
import DeepWorkspace from '@/components/chat/DeepWorkspace.vue'
import ColumnResizer from '@/components/chat/ColumnResizer.vue'
import { getSessionList, getHistoryMsg, saveChatMessage, saveAiMessage, deleteSession, createSession, deleteMessage, renameSession } from '@/api/chat'
import { generateCodeStream, analyzeWorkflowStream, continueWorkflowStream, generateCode } from '@/api/codegen'
import { readSseStream } from '@/utils/codegenStream'
import { splitAiContent } from '@/utils/splitMultiFile'
import { formatCodeFilesToContent, formatPrdSummary } from '@/utils/formatCodeFiles'
import { useColumnResize } from '@/composables/useColumnResize'
import type { ChatSaveReq, ChatSession, ChatMessage as ChatMessageType } from '@/types/chat'
import type { CodeFile, WorkflowResult, WorkflowStepEvent, WorkflowTask } from '@/types/codegen'

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
const workspaceMode = ref(false)
const workspaceFiles = ref<CodeFile[]>([])
const workspaceRef = ref<HTMLElement | null>(null)
const { widths: columnWidths, beginResize } = useColumnResize(
  () => workspaceRef.value,
  { chat: 360, tree: 240 },
  { chat: 280, tree: 180 },
  320,
)
const getDefaultPreviewWidth = () => Math.max(360, Math.round(window.innerWidth * 0.50))
const getMaxPreviewWidth = () => Math.max(400, Math.round(window.innerWidth * 0.50))
const previewWidth = ref(getDefaultPreviewWidth())
const isDragging = ref(false)
const msgListRef = ref<HTMLDivElement | null>(null)
const chatInputRef = ref<InstanceType<typeof ChatInput> | null>(null)
const showScrollToBottom = ref(false)

// ========== 多选删除 ==========
const selectMode = ref(false)
const selectedMsgIds = ref(new Set<number>())

/** 当前预览面板对应的消息 ID（null = 自动跟随最新） */
const activePreviewMsgId = ref<number | null>(null)

/** 预览内容：优先取流式内容，其次取选中/最新 AI 消息所在的多文件批次 */
const previewContent = computed(() => {
  // 流式生成中：累积的完整内容包含所有文件
  if (streamingContent.value) return streamingContent.value

  // 用户手动选中了某条消息 → 收集它所在批次的连续 AI 消息
  if (activePreviewMsgId.value != null) {
    const batch = collectAiBatch(activePreviewMsgId.value)
    if (batch) return batch
  }

  // 默认：收集最新一批连续 AI 消息（一次生成的所有文件）
  const latestBatch = collectLatestAiBatch()
  if (latestBatch) return latestBatch

  return ''
})

/**
 * 收集包含指定消息的连续 AI 消息批次
 * 向上找到第一个 user 消息作为批次起点，向下收集所有连续 AI 消息
 */
function collectAiBatch(msgId: number): string | null {
  const idx = msgList.value.findIndex(m => m.id === msgId)
  if (idx < 0 || msgList.value[idx].messageType !== 'ai') return null

  let start = idx
  while (start > 0 && msgList.value[start - 1].messageType === 'ai') start--

  const parts: string[] = []
  for (let i = start; i < msgList.value.length && msgList.value[i].messageType === 'ai'; i++) {
    parts.push(msgList.value[i].content)
  }
  return parts.length > 0 ? parts.join('\n\n') : null
}

/** 收集消息列表末尾的连续 AI 消息批次 */
function collectLatestAiBatch(): string | null {
  const parts: string[] = []
  for (let i = msgList.value.length - 1; i >= 0; i--) {
    if (msgList.value[i].messageType === 'ai') {
      parts.unshift(msgList.value[i].content)
    } else {
      break
    }
  }
  return parts.length > 0 ? parts.join('\n\n') : null
}

/** 是否有可预览的内容（含 HTML/Vue/多文件代码块） */
const hasPreviewContent = computed(() => {
  const c = previewContent.value
  if (!c) return false
  // HTML/Vue 代码块
  if (/```(html|vue)```|```\s*\n[^`]*<|<!DOCTYPE|<html|<template/i.test(c)) return true
  // 多文件 ## 📁 格式（拆分后的消息批次）
  if (/##\s+📁/.test(c)) return true
  // 多文件 JSON 数组
  if (c.trim().startsWith('[') && c.includes('"path"') && c.includes('"content"')) return true
  return false
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
    workspaceMode.value = false
    workspaceFiles.value = []
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

async function handleRenameSession(sessionId: number, title: string) {
  try {
    await renameSession(sessionId, title)
    const sessRes = await getSessionList()
    if (sessRes.code === 200 && sessRes.data) {
      sessionList.value = sessRes.data
    }
    message.success('已重命名')
  } catch {
    // 错误已由请求拦截器统一提示
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
      workspaceMode.value = true
      workspaceFiles.value = []
      showSidebar.value = false
      await runDeepAnalyze(activeSessionId.value, prompt, controller.signal)
    } else {
      workspaceMode.value = false
      workspaceFiles.value = []
      if (output === 'sync') {
        await runFastGenerateSync(activeSessionId.value, prompt, format)
      } else {
        await runFastGenerate(activeSessionId.value, prompt, format, controller.signal)
      }
      // 生成完成后自动弹出预览（如果有 HTML 内容）
      if (hasPreviewContent.value) {
        previewVisible.value = true
      }
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

  // 保存 AI 回复 — 多文件拆分为多条消息，每条一个气泡
  if (receivedContent) {
    const parts = splitAiContent(receivedContent)
    for (const part of parts) {
      await saveAiMessage(sessionId, null, part)
    }
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
    generateType: format,
  })

  const code = res.data?.codeContent
  if (code) {
    streamingContent.value = code
    // 多文件拆分为多条消息，每条一个气泡
    const parts = splitAiContent(code)
    for (const part of parts) {
      await saveAiMessage(sessionId, null, part)
    }
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

function appendWorkflowProgress(phaseText: string, event: WorkflowStepEvent): string {
  if (event.type === 'task' && event.data) {
    const task = event.data as WorkflowTask
    return `${phaseText}- ${task.label ?? '执行任务中...'}\n`
  }
  if (event.type === 'step' && event.message) {
    return `${phaseText}**${event.message}**\n\n`
  }
  return phaseText
}

async function consumeWorkflowStream(
  response: Response,
  onEvent: (event: WorkflowStepEvent) => void,
) {
  await readSseStream(response, (eventName, data) => {
    if (eventName !== 'workflow') return
    try {
      onEvent(JSON.parse(data) as WorkflowStepEvent)
    } catch {
      // ignore malformed chunk
    }
  })
}

/**
 * 深度分析：阶段一分析 PRD → 阶段二自动继续生成代码 → 三栏展示
 */
async function runDeepAnalyze(sessionId: number, prompt: string, signal?: AbortSignal) {
  let phaseText = ''
  let generateId: number | undefined
  let prdSummary = ''
  let cachedPrdContent = ''
  let cachedSummary = ''

  const analyzeResponse = await analyzeWorkflowStream({ prompt, sessionId }, signal)
  await consumeWorkflowStream(analyzeResponse, (event) => {
    if (event.type === 'error') {
      throw new Error(event.message || '深度分析失败')
    }
    phaseText = appendWorkflowProgress(phaseText, event)
    streamingContent.value = phaseText
    scrollToBottom()

    if (event.type === 'prd_ready' && event.data) {
      const result = event.data as WorkflowResult
      generateId = result.generateId
      cachedPrdContent = result.prdContent ?? ''
      cachedSummary = result.summary ?? ''
      prdSummary = formatPrdSummary(result)
      streamingContent.value = `${phaseText}\n\n${prdSummary}`
      scrollToBottom()
    }
  })

  if (!generateId) {
    throw new Error('需求分析未完成，未获取到生成记录')
  }
  if (!cachedPrdContent.trim()) {
    throw new Error('需求文档为空，请重试深度分析')
  }

  message.info('需求文档已生成，正在自动创作应用...')
  phaseText += `\n**正在根据需求文档生成应用...**\n\n`
  streamingContent.value = phaseText + prdSummary
  scrollToBottom()

  let codeFiles: CodeFile[] = []
  const continueResponse = await continueWorkflowStream(generateId, {
    prdContent: cachedPrdContent,
    summary: cachedSummary,
  }, signal)
  await consumeWorkflowStream(continueResponse, (event) => {
    if (event.type === 'error') {
      throw new Error(event.message || '代码生成失败')
    }
    phaseText = appendWorkflowProgress(phaseText, event)
    streamingContent.value = phaseText + (prdSummary ? `\n\n${prdSummary}` : '')
    scrollToBottom()

    if (event.type === 'done' && event.data) {
      const result = event.data as WorkflowResult
      codeFiles = result.codeFiles ?? []
      workspaceFiles.value = codeFiles
      if (result.strategy) {
        phaseText += `\n**策略：** ${result.strategy}\n`
      }
      if (result.validated != null) {
        phaseText += `**校验：** ${result.validated ? '通过' : '未通过'}\n`
      }
      streamingContent.value = phaseText
    }
  })

  if (prdSummary) {
    await saveAiMessage(sessionId, null, prdSummary)
  }

  if (codeFiles.length) {
    const codeContent = formatCodeFilesToContent(codeFiles)
    const parts = splitAiContent(codeContent)
    for (const part of parts) {
      await saveAiMessage(sessionId, null, part)
    }
    message.success(`应用生成完成，共 ${codeFiles.length} 个文件`)
  } else {
    throw new Error('代码生成完成但未返回文件，请重试')
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

/** 滚动消息列表到底部 */
async function scrollToBottom() {
  await nextTick()
  if (msgListRef.value) {
    msgListRef.value.scrollTop = msgListRef.value.scrollHeight
  }
  showScrollToBottom.value = false
}

/** 平滑滚动到底部（用户点击按钮时使用） */
function scrollToBottomSmooth() {
  if (msgListRef.value) {
    msgListRef.value.scrollTo({ top: msgListRef.value.scrollHeight, behavior: 'smooth' })
  }
  showScrollToBottom.value = false
}

/** 监听消息列表滚动，控制回到底部按钮的显隐 */
function onMsgListScroll() {
  const el = msgListRef.value
  if (!el) return
  // 距离底部超过 120px 时显示按钮
  const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  showScrollToBottom.value = distanceFromBottom > 120
}

// 挂载 / 卸载滚动监听
onMounted(() => {
  msgListRef.value?.addEventListener('scroll', onMsgListScroll, { passive: true })
})

onUnmounted(() => {
  msgListRef.value?.removeEventListener('scroll', onMsgListScroll)
})

// ========== 多选删除 ==========

/** 点击删除按钮 → 进入多选模式，预选该消息 */
function handleDeleteMessage(msgId: number) {
  selectMode.value = true
  selectedMsgIds.value = new Set([msgId])
}

/** 切换单条消息选中 */
function toggleMessageSelect(msgId: number) {
  const next = new Set(selectedMsgIds.value)
  if (next.has(msgId)) {
    next.delete(msgId)
    if (next.size === 0) {
      selectMode.value = false
      return
    }
  } else {
    next.add(msgId)
  }
  selectedMsgIds.value = next
}

/** 批量删除已选消息 */
async function batchDeleteMessages() {
  if (selectedMsgIds.value.size === 0) return

  const ids = Array.from(selectedMsgIds.value)

  try {
    const results = await Promise.allSettled(ids.map(id => deleteMessage(id)))
    const failed = results.filter(r => r.status === 'rejected').length
    if (failed > 0) {
      message.warning(`已删除 ${ids.length - failed} 条，${failed} 条失败`)
    } else {
      message.success(`已删除 ${ids.length} 条消息`)
    }
    if (activeSessionId.value) {
      await loadMessages(activeSessionId.value)
    }
  } catch {
    message.error('删除失败')
  }
  cancelSelectMode()
}

function cancelSelectMode() {
  selectMode.value = false
  selectedMsgIds.value = new Set()
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

.main-chat.workspace-mode {
  flex-direction: row;
  align-items: stretch;
}

.col-chat {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
}

.col-chat.col-chat-fixed {
  flex: none;
  border-right: 1px solid #eceef2;
}

.col-chat-fixed .msg-list {
  max-width: none;
  width: 100%;
  margin: 0;
}

.col-chat-fixed .input-wrap,
.col-chat-fixed .action-row {
  max-width: none;
  width: 100%;
  margin: 0;
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

/* ====== 回到底部按钮（豆包风格，sticky 跟随滚动） ====== */
.scroll-to-bottom-btn {
  position: sticky;
  bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin: 0 auto;
  border-radius: 50%;
  border: 1px solid #e5e7eb;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  color: #666;
  z-index: 20;
  flex-shrink: 0;
  transition: box-shadow 0.2s, border-color 0.2s, color 0.2s, background 0.2s;
}

.scroll-to-bottom-btn:hover {
  background: #f0f5ff;
  border-color: #91caff;
  color: #1677ff;
  box-shadow: 0 4px 16px rgba(22, 119, 255, 0.15);
}

.scroll-to-bottom-btn:active {
  transform: scale(0.94);
}

/* 渐隐渐显动画 */
.scroll-btn-fade-enter-active,
.scroll-btn-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.scroll-btn-fade-enter-from,
.scroll-btn-fade-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

/* ====== 多选删除 ====== */
.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  border-radius: 10px;
  padding: 2px 4px;
  margin: 0 -4px;
  transition: background 0.15s;
}

.msg-row.msg-selected {
  background: #f0f5ff;
}

.msg-body {
  flex: 1;
  min-width: 0;
}

.msg-checkbox {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  margin-top: 18px;
}

.checkbox-circle {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid #d0d3d7;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.checkbox-circle.checked {
  background: #1677ff;
  border-color: #1677ff;
}

.batch-action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: #fff;
  border-top: 1px solid #eef0f2;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.04);
  z-index: 25;
}

.batch-count {
  font-size: 14px;
  font-weight: 500;
  color: #666;
}

.batch-right {
  display: flex;
  gap: 10px;
}

.batch-del-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 7px 18px;
  border-radius: 8px;
  border: none;
  background: #f5222d;
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.batch-del-btn:hover:not(:disabled) {
  background: #cf1322;
}

.batch-del-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.batch-cancel-btn {
  padding: 7px 18px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #666;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.batch-cancel-btn:hover {
  background: #f5f7fa;
  border-color: #d0d3d7;
}

.batch-bar-fade-enter-active,
.batch-bar-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.batch-bar-fade-enter-from,
.batch-bar-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

</style>
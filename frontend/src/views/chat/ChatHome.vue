<template>
  <div class="chat-home">
    <!-- ====== 左侧边栏 ====== -->
    <div class="sidebar-wrapper" :class="{ 'sidebar-collapse': !showSidebar }">
      <SessionSidebar
          :session-list="sessionList"
          :current-session-id="null"
          @change-session="switchSession"
          @create-session="handleCreateSession"
          @delete-session="handleDeleteSession"
          @toggle-sidebar="showSidebar = false"
      />
    </div>

    <!-- 侧边栏折叠后的浮动按钮 -->
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

    <!-- ====== 中间主区域 ====== -->
    <div class="home-center">
      <!-- Logo / 标题 -->
      <div class="brand">
        <svg class="brand-icon" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#1677ff" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <polygon points="12 2 15 8 22 9 17 14 18 21 12 17.5 6 21 7 14 2 9 9 8 12 2" />
        </svg>
        <h1 class="brand-title">AI 对话助手</h1>
      </div>

      <p class="subtitle">可以问我任何问题，也可以帮你写代码</p>

      <!-- 代码生成预设卡片 -->
      <div class="feature-cards">
        <div class="feature-card" @click="setQuickPrompt('帮我写一个带登录和注册功能的响应式HTML页面')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#1677ff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="16 18 22 12 16 6" /><polyline points="8 6 2 12 8 18" />
          </svg>
          <span>登录页面</span>
        </div>
        <div class="feature-card" @click="setQuickPrompt('帮我写一个带增删改查功能的数据管理后台Vue工程')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#52c41a" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="12 2 15 8 22 9 17 14 18 21 12 17.5 6 21 7 14 2 9 9 8" />
          </svg>
          <span>后台管理</span>
        </div>
        <div class="feature-card" @click="setQuickPrompt('帮我写一个Python脚本，实现文件批量重命名和分类整理功能')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fa8c16" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 20h9" /><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z" />
          </svg>
          <span>Python 脚本</span>
        </div>
        <div class="feature-card" @click="setQuickPrompt('帮我写一段SQL语句，创建用户订单表并包含增删改查的存储过程')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#722ed1" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2" /><line x1="3" y1="9" x2="21" y2="9" /><line x1="9" y1="21" x2="9" y2="9" />
          </svg>
          <span>SQL 查询</span>
        </div>
      </div>

      <!-- 输入框 -->
      <ChatInput :is-home="true" @send="handleSend" />

      <!-- 加载遮罩 -->
      <div v-if="creating" class="creating-overlay">
        <a-spin />
        <span>正在创建对话...</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import SessionSidebar from '@/components/chat/SessionSidebar.vue'
import { createSession, saveChatMessage, getSessionList, deleteSession } from '@/api/chat'
import type { ChatSession } from '@/types/chat'

const router = useRouter()
const creating = ref(false)
const showSidebar = ref(true)
const sessionList = ref<ChatSession[]>([])

onMounted(async () => {
  const res = await getSessionList()
  if (res.code === 200 && res.data) {
    sessionList.value = res.data
  }
})

/** 切换到已有会话 */
function switchSession(id: number) {
  router.push(`/chat/session/${id}`)
}

/** 新建空会话（侧边栏+按钮） */
async function handleCreateSession() {
  const res = await createSession()
  if (res.code === 200 && res.data) {
    sessionList.value = [res.data, ...sessionList.value]
    router.push(`/chat/session/${res.data.id}`)
  }
}

/** 删除会话 */
async function handleDeleteSession(delId: number) {
  if (sessionList.value.length <= 1) {
    message.warning('至少保留一个会话')
    return
  }
  await deleteSession(delId)
  sessionList.value = sessionList.value.filter(s => s.id !== delId)
}

/** 点击预设卡片 → 自动创建会话并跳转对话页，快速生成+通用 */
function setQuickPrompt(text: string) {
  handleSend({ content: text, mode: 'fast', output: 'stream', format: 'MULTI_FILE' })
}

/** 发送消息 → 创建会话 → 跳到对话页 */
async function handleSend({ content, mode, output, format }: { content: string; mode: 'fast' | 'deep'; output: string; format?: string }) {
  if (creating.value) return
  creating.value = true

  try {
    // 1. 创建新会话
    const sessRes = await createSession()
    if (!sessRes.data?.id) {
      message.error('创建会话失败，请重试')
      return
    }
    const sessionId = sessRes.data.id

    // 2. 保存用户消息
    await saveChatMessage({
      sessionId,
      appId: null,
      messageType: 'user',
      content,
    })

    // 3. 跳转到会话页，携带参数
    router.push({
      path: `/chat/session/${sessionId}`,
      query: { prompt: content, mode, output, format: format || 'HTML' },
    })
  } catch {
    // 错误提示已由 request 拦截器统一展示
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.chat-home {
  display: flex;
  height: calc(100vh - 64px - 48px);
  overflow: hidden;
  background: linear-gradient(160deg, #fcfdff 0%, #f8f9fc 40%, #ffffff 100%);
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

/* 中间区域 */
.home-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  position: relative;
  padding: 40px 24px;
}

/* 品牌区域 */
.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 8px;
}

.brand-icon {
  flex-shrink: 0;
}

.brand-title {
  font-size: 36px;
  font-weight: 700;
  background: linear-gradient(135deg, #1a1a2e 0%, #1677ff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0;
}

.subtitle {
  font-size: 16px;
  color: #888;
  margin: 0 0 32px;
}

/* 能力预设卡片 */
.feature-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-bottom: 28px;
}

.feature-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border: 1px solid #e8e9ed;
  border-radius: 20px;
  background: #fff;
  font-size: 14px;
  color: #444;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}

.feature-card:hover {
  border-color: #1677ff;
  color: #1677ff;
  box-shadow: 0 2px 12px rgba(22, 119, 255, 0.08);
  transform: translateY(-1px);
}

/* 创建中遮罩 */
.creating-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.85);
  border-radius: 16px;
  z-index: 10;
  font-size: 14px;
  color: #666;
}
</style>
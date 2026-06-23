<template>
  <div class="chat-container">
    <!-- 顶部折叠按钮 -->
    <button class="toggle-btn-fixed" @click="showSidebar = !showSidebar">
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#222" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="3" y="3" width="18" height="18" rx="4" ry="4"></rect>
        <line x1="10" y1="3" x2="10" y2="21"></line>
      </svg>
    </button>

    <!-- 【关键修复】绑定点击事件 @click="handleCreateSession" -->
    <button class="create-btn-fixed" @click="handleCreateSession">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#3f3f46" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 20h9"></path>
        <path d="M16.5 3.5a2.121 2.121 0 1 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path>
      </svg>
    </button>

    <div class="sidebar-wrapper" :class="{ 'sidebar-collapse': !showSidebar }">
      <SessionSidebar
          :session-list="sessionList"
          :current-session-id="activeSession"
          @change-session="switchSession"
          @create-session="handleCreateSession"
          @delete-session="handleDeleteSession"
      />
    </div>

    <div class="msg-wrap" v-if="activeSession">
      <div class="msg-list" ref="msgListRef">
        <div v-if="loading" class="loading-tip">加载中...</div>
        <ChatMessage v-for="item in msgList" :key="item.id" :msg="item"/>
      </div>
      <ChatInput @send="handleSend" :is-home="false" />
    </div>

    <div class="home-wrap" v-else>
      <h2 class="home-title">AI对话助手</h2>
      <ChatInput @send="handleHomeSend" :is-home="true" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import SessionSidebar from '@/components/chat/SessionSidebar.vue'
import ChatMessage from '@/components/chat/ChatMessage.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import { getSessionList, getHistoryMsg, saveChatMessage, deleteSession, createSession } from '@/api/chat'
import type { ChatSaveReq, ChatSession, ChatMessage as ChatMessageType } from '@/types/chat'

const sessionList = ref<ChatSession[]>([])
const activeSession = ref<number | null>(null)
const msgList = ref<ChatMessageType[]>([])
const loading = ref(false)
const msgListRef = ref<HTMLDivElement | null>(null)
const CURRENT_USER_ID = 1

const showSidebar = ref(true)

onMounted(async () => {
  const res = await getSessionList()
  if (res.code === 200) {
    sessionList.value = res.data
  }
})

async function switchSession(id: number) {
  activeSession.value = id
  msgList.value = []
  await loadMsg(id)
}

async function loadMsg(sessionId: number) {
  loading.value = true
  try {
    const res = await getHistoryMsg(sessionId)
    if (res.code === 200) {
      msgList.value = res.data.list
      await nextTick()
      if (msgListRef.value) {
        msgListRef.value.scrollTop = msgListRef.value.scrollHeight
      }
    }
  } finally {
    loading.value = false
  }
}

async function handleSend({ content, mode }: { content: string; mode: 'fast' | 'deep' }) {
  if (!activeSession.value || loading.value) return
  console.log('当前执行模式：', mode)
  const params: ChatSaveReq = {
    sessionId: activeSession.value,
    appId: 1,
    messageType: 'user',
    content
  }
  loading.value = true
  try {
    await saveChatMessage(params)
    await loadMsg(activeSession.value!)
  } finally {
    loading.value = false
  }
}

async function handleHomeSend({ content, mode }: { content: string; mode: 'fast' | 'deep' }) {
  loading.value = true
  try {
    console.log('首页执行模式：', mode)
    const sessRes = await createSession(CURRENT_USER_ID, 1, '新对话')
    const newId = sessRes.data.id
    await saveChatMessage({
      sessionId: newId,
      appId: 1,
      messageType: 'user',
      content
    })
    activeSession.value = newId
    msgList.value = []
    await loadMsg(newId)
    const listRes = await getSessionList()
    if (listRes.code === 200) sessionList.value = listRes.data
  } finally {
    loading.value = false
  }
}

async function handleCreateSession() {
  const res = await createSession(CURRENT_USER_ID, 1, '新会话')
  if (res.code === 200) {
    const newSess = res.data
    activeSession.value = newSess.id
    msgList.value = []
    await loadMsg(newSess.id)
    const listRes = await getSessionList()
    sessionList.value = listRes.data
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
}

.toggle-btn-fixed {
  position: absolute;
  top: 16px;
  left: 16px;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  z-index: 99;
  display: flex;
  align-items: center;
  justify-content: center;
}
.toggle-btn-fixed:hover {
  background: #f2f3f5;
}

.create-btn-fixed {
  position: absolute;
  top: 21px;
  left: 66px;
  width: 30px;
  height: 30px;
  border-radius: 12px;
  border: 1px solid #e4e4e7;
  background: #ffffff;
  cursor: pointer;
  z-index: 99;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}
.create-btn-fixed:hover {
  background-color: #f4f4f5;
  border-color: #d4d4d8;
}

.sidebar-wrapper {
  width: 260px;
  flex-shrink: 0;
  transition: width 0.3s ease;
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
  width: 60%;
  max-width: 1200px;
  padding: 15px;
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
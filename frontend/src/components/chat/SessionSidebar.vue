<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <h3 class="sidebar-title">最近项目</h3>
      <!-- 删掉这里原来的 create-btn 按钮 -->
    </div>
    <!-- 下面代码完全不变 -->
    <div class="session-list">
      <div
          v-for="item in sessionList"
          :key="item.id"
          class="session-item"
          :class="{ active: currentSessionId === item.id }"
          @click="$emit('change-session', item.id)"
      >
        <div class="session-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="12" cy="12" r="10" stroke="#888888" stroke-width="1.4" fill="white"/>
            <path d="M7 8.5C7 7.67157 7.67157 7 8.5 7H15.5C16.3284 7 17 7.67157 17 8.5V12.5C17 13.3284 16.3284 14 15.5 14H12L10 16L8 14H8.5C7.67157 14 7 13.3284 7 12.5V8.5Z" stroke="#666666" stroke-width="1.4" fill="none" stroke-linejoin="round"/>
            <circle cx="9.5" cy="10.5" r="0.7" fill="#666666"/>
            <circle cx="12" cy="10.5" r="0.7" fill="#666666"/>
            <circle cx="14.5" cy="10.5" r="0.7" fill="#666666"/>
          </svg>
        </div>
        <span class="session-title">{{ item.sessionTitle ?? '未命名会话' }}</span>
        <button class="more-btn" @click.stop="openContextMenu($event, item)">···</button>
      </div>
    </div>
    <div
        v-if="contextMenu.show"
        class="context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        @click.stop
    >
      <div class="menu-item danger" @click="handleDelete">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" stroke="#ef4444" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
          <line x1="9" y1="4" x2="15" y2="4" />
          <line x1="5" y1="6" x2="19" y2="6" />
          <rect x="5" y="6" width="14" height="14" rx="3" />
          <line x1="9" y1="10" x2="9" y2="16" />
          <line x1="15" y1="10" x2="15" y2="16" />
        </svg>
        <span>删除对话</span>
      </div>
    </div>
    <div v-if="showDeleteModal" class="modal-mask" @click.self="closeDeleteModal">
      <div class="modal-box delete-modal">
        <h3 class="modal-title">确定删除该对话？</h3>
        <p class="modal-desc">删除后所有聊天记录将永久删除，无法恢复。</p>
        <div class="modal-btn-group">
          <button class="btn-cancel" @click="closeDeleteModal">取消</button>
          <button class="btn-delete" @click="confirmDelete">确认删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { ChatSession } from '@/types/chat'

defineProps<{
  sessionList: ChatSession[]
  currentSessionId: number | null
}>()

const emit = defineEmits([
  'change-session',
  'create-session',
  'delete-session'
])

const contextMenu = ref({
  show: false,
  x: 0,
  y: 0,
  currentItem: null as ChatSession | null
})

const showDeleteModal = ref(false)
const targetDel = ref<ChatSession | null>(null)

const hideContextMenu = () => {
  contextMenu.value.show = false
}

const openContextMenu = (e: MouseEvent, item: ChatSession) => {
  contextMenu.value.show = true
  contextMenu.value.x = e.clientX
  contextMenu.value.y = e.clientY
  contextMenu.value.currentItem = item
}

onMounted(() => {
  document.addEventListener('click', hideContextMenu)
})

onUnmounted(() => {
  document.removeEventListener('click', hideContextMenu)
})

const handleDelete = () => {
  if (!contextMenu.value.currentItem) return
  hideContextMenu()
  targetDel.value = contextMenu.value.currentItem
  showDeleteModal.value = true
}

const closeDeleteModal = () => {
  showDeleteModal.value = false
  targetDel.value = null
}

const confirmDelete = () => {
  if (!targetDel.value) return
  emit('delete-session', targetDel.value.id)
  closeDeleteModal()
}
</script>

<style scoped>
.sidebar {
  width: 260px;
  padding: 64px 16px 24px;
  background-color: #fcfcfd;
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  border-right: 1px solid #f0f0f3;
}
.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 28px;
  flex-shrink: 0;
}
.sidebar-title {
  font-size: 17px;
  font-weight: 600;
  color: #27272a;
  margin: 0;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.session-list::-webkit-scrollbar {
  width: 5px;
}
.session-list::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
}
.session-list::-webkit-scrollbar-track {
  background: transparent;
}

.session-item {
  padding: 12px 14px;
  border-radius: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: all 0.2s ease;
  flex-shrink: 0;
  background-color: transparent;
}
.session-icon {
  display: flex;
  align-items: center;
  margin-right: 8px;
}

.session-item:hover {
  background-color: #f4f4f5;
}

.session-item.active {
  background-color: #eff6ff;
}
.session-item.active .session-title {
  font-weight: 550;
  color: #1d4ed8;
}

.session-title {
  flex: 1;
  font-size: 14px;
  color: #52525b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.more-btn {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  border: none;
  background-color: transparent;
  font-size: 14px;
  color: #71717a;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
}
.more-btn:hover {
  background-color: #e4e4e7;
}

.context-menu {
  position: fixed;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  min-width: 150px;
  padding: 6px 0;
  z-index: 9999;
  border: 1px solid #f1f1f1;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}
.menu-item:hover {
  background-color: #f4f4f5;
}
.menu-item.danger {
  color: #ef4444;
}

.modal-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
}

.modal-box {
  background: #fff;
  border-radius: 14px;
  padding: 28px;
}
.delete-modal {
  width: 380px;
  text-align: center;
}

.modal-title {
  font-size: 19px;
  margin: 0 0 12px;
  color: #1f2937;
}
.modal-desc {
  color: #6b7280;
  margin: 0 0 26px;
  line-height: 1.5;
}

.modal-btn-group {
  display: flex;
  gap: 14px;
  justify-content: center;
}
.btn-cancel {
  padding: 9px 26px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-cancel:hover {
  background-color: #f9fafb;
}
.btn-delete {
  padding: 9px 26px;
  background-color: #ef4444;
  color: #fff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
}
.btn-delete:hover {
  background-color: #dc2626;
}
</style>
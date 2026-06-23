<template>
  <!-- 侧边容器 -->
  <div class="sidebar">
    <div class="sidebar-header">
      <h3 class="sidebar-title">最近项目</h3>
      <button class="create-btn" @click="$emit('create-session')">+ 新建</button>
    </div>
    <div class="session-list">
      <div
          v-for="item in sessionList"
          :key="item.id"
          class="session-item"
          :class="{ active: currentSessionId === item.id }"
          @click="$emit('change-session', item.id)"
      >
        <span class="session-title">{{ item.sessionTitle ?? '未命名会话' }}</span>
        <!-- 点击三点弹出菜单 -->
        <button class="more-btn" @click.stop="openContextMenu($event, item)">···</button>
      </div>
    </div>
    <!-- 下拉菜单 -->
    <div
        v-if="contextMenu.show"
        class="context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        @click.stop
    >
      <div class="menu-item danger" @click="handleDelete">
        <span>🗑</span>
        <span>删除</span>
      </div>
    </div>
    <!-- 删除确认弹窗 -->
    <div v-if="showDeleteModal" class="modal-mask" @click.self="closeDeleteModal">
      <div class="modal-box delete-modal">
        <h3 class="modal-title">确定删除对话？</h3>
        <p class="modal-desc">删除后，聊天记录将不可恢复。</p>
        <div class="modal-btn-group">
          <button class="btn-cancel" @click="closeDeleteModal">取消</button>
          <button class="btn-delete" @click="confirmDelete">删除</button>
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

// 移除 rename-session
const emit = defineEmits([
  'change-session',
  'create-session',
  'delete-session'
])

// 菜单状态变量
const contextMenu = ref({
  show: false,
  x: 0,
  y: 0,
  currentItem: null as ChatSession | null
})

// 删除弹窗状态
const showDeleteModal = ref(false)
const targetDel = ref<ChatSession | null>(null)

// 全局关闭菜单函数
const hideContextMenu = () => {
  contextMenu.value.show = false
}

// 点击三点打开下拉菜单
const openContextMenu = (e: MouseEvent, item: ChatSession) => {
  contextMenu.value.show = true
  contextMenu.value.x = e.clientX
  contextMenu.value.y = e.clientY
  contextMenu.value.currentItem = item
}

// 组件挂载时绑定全局点击事件
onMounted(() => {
  document.addEventListener('click', hideContextMenu)
})

// 组件销毁时移除全局事件，防止内存泄漏
onUnmounted(() => {
  document.removeEventListener('click', hideContextMenu)
})

// 打开删除弹窗
const handleDelete = () => {
  if (!contextMenu.value.currentItem) return
  hideContextMenu()
  targetDel.value = contextMenu.value.currentItem
  showDeleteModal.value = true
}

// 关闭删除弹窗
const closeDeleteModal = () => {
  showDeleteModal.value = false
  targetDel.value = null
}

// 确认删除会话
const confirmDelete = () => {
  if (!targetDel.value) return
  emit('delete-session', targetDel.value.id)
  closeDeleteModal()
}
</script>

<style scoped>
.sidebar {
  width: 260px;
  padding: 64px 16px 20px;
  background-color: #f7f8fc;
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  flex-shrink: 0;
}

.sidebar-title {
  font-size: 16px;
  font-weight: 500;
  color: #666;
  margin: 0;
}

.create-btn {
  padding: 5px 12px;
  font-size: 13px;
  border: 1px solid #e5e7eb;
  color: #333;
  border-radius: 20px;
  background: #f7f8fa;
  cursor: pointer;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.session-list::-webkit-scrollbar {
  width: 4px;
}
.session-list::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 2px;
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
  transition: background-color 0.2s ease;
  flex-shrink: 0;
}

.session-item:hover {
  background-color: #eef0f7;
}

.session-item.active {
  background-color: #e4e8f8;
}

.session-item.active .session-title {
  font-weight: 600;
  color: #111;
}

.session-title {
  font-size: 14px;
  color: #444;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.more-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background-color: #e9ebf5;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.more-btn:hover {
  background-color: #dde0ef;
}

.context-menu {
  position: fixed;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 14px rgba(0, 0, 0, 0.1);
  min-width: 140px;
  padding: 4px 0;
  z-index: 9999;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  font-size: 14px;
  cursor: pointer;
}

.menu-item:hover {
  background-color: #f5f7fa;
}

.menu-item.danger {
  color: #f5222d;
}

.modal-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
}

.modal-box {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
}

.delete-modal {
  width: 360px;
  text-align: center;
}

.modal-title {
  font-size: 18px;
  margin: 0 0 12px;
}

.modal-desc {
  color: #666;
  margin: 0 0 24px;
}

.modal-btn-group {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.btn-cancel {
  padding: 8px 24px;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
}

.btn-delete {
  padding: 8px 24px;
  background-color: #f5222d;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
</style>
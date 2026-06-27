<template>
  <div class="sidebar">
    <!-- 顶部工具栏（深色板块） -->
    <div class="sidebar-toolbar">
      <span class="toolbar-label">对话</span>
      <div class="toolbar-actions">
        <a-tooltip placement="bottom" title="收起边栏" color="#1f1f1f">
          <button class="toolbar-btn" @click="$emit('toggle-sidebar')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="3" width="18" height="18" rx="3" ry="3"></rect>
              <line x1="9" y1="3" x2="9" y2="21"></line>
            </svg>
          </button>
        </a-tooltip>
        <a-tooltip placement="bottom" title="开启新对话" color="#1f1f1f">
          <button class="toolbar-btn new-btn" @click="$emit('create-session')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="8" x2="12" y2="16"></line>
              <line x1="8" y1="12" x2="16" y2="12"></line>
            </svg>
          </button>
        </a-tooltip>
      </div>
    </div>

    <!-- 会话列表（浅色板块） -->
    <div class="session-list">
      <template v-for="group in groupedSessions" :key="group.label">
        <div class="group-label">{{ group.label }}</div>
        <div
          v-for="item in group.items"
          :key="item.id"
          class="session-item"
          :class="{ active: currentSessionId === item.id }"
          @click="$emit('change-session', item.id)"
        >
          <span class="session-title">{{ item.sessionTitle ?? '未命名会话' }}</span>
          <button class="more-btn" @click.stop="openContextMenu($event, item)">
            <EllipsisOutlined />
          </button>
        </div>
      </template>
    </div>

    <div
      v-if="contextMenu.show"
      class="context-menu"
      :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      @click.stop
    >
      <div class="menu-item" @click="handleRename">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
          <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
        </svg>
        <span>重命名</span>
      </div>
      <div class="menu-item danger" @click="handleDelete">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="3 6 5 6 21 6" />
          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          <line x1="10" y1="11" x2="10" y2="17" />
          <line x1="14" y1="11" x2="14" y2="17" />
        </svg>
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

    <!-- 重命名弹窗 -->
    <div v-if="showRenameModal" class="modal-mask" @click.self="closeRenameModal">
      <div class="modal-box rename-modal">
        <h3 class="modal-title">重命名</h3>
        <input
            ref="renameInputRef"
            v-model="renameTitle"
            class="rename-input"
            placeholder="输入新名称"
            maxlength="30"
            @keydown.enter="confirmRename"
        />
        <div class="modal-btn-group">
          <button class="btn-cancel" @click="closeRenameModal">取消</button>
          <button class="btn-confirm" :disabled="!renameTitle.trim()" @click="confirmRename">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, nextTick, onMounted, onUnmounted } from 'vue'
import { EllipsisOutlined } from '@ant-design/icons-vue'
import type { ChatSession } from '@/types/chat'

const props = defineProps<{
  sessionList: ChatSession[]
  currentSessionId: number | null
}>()

const emit = defineEmits([
  'change-session',
  'create-session',
  'delete-session',
  'rename-session',
  'toggle-sidebar',
])

const contextMenu = ref({
  show: false,
  x: 0,
  y: 0,
  currentItem: null as ChatSession | null,
})

const showDeleteModal = ref(false)
const showRenameModal = ref(false)
const targetDel = ref<ChatSession | null>(null)
const targetRename = ref<ChatSession | null>(null)
const renameTitle = ref('')
const renameInputRef = ref<HTMLInputElement | null>(null)

const groupedSessions = computed(() => {
  const today: ChatSession[] = []
  const week: ChatSession[] = []
  const older: ChatSession[] = []
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const weekStart = new Date(todayStart.getTime() - 7 * 24 * 60 * 60 * 1000)

  for (const s of props.sessionList) {
    const t = s.lastMessageTime || s.createTime
    const d = t ? new Date(t.replace(' ', 'T')) : new Date(s.createTime.replace(' ', 'T'))
    if (d >= todayStart) today.push(s)
    else if (d >= weekStart) week.push(s)
    else older.push(s)
  }

  const groups = []
  if (today.length) groups.push({ label: '今天', items: today })
  if (week.length) groups.push({ label: '7 天内', items: week })
  if (older.length) groups.push({ label: '更早', items: older })
  return groups
})

const hideContextMenu = () => {
  contextMenu.value.show = false
}

const openContextMenu = (e: MouseEvent, item: ChatSession) => {
  contextMenu.value.show = true
  contextMenu.value.x = e.clientX
  contextMenu.value.y = e.clientY
  contextMenu.value.currentItem = item
}

onMounted(() => document.addEventListener('click', hideContextMenu))
onUnmounted(() => document.removeEventListener('click', hideContextMenu))

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

const handleRename = () => {
  if (!contextMenu.value.currentItem) return
  hideContextMenu()
  targetRename.value = contextMenu.value.currentItem
  renameTitle.value = targetRename.value.sessionTitle || ''
  showRenameModal.value = true
  nextTick(() => renameInputRef.value?.focus())
}

const closeRenameModal = () => {
  showRenameModal.value = false
  targetRename.value = null
  renameTitle.value = ''
}

const confirmRename = () => {
  const title = renameTitle.value.trim()
  if (!title || !targetRename.value) return
  emit('rename-session', targetRename.value.id, title)
  closeRenameModal()
}
</script>

<style scoped>
.sidebar {
  width: 260px;
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  border-right: 1px solid #e0e3e8;
  border-radius: 0 16px 16px 0;
  overflow: hidden;
}

/* ====== 顶部工具栏（深灰底色） ====== */
.sidebar-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: #e4e6eb;
  flex-shrink: 0;
}

.toolbar-label {
  font-size: 15px;
  font-weight: 600;
  color: #3a3d45;
  letter-spacing: 0.3px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5f6368;
  transition: all 0.15s;
}

.toolbar-btn:hover {
  background: #d0d3d9;
  color: #333;
}

.new-btn {
  color: #1677ff;
}

.new-btn:hover {
  background: #c7d9f7;
  color: #0958d9;
}

/* ====== 会话列表（浅灰底色） ====== */
.session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px 16px;
  background-color: #f5f6f8;
}

.session-list::-webkit-scrollbar {
  width: 4px;
}
.session-list::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 2px;
}

.group-label {
  font-size: 12px;
  color: #999;
  padding: 8px 12px 4px;
  font-weight: 500;
}

.session-item {
  padding: 10px 12px;
  border-radius: 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: background-color 0.15s ease;
  flex-shrink: 0;
  gap: 8px;
}

.session-item:hover {
  background-color: #e8eaef;
}

.session-item.active {
  background-color: #dce3f2;
}

.session-item.active .session-title {
  font-weight: 600;
  color: #1677ff;
}

.session-title {
  font-size: 14px;
  color: #444;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.more-btn {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s;
}

.session-item:hover .more-btn {
  opacity: 1;
}

.more-btn:hover {
  background: rgba(0, 0, 0, 0.06);
  color: #555;
}

.context-menu {
  position: fixed;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
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
  inset: 0;
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
  border-radius: 8px;
  cursor: pointer;
}

.btn-delete {
  padding: 8px 24px;
  background-color: #f5222d;
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

/* 重命名弹窗 */
.rename-modal {
  width: 360px;
}

.rename-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  font-size: 15px;
  margin-bottom: 20px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.15s;
}

.rename-input:focus {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.12);
}

.btn-confirm {
  padding: 8px 24px;
  background-color: #1677ff;
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 500;
}

.btn-confirm:hover:not(:disabled) {
  background-color: #4096ff;
}

.btn-confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

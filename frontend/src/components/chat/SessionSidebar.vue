<template>
  <div class="sidebar">
  <!-- 顶部工具栏：边栏切换 -->
    <div class="sidebar-toolbar">
      <a-tooltip placement="bottom" title="收起边栏" color="#1f1f1f">
        <button class="toolbar-btn" @click="$emit('toggle-sidebar')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="3" ry="3"></rect>
            <line x1="9" y1="3" x2="9" y2="21"></line>
          </svg>
        </button>
      </a-tooltip>
      <a-tooltip placement="bottom" title="开启新对话" color="#1f1f1f">
        <button class="toolbar-btn" @click="$emit('create-session')">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="16"></line>
            <line x1="8" y1="12" x2="16" y2="12"></line>
          </svg>
        </button>
      </a-tooltip>
    </div>

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
      <div class="menu-item danger" @click="handleDelete">
        <DeleteOutlined />
        <span>删除</span>
      </div>
    </div>

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
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { EllipsisOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { ChatSession } from '@/types/chat'

const props = defineProps<{
  sessionList: ChatSession[]
  currentSessionId: number | null
}>()

const emit = defineEmits([
  'change-session',
  'create-session',
  'delete-session',
  'toggle-sidebar',
])

const contextMenu = ref({
  show: false,
  x: 0,
  y: 0,
  currentItem: null as ChatSession | null,
})

const showDeleteModal = ref(false)
const targetDel = ref<ChatSession | null>(null)

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
</script>

<style scoped>
.sidebar {
  width: 260px;
  padding: 16px 12px 20px;
  background-color: #f7f8fa;
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  border-right: 1px solid #eceef2;
}

.sidebar-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 12px;
  padding: 0 4px;
}

.toolbar-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #555;
  transition: background 0.15s;
}

.toolbar-btn:hover {
  background: #eceef2;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  overflow-y: auto;
  padding-right: 2px;
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
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: background-color 0.15s ease;
  flex-shrink: 0;
  gap: 8px;
}

.session-item:hover {
  background-color: #eceef2;
}

.session-item.active {
  background-color: #e8eeff;
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
</style>

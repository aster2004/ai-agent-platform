<template>
  <div class="user-msg-wrap" :class="{ editing }">
    <div class="user-bubble" :class="{ 'edit-card': editing }">
      <div v-if="editing" class="edit-area">
        <textarea
          v-model="editText"
          class="edit-input"
          rows="3"
          @keydown.enter.ctrl.prevent="confirmEdit"
        />
        <div class="edit-actions">
          <button class="edit-btn cancel" @click="cancelEdit">取消</button>
          <button class="edit-btn confirm" @click="confirmEdit">发送</button>
        </div>
      </div>
      <div v-else class="bubble-text">{{ msg.content }}</div>
    </div>
    <div v-if="!editing" class="msg-actions">
      <button class="action-icon" title="复制" @click="handleCopy">
        <CopyOutlined />
      </button>
      <button class="action-icon" title="编辑" @click="startEdit">
        <EditOutlined />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, EditOutlined } from '@ant-design/icons-vue'
import type { ChatMessage as ChatMessageItem } from '@/types/chat'

const props = defineProps<{ msg: ChatMessageItem }>()
const emit = defineEmits<{
  resend: [content: string]
}>()

const editing = ref(false)
const editText = ref('')

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.msg.content)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

function startEdit() {
  editText.value = props.msg.content
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  editText.value = ''
}

function confirmEdit() {
  const val = editText.value.trim()
  if (!val) return
  editing.value = false
  emit('resend', val)
}
</script>

<style scoped>
.user-msg-wrap {
  margin: 20px 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  max-width: 75%;
  margin-left: auto;
}

.user-msg-wrap.editing {
  max-width: 100%;
  width: 100%;
  align-items: stretch;
}

.user-bubble {
  background: #f2f3f5;
  border-radius: 16px;
  padding: 12px 16px;
  max-width: 100%;
}

.user-bubble.edit-card {
  width: 100%;
  max-width: 720px;
  margin-left: auto;
  padding: 16px 20px 14px;
  border-radius: 20px;
  border: 1px solid #e8e9ed;
  background: #f2f3f5;
  box-sizing: border-box;
}

.bubble-text {
  font-size: 14px;
  line-height: 1.6;
  color: #1f2329;
  word-break: break-word;
}

.edit-area {
  width: 100%;
}

.edit-input {
  width: 100%;
  border: none;
  background: transparent;
  padding: 0;
  font-size: 15px;
  line-height: 1.6;
  resize: none;
  outline: none;
  font-family: inherit;
  color: #1f2329;
  min-height: 72px;
  box-sizing: border-box;
}

.edit-actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
  justify-content: flex-end;
}

.edit-btn {
  padding: 7px 22px;
  border-radius: 20px;
  font-size: 14px;
  cursor: pointer;
  line-height: 1.4;
  transition: all 0.15s;
}

.edit-btn.cancel {
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #333;
}

.edit-btn.cancel:hover {
  background: #f5f7fa;
}

.edit-btn.confirm {
  border: none;
  background: #1677ff;
  color: #fff;
}

.edit-btn.confirm:hover {
  background: #4096ff;
}

.msg-actions {
  display: flex;
  gap: 4px;
  margin-top: 6px;
  padding-right: 4px;
}

.action-icon {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
}

.action-icon:hover {
  background: #f2f3f5;
  color: #555;
}
</style>

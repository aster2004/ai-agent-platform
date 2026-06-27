<template>
  <div class="user-msg-wrap" :class="{ editing }">
    <!-- 引用代码卡片（在用户气泡上方） -->
    <div v-if="quoteRef" class="quote-ref-card">
      <div class="quote-ref-icon">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17 1l4 0l0 4" /><path d="M3 11V9a4 4 0 0 1 4-4h14" /><path d="M7 23l-4 0l0-4" /><path d="M21 13v2a4 4 0 0 1-4 4H3" />
        </svg>
      </div>
      <div class="quote-ref-body">
        <span class="quote-ref-label">{{ quoteRef.label }}</span>
        <span class="quote-ref-preview">{{ quoteRef.preview }}</span>
      </div>
    </div>

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
      <div v-else class="bubble-text">{{ displayText }}</div>
    </div>
    <div v-if="!editing" class="msg-actions">
      <button class="action-icon" title="复制" @click="handleCopy">
        <CopyOutlined />
      </button>
      <button class="action-icon" title="编辑" @click="startEdit">
        <EditOutlined />
      </button>
      <button class="action-icon" title="删除" @click="handleDelete">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="3 6 5 6 21 6" />
          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          <line x1="10" y1="11" x2="10" y2="17" />
          <line x1="14" y1="11" x2="14" y2="17" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, EditOutlined } from '@ant-design/icons-vue'
import type { ChatMessage as ChatMessageItem } from '@/types/chat'

const props = defineProps<{ msg: ChatMessageItem }>()
const emit = defineEmits<{
  resend: [content: string]
  delete: []
}>()

const editing = ref(false)
const editText = ref('')

/** 解析引用代码卡片信息（豆包风格：在气泡上方显示引用卡片，气泡内仅显示用户文本） */
const quoteRef = computed(() => {
  const c = props.msg.content
  if (!c) return null
  // 匹配格式：参考以下代码：\n```\n<code>\n```\n\n<user text>
  const m = c.match(/^参考以下代码：\n```\n([\s\S]*?)\n```\n\n([\s\S]*)$/)
  if (!m) return null
  const code = m[1]
  const userText = m[2]
  if (!userText.trim()) return null

  // 检测代码语言作为标签
  let label = '引用代码'
  if (/^<(!DOCTYPE|html)/i.test(code)) label = '引用 HTML'
  else if (/^<template/i.test(code)) label = '引用 Vue'
  else if (/^(import|export|const|let|var|function|class|interface|type)\s/m.test(code)) label = '引用 TypeScript'
  else if (/^##\s+📁/.test(code)) label = '引用文件'

  // 前 3 行作为预览
  const lines = code.split('\n').filter((l: string) => l.trim())
  const preview = lines.slice(0, 3).join('\n') + (lines.length > 3 ? '…' : '')

  return { label, preview, code, userText }
})

/** 显示文本：有引用时只显示用户实际输入，否则显示完整内容 */
const displayText = computed(() => {
  return quoteRef.value?.userText ?? props.msg.content
})

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(displayText.value)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

function startEdit() {
  editText.value = displayText.value
  editing.value = true
}

function handleDelete() {
  emit('delete')
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

/* ====== 引用代码卡片（气泡上方，豆包风格） ====== */
.quote-ref-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 6px;
  background: #f7f8fb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  border-left: 3px solid #1677ff;
  max-width: 100%;
  box-sizing: border-box;
}

.quote-ref-icon {
  color: #1677ff;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.quote-ref-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  overflow: hidden;
}

.quote-ref-label {
  font-size: 12px;
  font-weight: 600;
  color: #1677ff;
}

.quote-ref-preview {
  font-size: 11px;
  color: #999;
  font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
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
  white-space: pre-wrap;
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

<template>
  <div class="prd-editor-overlay" @click.self="$emit('close')">
    <div class="prd-editor-panel">
      <div class="editor-header">
        <div class="file-tabs">
          <span class="file-tab active">
            <FileTextOutlined />
            需求文档.md
          </span>
        </div>
        <div class="editor-actions">
          <a-button size="small" @click="copyContent">复制</a-button>
          <a-button size="small" type="primary" :loading="saving" @click="handleSave">
            保存
          </a-button>
          <button class="close-btn" @click="$emit('close')">×</button>
        </div>
      </div>

      <div class="editor-body">
        <div v-if="!editing" class="preview-pane" v-html="renderedHtml" />
        <textarea
          v-else
          v-model="localContent"
          class="edit-pane"
          placeholder="在此编辑需求文档..."
        />
      </div>

      <div class="editor-footer">
        <a-button @click="editing = !editing">
          {{ editing ? '预览' : '编辑' }}
        </a-button>
        <a-button type="primary" :loading="generating" @click="handleGenerate">
          立即创作
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { FileTextOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  content: string
  saving?: boolean
  generating?: boolean
}>()

const emit = defineEmits<{
  close: []
  save: [content: string]
  generate: []
}>()

const localContent = ref(props.content)
const editing = ref(true)

watch(() => props.content, (val) => {
  localContent.value = val
})

const renderedHtml = computed(() => {
  return simpleMarkdown(localContent.value)
})

function simpleMarkdown(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^- (.+)$/gm, '<li>$1</li>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br/>')
}

function handleSave() {
  emit('save', localContent.value)
}

function handleGenerate() {
  emit('save', localContent.value)
  emit('generate')
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(localContent.value)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败')
  }
}
</script>

<style scoped>
.prd-editor-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.prd-editor-panel {
  width: min(900px, 100%);
  height: min(80vh, 720px);
  background: #fff;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.file-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 6px;
  background: #f5f5f5;
  font-size: 13px;
  color: #333;
}

.file-tab.active {
  background: #e6f4ff;
  color: #1677ff;
}

.editor-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.close-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  font-size: 20px;
  color: #999;
  cursor: pointer;
  border-radius: 4px;
}

.close-btn:hover {
  background: #f5f5f5;
}

.editor-body {
  flex: 1;
  overflow: auto;
  padding: 20px 24px;
}

.preview-pane {
  font-size: 14px;
  line-height: 1.8;
  color: #333;
}

.preview-pane :deep(h2) {
  font-size: 18px;
  margin: 16px 0 8px;
}

.preview-pane :deep(h3) {
  font-size: 15px;
  margin: 12px 0 6px;
}

.edit-pane {
  width: 100%;
  height: 100%;
  min-height: 400px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.7;
  font-family: 'Consolas', 'Monaco', monospace;
  resize: none;
  outline: none;
}

.edit-pane:focus {
  border-color: #1677ff;
}

.editor-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #f0f0f0;
}
</style>

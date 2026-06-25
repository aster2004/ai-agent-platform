<template>
  <div class="code-block">
    <div class="code-header">
      <span class="code-lang">{{ label }}</span>
      <div class="code-actions">
        <button class="action-btn" @click="handleCopy">
          <CopyOutlined />
          <span>复制</span>
        </button>
        <button class="action-btn" @click="handleDownload">
          <DownloadOutlined />
          <span>下载</span>
        </button>
        <button class="action-btn run-btn" @click="handleRun" :disabled="running">
          <PlayCircleOutlined />
          <span>{{ running ? '加载中' : '运行' }}</span>
        </button>
      </div>
    </div>

    <div class="code-body-wrap" :class="{ collapsed: isCollapsed }">
      <pre class="code-body"><code>{{ content }}</code></pre>
    </div>

    <button class="collapse-btn" @click="isCollapsed = !isCollapsed">
      <DownOutlined :class="{ rotated: !isCollapsed }" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  CopyOutlined,
  DownloadOutlined,
  PlayCircleOutlined,
  DownOutlined,
} from '@ant-design/icons-vue'
import type { CodeFile } from '@/types/codegen'

const props = defineProps<{
  content: string
  filename?: string
  lang?: string
  allFiles?: CodeFile[]
  appId?: number
}>()

const emit = defineEmits<{
  preview: [files: CodeFile[]]
}>()

const isCollapsed = ref(false)
const running = ref(false)

const label = props.filename || props.lang || 'code'

function collectFiles(): CodeFile[] {
  if (props.allFiles?.length) {
    return props.allFiles
  }
  return [{ path: props.filename || 'index.html', content: props.content }]
}

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(props.content)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败')
  }
}

function handleDownload() {
  const name = props.filename?.includes('.')
    ? props.filename
    : `${props.filename ?? 'code'}.${props.lang ?? 'txt'}`
  const blob = new Blob([props.content], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
}

async function handleRun() {
  const files = collectFiles()
  const primary = files.find(f => f.path.endsWith('.html'))?.content ?? props.content
  const isStandaloneHtml =
    primary.includes('<html') || primary.includes('<!DOCTYPE')

  if (files.length === 1 && isStandaloneHtml && !primary.includes('type="module"')) {
    const win = window.open('', '_blank')
    if (win) {
      win.document.open()
      win.document.write(primary)
      win.document.close()
    }
    return
  }

  running.value = true
  try {
    emit('preview', files)
  } finally {
    running.value = false
  }
}
</script>

<style scoped>
.code-block {
  margin-top: 12px;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  border-radius: 12px;
  background: #f4f5f7;
  overflow: hidden;
  border: 1px solid #e8e9ed;
}

.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #eceef2;
  border-bottom: 1px solid #e2e4e8;
}

.code-lang {
  font-size: 13px;
  color: #555;
  font-family: 'Consolas', 'Monaco', monospace;
}

.code-actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: none;
  background: transparent;
  color: #555;
  font-size: 12px;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.15s;
}

.action-btn:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.06);
  color: #1677ff;
}

.run-btn {
  color: #1677ff;
}

.code-body-wrap {
  max-height: 480px;
  overflow: auto;
  transition: max-height 0.25s ease;
}

.code-body-wrap.collapsed {
  max-height: 120px;
}

.code-body {
  margin: 0;
  padding: 14px 16px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: #1f2329;
}

.collapse-btn {
  width: 100%;
  padding: 6px;
  border: none;
  background: #eceef2;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #888;
}

.collapse-btn:hover {
  background: #e2e4e8;
}

.rotated {
  transform: rotate(180deg);
  transition: transform 0.2s;
}
</style>


<template>
  <div class="code-block">
    <div class="code-header">
      <div class="code-header-left">
        <span class="code-dot dot-red" />
        <span class="code-dot dot-yellow" />
        <span class="code-dot dot-green" />
        <span class="code-lang">{{ label }}</span>
      </div>
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

    <div ref="codeBodyWrapRef" class="code-body-wrap" :class="{ collapsed: isCollapsed }" @scroll="onScroll">
      <pre class="code-body"><code v-html="highlightedCode" /></pre>
    </div>

    <!-- 横向滚动按钮 -->
    <div class="h-scroll-bar">
      <button class="h-scroll-btn" :disabled="!canScrollLeft" @click="scrollLeft">
        <LeftOutlined />
      </button>
      <button class="h-scroll-btn" :disabled="!canScrollRight" @click="scrollRight">
        <RightOutlined />
      </button>
    </div>

    <button class="collapse-btn" @click="isCollapsed = !isCollapsed">
      <DownOutlined :class="{ rotated: !isCollapsed }" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined, DownloadOutlined, PlayCircleOutlined, DownOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue'
import { highlightCode } from '@/utils/syntaxHighlight'
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
const codeBodyWrapRef = ref<HTMLElement | null>(null)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)
const showHScroll = ref(false)

const label = props.filename || props.lang || 'code'

const highlightedCode = computed(() => highlightCode(props.content, props.lang || 'text'))

// ---- 横向滚动 ----

function updateScrollState() {
  const el = codeBodyWrapRef.value
  if (!el) return
  const needsScroll = el.scrollWidth > el.clientWidth + 1
  showHScroll.value = needsScroll
  canScrollLeft.value = el.scrollLeft > 0
  canScrollRight.value = el.scrollLeft < el.scrollWidth - el.clientWidth - 1
}

function onScroll() {
  updateScrollState()
}

function scrollLeft() {
  codeBodyWrapRef.value?.scrollBy({ left: -200, behavior: 'smooth' })
}

function scrollRight() {
  codeBodyWrapRef.value?.scrollBy({ left: 200, behavior: 'smooth' })
}

onMounted(() => {
  nextTick(() => updateScrollState())
})

watch(() => props.content, () => {
  nextTick(() => updateScrollState())
})

function collectFiles(): CodeFile[] {
  if (props.allFiles?.length) return props.allFiles
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
  const isStandaloneHtml = primary.includes('<html') || primary.includes('<!DOCTYPE')
  if (files.length === 1 && isStandaloneHtml && !primary.includes('type="module"')) {
    const win = window.open('', '_blank')
    if (win) { win.document.open(); win.document.write(primary); win.document.close() }
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
  margin: 14px 0;
  width: 100%;
  box-sizing: border-box;
  border-radius: 12px;
  background: #1e1e2e;
  overflow: hidden;
  border: 1px solid #2a2a3d;
}

/* 头部栏 —— macOS 风格圆点 */
.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #252536;
  border-bottom: 1px solid #2a2a3d;
}

.code-header-left {
  display: flex;
  align-items: center;
  gap: 7px;
}

.code-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.dot-red    { background: #ff5f57; }
.dot-yellow { background: #febc2e; }
.dot-green  { background: #28c840; }

.code-lang {
  font-size: 12px;
  color: #8b8b9e;
  margin-left: 6px;
  font-family: 'SF Mono', 'Consolas', 'Monaco', monospace;
  font-weight: 500;
}

.code-actions {
  display: flex;
  gap: 2px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: none;
  background: transparent;
  color: #8b8b9e;
  font-size: 12px;
  cursor: pointer;
  border-radius: 5px;
  transition: all 0.15s;
}

.action-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.06);
  color: #c8c8d8;
}

.run-btn {
  color: #5e9cf7;
}

.run-btn:hover:not(:disabled) {
  color: #7ab4ff;
}

/* 代码主体 */
.code-body-wrap {
  max-height: 420px;
  overflow-y: auto;
  overflow-x: hidden;
  transition: max-height 0.25s ease;
}

.code-body-wrap::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}

.code-body-wrap::-webkit-scrollbar-thumb {
  background: #3a3a55;
  border-radius: 2px;
}

.code-body-wrap.collapsed {
  max-height: 100px;
}

.code-body {
  margin: 0;
  padding: 16px 18px;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre;
  color: #e1e1ee;
  tab-size: 2;
  -moz-tab-size: 2;
}

/* ====== 语法高亮 Token 颜色 ====== */
.code-body :deep(.tk-keyword) { color: #c792ea; font-style: italic; }
.code-body :deep(.tk-string)  { color: #c3e88d; }
.code-body :deep(.tk-comment) { color: #676e95; font-style: italic; }
.code-body :deep(.tk-tag)     { color: #82aaff; }
.code-body :deep(.tk-num)     { color: #f78c6c; }
.code-body :deep(.tk-bool)    { color: #ff5370; }
.code-body :deep(.tk-fn)      { color: #82aaff; }
.code-body :deep(.tk-prop)    { color: #80cbc4; }

/* 横向滚动按钮栏 */
.h-scroll-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 3px 10px;
  background: #252536;
  border-top: 1px solid #2a2a3d;
}

.h-scroll-btn {
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: #676e95;
  cursor: pointer;
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 9px;
  transition: all 0.15s;
}

.h-scroll-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
  color: #c8c8d8;
}

.h-scroll-btn:disabled {
  opacity: 0.25;
  cursor: default;
}

/* 折叠按钮 */
.collapse-btn {
  width: 100%;
  padding: 5px;
  border: none;
  background: #252536;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #676e95;
  transition: all 0.15s;
}

.collapse-btn:hover {
  background: #2a2a3d;
  color: #8b8b9e;
}

.rotated {
  transform: rotate(180deg);
  transition: transform 0.2s;
}
</style>
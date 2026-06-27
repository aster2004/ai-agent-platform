<template>
  <div
      class="preview-panel"
      :style="panelStyle"
      :class="{ collapsed: collapsed && !visible }"
  >
    <!-- 左侧拖拽把手 -->
    <div
        v-if="visible"
        class="resize-grip"
        @mousedown="onResizeStart"
        title="拖拽调整宽度"
    />


    <!-- 面板内容 -->
    <div class="preview-inner" v-show="visible">
      <!-- 标题栏 -->
      <div class="preview-header">
        <div class="preview-title">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2" /><line x1="8" y1="21" x2="16" y2="21" /><line x1="12" y1="17" x2="12" y2="21" />
          </svg>
          <span>网页预览</span>
          <span v-if="htmlContent" class="preview-badge">已渲染</span>
        </div>
        <div class="preview-actions">
          <!-- 预览 / 代码 切换 -->
          <div class="view-mode-toggle">
            <button
                class="mode-toggle-btn"
                :class="{ active: viewMode === 'preview' }"
                @click="viewMode = 'preview'"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2" /><circle cx="12" cy="12" r="3" />
              </svg>
              <span>预览</span>
            </button>
            <button
                class="mode-toggle-btn"
                :class="{ active: viewMode === 'code' }"
                @click="viewMode = 'code'"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="16 18 22 12 16 6" /><polyline points="8 6 2 12 8 18" />
              </svg>
              <span>代码</span>
            </button>
          </div>
          <button class="header-btn" title="刷新预览" @click="refreshPreview">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="23 4 23 10 17 10" /><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
            </svg>
          </button>
          <button class="header-btn" title="在新窗口打开" @click="openInNewWindow">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /><polyline points="15 3 21 3 21 9" /><line x1="10" y1="14" x2="21" y2="3" />
            </svg>
          </button>
          <button class="header-btn" title="下载网页" @click="handleDownload">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
            </svg>
          </button>
          <div class="header-divider" />
          <button class="header-btn close-btn" title="关闭预览" @click="emit('close')">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>
      </div>

      <!-- 预览内容 -->
      <div class="preview-body">
        <div v-if="!htmlContent && !sourceCode" class="preview-empty">
          <div class="empty-icon">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="#d0d5dd" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2" /><line x1="8" y1="21" x2="16" y2="21" /><line x1="12" y1="17" x2="12" y2="21" />
            </svg>
          </div>
          <p>暂无预览内容</p>
          <span>AI 生成代码后将自动展示</span>
        </div>

        <!-- 预览模式：iframe 渲染 -->
        <iframe
            v-else-if="viewMode === 'preview' && htmlContent"
            :key="'preview-' + previewKey"
            :srcdoc="htmlContent"
            class="preview-iframe"
            sandbox="allow-scripts allow-same-origin"
            title="网页预览"
        />

        <!-- 代码模式：语法高亮 -->
        <div v-else class="code-view">
          <div class="code-header">
            <span class="code-lang">{{ sourceLang === 'multi' ? '多文件项目' : formatLangLabel(sourceLang) }}</span>
            <button class="copy-btn" @click="copyCode">复制代码</button>
          </div>
          <pre class="code-block"><code v-html="highlightedCode" /></pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { highlightCode } from '@/utils/syntaxHighlight'
import { formatLangLabel } from '@/utils/formatCode'
import { mergeToPreviewHtml } from '@/utils/previewMerge'
import { parseContentToFiles } from '@/utils/splitMultiFile'
import type { CodeFile } from '@/types/codegen'

const props = defineProps<{
  // 支持单段文本 或者 本轮批量消息文本数组（用于多代码片段合并）
  content: string | string[]
  visible: boolean
  width: number
  collapsed: boolean
}>()

const emit = defineEmits<{
  'resize-start': [e: MouseEvent]
  toggle: []
  close: []
}>()

const previewKey = ref(0)
const viewMode = ref<'preview' | 'code'>('preview')

// ===================== 计算属性 =====================

/** iframe 预览用的 HTML — 统一合并管道（支持批量多片段自动合并） */
const htmlContent = computed(() => {
  try {
    if (Array.isArray(props.content)) {
      const validList = props.content.filter(s => s?.trim())
      if (validList.length === 0) return null
      return mergeToPreviewHtml(validList)
    } else {
      if (!props.content?.trim()) return null
      return mergeToPreviewHtml(props.content)
    }
  } catch (err) {
    // 编译异常兜底错误页面
    return `
    <!DOCTYPE html>
    <html lang="zh-CN">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>代码编译异常</title>
      <style>
        body { padding: 24px; font-family: system-ui; }
        h2 { color: #f53f3f; }
        pre { padding: 16px; background: #f7f8fa; border-radius: 8px; color: #f53f3f; white-space: pre-wrap; }
      </style>
    </head>
    <body>
      <h2>代码预览编译失败</h2>
      <pre>${(err as Error).message}\n${(err as Error).stack ?? ''}</pre>
    </body>
    </html>
    `
  }
})

/** 代码视图用的原始源码 */
const sourceCode = computed(() => {
  const raw = Array.isArray(props.content) ? props.content.join('\n\n') : props.content
  return extractSourceCode(raw)
})

const sourceLang = computed(() => {
  const raw = Array.isArray(props.content) ? props.content.join('\n\n') : props.content || ''
  if (/<template[\s>]/i.test(raw) || /```vue\b/i.test(raw)) return 'vue'
  if (parseContentToFiles(raw)) return 'multi'
  return 'html'
})

const highlightedCode = computed(() => {
  const code = sourceCode.value
  if (!code) return ''
  const lang = sourceLang.value === 'vue' ? 'vue' : sourceLang.value === 'multi' ? 'text' : 'html'
  return highlightCode(code, lang)
})

const panelStyle = computed(() => {
  if (props.visible) return { width: props.width + 'px' }
  if (props.collapsed) return { width: '36px' }
  return { width: '0px', minWidth: '0px' }
})

// ===================== 内容变化监听 =====================

watch(() => props.content, () => {
  if (props.visible && htmlContent.value) {
    previewKey.value++
  }
})

watch([htmlContent, sourceCode], () => {
  if (htmlContent.value) {
    viewMode.value = 'preview'
  } else if (sourceCode.value) {
    viewMode.value = 'code'
  }
})

// ===================== UI 事件 =====================

function onResizeStart(e: MouseEvent) { emit('resize-start', e) }
function refreshPreview() { previewKey.value++ }

function openInNewWindow() {
  if (!htmlContent.value) return
  const win = window.open('', '_blank')
  if (win) {
    win.document.open()
    win.document.write(htmlContent.value)
    win.document.close()
  }
}

function copyCode() {
  if (!sourceCode.value) return
  navigator.clipboard.writeText(sourceCode.value).then(() => {
    message.success('代码已复制')
  }).catch(() => {
    message.error('复制失败')
  })
}

function handleDownload() {
  if (!htmlContent.value) {
    message.warning('暂无可下载的网页内容')
    return
  }
  try {
    const blob = new Blob([htmlContent.value], { type: 'text/html;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ts = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19)
    a.download = `preview-${ts}.html`
    a.click()
    URL.revokeObjectURL(url)
    message.success('下载成功')
  } catch {
    message.error('下载失败')
  }
}

// ===================== 源码提取 =====================

/** 提取源码用于代码视图（优先多文件 → Vue SFC → HTML 代码块 → 纯文本） */
function extractSourceCode(content: string): string | null {
  if (!content?.trim()) return null

  const multiFiles = parseContentToFiles(content)
  if (multiFiles) return formatMultiFileSource(multiFiles)

  const vueRegex = /```vue\s*\n?([\s\S]*?)```/gi
  const vueBlocks: string[] = []
  let m: RegExpExecArray | null
  while ((m = vueRegex.exec(content)) !== null) {
    if (m[1].trim()) vueBlocks.push(m[1].trim())
  }
  if (vueBlocks.length > 0) return vueBlocks[vueBlocks.length - 1]

  const htmlRegex = /```html\s*\n?([\s\S]*?)```/gi
  const htmlBlocks: string[] = []
  while ((m = htmlRegex.exec(content)) !== null) {
    if (m[1].trim()) htmlBlocks.push(m[1].trim())
  }
  if (htmlBlocks.length > 0) return htmlBlocks[htmlBlocks.length - 1]

  const bareRegex = /```\s*\n?([\s\S]*?)```/g
  const bareBlocks: string[] = []
  while ((m = bareRegex.exec(content)) !== null) {
    const code = m[1].trim()
    if (code && (code.includes('<') || code.includes('<!DOCTYPE'))) bareBlocks.push(code)
  }
  if (bareBlocks.length > 0) return bareBlocks[bareBlocks.length - 1]

  const trimmed = content.trim()
  if (trimmed.startsWith('<!DOCTYPE') || trimmed.startsWith('<html') || (trimmed.startsWith('<') && trimmed.includes('</'))) {
    return trimmed
  }

  return null
}

function formatMultiFileSource(files: CodeFile[]): string {
  return files.map(f => {
    return `// ====== ${f.path} ======\n\n${f.content}`
  }).join('\n\n')
}
</script>

<style scoped>
.preview-panel {
  flex-shrink: 0;
  overflow: hidden;
  background: #fafbfc;
  position: relative;
}

.preview-panel:not(.collapsed) {
  transition: width 0.15s ease;
}

.preview-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
}

/* ====== 左侧拖拽把手 ====== */
.resize-grip {
  position: absolute;
  left: -6px;
  top: 0;
  bottom: 0;
  width: 22px;
  cursor: col-resize;
  z-index: 20;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
}

.resize-grip::after {
  content: '';
  width: 3px;
  height: 100%;
  border-radius: 2px;
  background: #e8eaed;
  transition: all 0.15s;
}

.resize-grip:hover::after {
  width: 4px;
  background: #1677ff;
  box-shadow: 0 0 6px rgba(22, 119, 255, 0.25);
}

.resize-grip:active::after {
  width: 4px;
  background: #0958d9;
}

/* ====== 标题栏 ====== */
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px 10px 16px;
  background: #fff;
  border-bottom: 1px solid #eef0f2;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  font-weight: 600;
  color: #444;
}

.preview-badge {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 10px;
  background: #e6f7ed;
  color: #389e0d;
  font-weight: 500;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ====== 预览/代码切换按钮组 ====== */
.view-mode-toggle {
  display: flex;
  background: #f2f3f5;
  border-radius: 8px;
  padding: 2px;
  gap: 1px;
}

.mode-toggle-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #888;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.mode-toggle-btn:hover {
  color: #555;
}

.mode-toggle-btn.active {
  background: #fff;
  color: #1677ff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.header-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  transition: all 0.15s;
}

.header-btn:hover {
  background: #f2f3f5;
  color: #555;
}

.header-btn.close-btn:hover {
  background: #fef2f2;
  color: #ef4444;
}

.header-divider {
  width: 1px;
  height: 20px;
  background: #e5e7eb;
  margin: 0 2px;
}

/* ====== 预览主体 ====== */
.preview-body {
  flex: 1;
  overflow: hidden;
  background: #fff;
}

.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
}

/* ====== 空状态 ====== */
.preview-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 8px;
  background: #fafbfc;
}

.empty-icon {
  margin-bottom: 4px;
}

.preview-empty p {
  font-size: 14px;
  color: #999;
  margin: 0;
  font-weight: 500;
}

.preview-empty span {
  font-size: 12px;
  color: #ccc;
}

/* ====== 代码视图 ====== */
.code-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.code-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: #f8f9fb;
  border-bottom: 1px solid #eef0f2;
  flex-shrink: 0;
}

.code-lang {
  font-size: 11px;
  font-weight: 600;
  color: #999;
  letter-spacing: 0.3px;
}

.copy-btn {
  padding: 3px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 5px;
  background: #fff;
  color: #666;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}

.copy-btn:hover {
  border-color: #1677ff;
  color: #1677ff;
}

.code-block {
  flex: 1;
  overflow: auto;
  margin: 0;
  padding: 14px;
  background: #1e1e2e;
  color: #cdd6f4;
  font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre;
  tab-size: 2;
}

.code-block :deep(.tk-keyword) { color: #cba6f7; }
.code-block :deep(.tk-string)  { color: #a6e3a1; }
.code-block :deep(.tk-comment) { color: #6c7086; font-style: italic; }
.code-block :deep(.tk-tag)     { color: #89b4fa; }
.code-block :deep(.tk-fn)      { color: #89b4fa; }
.code-block :deep(.tk-num)     { color: #fab387; }
.code-block :deep(.tk-bool)    { color: #fab387; }
.code-block :deep(.tk-prop)    { color: #89dceb; }
</style>
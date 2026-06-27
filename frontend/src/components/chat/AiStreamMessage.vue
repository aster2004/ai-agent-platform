<template>
  <div class="ai-msg-body">
    <!-- 思考中状态 -->
    <div v-if="thinking && !content" class="thinking-indicator">
      <span class="think-dot" />
      <span class="think-dot" />
      <span class="think-dot" />
    </div>

    <!-- 已完成 或 流式：统一浅灰圆角卡片（无有效代码时回退 markdown，避免空白白块） -->
    <div v-else-if="isCodeContent && hasRenderableCode" class="code-card">
      <!-- 头部栏 -->
      <div class="card-header">
        <span class="card-lang-tag">{{ isStreaming ? streamLangLabel : codeLangLabel }}</span>
        <div class="card-actions">
          <!-- 流式 → "生成中" 徽章 -->
          <span v-if="isStreaming" class="streaming-badge-light">生成中</span>
          <!-- 完成 → 操作按钮 -->
          <template v-else>
            <button class="icon-btn" title="复制代码" @click.stop="handleCopyToClipboard">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
              </svg>
            </button>
            <button v-if="showPreviewBtn" class="icon-btn preview-btn" :title="previewBtnLabel" @click.stop="handlePreviewClick">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" /><circle cx="12" cy="12" r="3" />
              </svg>
              <span>{{ previewBtnLabel }}</span>
            </button>
            <button class="icon-btn" title="引用" @click.stop="handleQuoteClick">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M17 1l4 0l0 4" /><path d="M3 11V9a4 4 0 0 1 4-4h14" /><path d="M7 23l-4 0l0-4" /><path d="M21 13v2a4 4 0 0 1-4 4H3" />
              </svg>
            </button>
            <button class="icon-btn" title="放大查看" @click.stop="handleEnlargeClick">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="15 3 21 3 21 9" /><polyline points="9 21 3 21 3 15" /><line x1="21" y1="3" x2="14" y2="10" /><line x1="3" y1="21" x2="10" y2="14" />
              </svg>
            </button>
            <button class="icon-btn" title="下载代码" @click.stop="handleDownload">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="7 10 12 15 17 10" /><line x1="12" y1="15" x2="12" y2="3" />
              </svg>
            </button>
          </template>
        </div>
      </div>

      <!-- 白色代码区 -->
      <div ref="cardCodeBodyRef" class="card-code-body" :class="{ streaming: isStreaming }" @scroll="onCardScroll">
        <pre class="card-code"><code v-html="isStreaming ? streamHighlighted : completedHighlightedCode" /></pre>
        <span v-if="isStreaming" class="stream-cursor-light">|</span>
      </div>

      <!-- 横向滚动按钮 -->
      <div v-if="!isStreaming" class="card-h-scroll-bar">
        <button class="card-h-scroll-btn" :disabled="!cardCanScrollLeft" @click="cardScrollLeft">
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <button class="card-h-scroll-btn" :disabled="!cardCanScrollRight" @click="cardScrollRight">
          <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 普通的 markdown 文本/多文件 -->
    <template v-else-if="!isStreaming && (!isCodeContent || !hasRenderableCode)">
      <template v-for="(seg, idx) in parsedSegments" :key="idx">
        <div v-if="seg.type === 'text'" class="md-text" v-html="seg.html" />
        <CodeBlock
            v-else-if="seg.type === 'code'"
            :content="seg.code || ''"
            :lang="seg.lang || 'text'"
            :filename="codeFilename(seg.lang, idx)"
            @preview="emit('preview')"
        />
      </template>
    </template>

    <!-- 流式文本 -->
    <template v-else-if="isStreaming && (!isCodeContent || !hasRenderableCode)">
      <template v-for="(seg, idx) in parsedSegments" :key="idx">
        <div v-if="seg.type === 'text'" class="md-text" v-html="seg.html" />
        <CodeBlock
            v-else-if="seg.type === 'code'"
            :content="seg.code || ''"
            :lang="seg.lang || 'text'"
            :filename="codeFilename(seg.lang, idx)"
            @preview="emit('preview')"
        />
      </template>
      <span class="stream-cursor">|</span>
    </template>

    <!-- 重新生成 / 删除 -->
    <div v-if="!isStreaming && content" class="msg-actions">
      <button class="act-btn" @click.stop="$emit('retry')">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="1 4 1 10 7 10" /><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" />
        </svg>
        <span>重新生成</span>
      </button>
      <button class="act-btn delete-btn" @click.stop="handleDelete">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="3 6 5 6 21 6" />
          <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
          <line x1="10" y1="11" x2="10" y2="17" />
          <line x1="14" y1="11" x2="14" y2="17" />
        </svg>
        <span>删除</span>
      </button>
    </div>

    <!-- 错误 -->
    <div v-if="errorMsg" class="error-text">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
      <span>{{ errorMsg }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, nextTick, watch } from 'vue'
import { message } from 'ant-design-vue'
import { parseMarkdown, normalizeAiContent } from '@/utils/streamMarkdown'
import type { MarkdownSegment } from '@/utils/streamMarkdown'
import { highlightCode } from '@/utils/syntaxHighlight'
import CodeBlock from './CodeBlock.vue'

const props = defineProps<{
  content: string
  isStreaming?: boolean
  thinking?: boolean
  errorMsg?: string
}>()

const emit = defineEmits<{
  retry: []
  delete: []
  preview: []
  quote: [code: string]
  enlarge: [code: string]
}>()

// ---- 横向滚动 ----

const cardCodeBodyRef = ref<HTMLElement | null>(null)
const cardCanScrollLeft = ref(false)
const cardCanScrollRight = ref(false)
const showCardHScroll = ref(false)

function updateCardScrollState() {
  const el = cardCodeBodyRef.value
  if (!el) return
  const needsScroll = el.scrollWidth > el.clientWidth + 1
  showCardHScroll.value = needsScroll
  cardCanScrollLeft.value = el.scrollLeft > 0
  cardCanScrollRight.value = el.scrollLeft < el.scrollWidth - el.clientWidth - 1
}

function onCardScroll() {
  updateCardScrollState()
}

function cardScrollLeft() {
  cardCodeBodyRef.value?.scrollBy({ left: -200, behavior: 'smooth' })
}

function cardScrollRight() {
  cardCodeBodyRef.value?.scrollBy({ left: 200, behavior: 'smooth' })
}

onMounted(() => {
  nextTick(() => updateCardScrollState())
})

watch(() => props.content, () => {
  nextTick(() => updateCardScrollState())
})

// ---- 代码检测 ----

const isCodeContent = computed(() => {
  const c = props.content?.trim()
  if (!c) return false
  // 工作流 / 需求分析消息始终走 markdown，避免误识别为代码卡片
  if (/^##\s+(📋|🔄)/.test(c)) return false
  if (/^<(!DOCTYPE|html|head|body|div|meta|link|style|script|title|template)/i.test(c)) return true
  if (c.startsWith('[') && c.includes('"path"') && c.includes('"content"')) return true
  if (c.includes('```')) return true
  // ## 📁 文件标题格式（拆分后每条消息一个文件）
  if (/^##\s+📁/.test(c)) return true
  return false
})

/** 提取后是否仍有可展示的代码正文（防止空白 code-card） */
const hasRenderableCode = computed(() => {
  // 流式阶段只要有内容就渲染
  if (props.isStreaming) {
    return !!props.content?.trim()
  }
  // 完成后：codeText 有内容则渲染；若被意外清空但原始内容有代码标记也渲染
  if (codeText.value.trim()) return true
  const raw = props.content?.trim() || ''
  // 包含代码块标记或文件标题 → 保留代码卡片
  if (raw.includes('```') || /^##\s+📁/.test(raw)) return true
  return false
})

/** 根据文件路径扩展名获取显示标签 */
function fileLangLabel(ext: string): string {
  const map: Record<string, string> = {
    html: 'HTML', htm: 'HTML',
    vue: 'Vue',
    js: 'JavaScript', mjs: 'JavaScript', jsx: 'React JSX',
    ts: 'TypeScript', tsx: 'React TSX',
    css: 'CSS', scss: 'SCSS', less: 'Less',
    json: 'JSON',
    py: 'Python', java: 'Java',
    xml: 'XML', md: 'Markdown',
  }
  return map[ext] || ext.toUpperCase() || 'Code'
}

/** 从 content 中提取 ## 📁 文件名（若有），返回语言标签 */
function extractFileHeaderLang(content: string): string | null {
  const m = content.match(/^##\s+📁\s+(\S+)/m)
  if (!m) return null
  const ext = (m[1].split('.').pop() || '').toLowerCase()
  return fileLangLabel(ext)
}

const codeLangLabel = computed(() => {
  // 优先从原始 content 提取 ## 📁 文件标题
  const rawHeaderLang = extractFileHeaderLang(props.content)
  if (rawHeaderLang) return rawHeaderLang

  const c = normalizeAiContent(props.content)

  const headerLang = extractFileHeaderLang(c)
  if (headerLang) return headerLang

  // 从代码块语言标记检测
  if (c.includes('```vue')) return 'Vue'
  if (c.includes('```html')) return 'HTML'
  if (c.includes('```javascript') || c.includes('```js')) return 'JavaScript'
  if (c.includes('```typescript') || c.includes('```ts')) return 'TypeScript'
  if (c.includes('```css')) return 'CSS'
  if (c.includes('```scss')) return 'SCSS'
  if (c.includes('```json')) return 'JSON'
  if (c.includes('```python') || c.includes('```py')) return 'Python'
  if (c.includes('```java')) return 'Java'

  // 裸 HTML
  const raw = props.content?.trim() || ''
  if (/^<(!DOCTYPE|html)/i.test(raw)) return 'HTML'
  if (/^<template[\s>]/i.test(raw)) return 'Vue'

  // 还可能是未拆分的旧多文件消息
  if (raw.startsWith('[') && raw.includes('"path"') && raw.includes('"content"')) return '多文件'

  return 'Code'
})

/** 提取纯代码文本（去 markdown 标记） */
const codeText = computed(() => {
  let c = normalizeAiContent(props.content)
  // 去掉 markdown 代码块标记
  c = c.replace(/```\w*\s*\n?/g, '').replace(/```/g, '')
  // 去掉多文件标题
  c = c.replace(/## 📁 .*/g, '')
  return c.trim()
})

/** 完成后的代码语言标识 → 语法高亮语言 key */
function highlightLangFromLabel(label: string): string {
  const map: Record<string, string> = {
    Vue: 'vue', HTML: 'html', JavaScript: 'javascript', TypeScript: 'typescript',
    CSS: 'css', SCSS: 'scss', Less: 'less', JSON: 'json',
    Python: 'python', Java: 'java', Markdown: 'markdown',
    'React JSX': 'jsx', 'React TSX': 'tsx',
  }
  return map[label] || 'text'
}

/** 完成后浅灰卡片的语法高亮 */
const completedHighlightedCode = computed(() => {
  const code = codeText.value
  if (!code) return ''
  return highlightCode(code, highlightLangFromLabel(codeLangLabel.value))
})

// ---- 流式相关 ----

const streamLangLabel = computed(() => {
  // 先规范化内容（将多文件 JSON/裸 Vue 等转为标准 markdown 格式），再检测语言
  const raw = props.content?.trim() || ''
  const c = normalizeAiContent(raw)

  // 流式中有 ## 📁 文件标题 → 提取文件名类型
  const headerLang = extractFileHeaderLang(c)
  if (headerLang) return headerLang

  // 未拆分的多文件 JSON 数组（流式早期阶段）
  if (c.startsWith('[') && c.includes('"path"') && c.includes('"content"')) return '多文件'

  if (/^<(!DOCTYPE|html)/i.test(c)) return 'HTML'
  if (/^```vue|^<template>/im.test(c)) return 'Vue'
  if (/^```javascript|^```js/i.test(c)) return 'JavaScript'
  if (/^```typescript|^```ts/i.test(c)) return 'TypeScript'
  if (/^```css/i.test(c)) return 'CSS'
  return 'Code'
})

const streamHighlighted = computed(() => {
  const raw = props.content || ''
  if (!raw.trim()) return ''
  // 先规范化内容：将多文件 JSON、裸 HTML/Vue 等转为标准 markdown 代码块格式
  const c = normalizeAiContent(raw)
  const lang = highlightLangFromLabel(streamLangLabel.value)
  let code = c
  code = code.replace(/^```\w*\s*\n?/, '').replace(/\n?```$/, '')
  // 去掉流式中的 ## 📁 文件标题行
  code = code.replace(/^##\s+📁\s+[^\n]*\n*/gm, '')
  return highlightCode(code, lang)
})

// ---- 已完成 markdown 渲染 ----

const displayContent = computed(() => {
  if (!props.content) return ''
  if (props.isStreaming) return props.content
  return normalizeAiContent(props.content)
})

const parsedSegments = computed<MarkdownSegment[]>(() => {
  const c = displayContent.value
  if (!c) return []
  return parseMarkdown(c)
})

function codeFilename(lang: string | undefined, idx: number): string {
  const map: Record<string, string> = {
    html: 'index.html', vue: 'App.vue', typescript: 'main.ts', javascript: 'main.js',
    css: 'style.css', python: 'main.py', java: 'Main.java', json: 'data.json',
  }
  return map[lang ?? ''] ?? `code-${idx + 1}.${lang || 'txt'}`
}

// ---- 操作 ----

function handleDelete() {
  emit('delete')
}

// ---- 预览按钮 ----

/** 快速生成模式：仅 HTML 内容显示"运行"按钮（多文件/Vue 不显示） */
const showPreviewBtn = computed(() => {
  if (!props.content || props.isStreaming) return false
  const code = codeText.value
  if (!code) return false
  const raw = props.content
  // 裸 HTML 开头（包括 codeText 提取后的纯代码）
  if (/^<(!DOCTYPE|html)/i.test(code.trim())) return true
  // ```html 代码块
  if (raw.includes('```html')) return true
  // DOCTYPE 或 <html 标签在内容中任意位置（AI 可能在前面加了说明文字）
  if (/<!DOCTYPE\s+html/i.test(raw)) return true
  if (/<html[\s>]/i.test(raw)) return true
  // ## 📁 .html 文件标题
  if (/^##\s+📁\s+.*\.html/i.test(raw)) return true
  return false
})

const previewBtnLabel = computed(() => '运行')

function handlePreviewClick() {
  emit('preview')
}

function handleQuoteClick() {
  const code = codeText.value
  if (!code) return
  emit('quote', code)
}

function handleEnlargeClick() {
  const code = codeText.value
  if (!code) return
  emit('enlarge', code)
}

function handleCopyToClipboard() {
  const code = codeText.value
  if (!code) return
  navigator.clipboard.writeText(code).then(() => {
    message.success('代码已复制')
  }).catch(() => {
    message.error('复制失败')
  })
}

/** 下载代码为文件 */
function handleDownload() {
  const code = codeText.value
  if (!code) return
  // 先从 ## 📁 标题提取文件名
  const headerMatch = props.content.match(/^##\s+📁\s+(\S+)/m)
  const filename = headerMatch?.[1] || codeLabelToFilename(codeLangLabel.value)
  const blob = new Blob([code], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
  message.success(`已下载 ${filename}`)
}

/** 语言标签 → 默认文件名 */
function codeLabelToFilename(label: string): string {
  const map: Record<string, string> = {
    HTML: 'index.html',
    Vue: 'App.vue',
    JavaScript: 'main.js',
    TypeScript: 'main.ts',
    'React JSX': 'App.jsx',
    'React TSX': 'App.tsx',
    CSS: 'style.css',
    SCSS: 'style.scss',
    Less: 'style.less',
    JSON: 'data.json',
    Python: 'main.py',
    Java: 'Main.java',
    Markdown: 'README.md',
    XML: 'data.xml',
  }
  return map[label] || 'code.txt'
}


</script>

<style scoped>
.ai-msg-body {
  font-size: 15px;
  line-height: 1.78;
  color: #1f2329;
  padding: 2px 0;
}

/* ====== 思考动画 ====== */
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 8px 0;
}

.think-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #1677ff;
  animation: dotBounce 1.3s ease-in-out infinite;
}

.think-dot:nth-child(2) { animation-delay: 0.15s; }
.think-dot:nth-child(3) { animation-delay: 0.3s; }

@keyframes dotBounce {
  0%, 60%, 100% { opacity: 0.25; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-4px); }
}

/* ====== 浅灰圆角代码卡片（已完成） ====== */
.code-card {
  background: #f0f2f5;
  border-radius: 12px;
  overflow: hidden;
  margin: 4px 0;
}

/* 头部栏 */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
}

.card-lang-tag {
  font-size: 12px;
  font-weight: 600;
  color: #666;
  padding: 3px 12px;
  background: #e0e3e8;
  border-radius: 6px;
  letter-spacing: 0.3px;
}

.card-actions {
  display: flex;
  gap: 2px;
}

.icon-btn {
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #888;
  transition: all 0.15s;
}

.icon-btn:hover {
  background: #e0e3e8;
  color: #444;
}

.preview-btn {
  color: #1677ff;
  gap: 3px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 500;
  width: auto;
}

.preview-btn:hover {
  background: #e8f0ff;
  color: #1677ff;
}

/* 白色代码区 */
.card-code-body {
  background: #ffffff;
  margin: 0 8px 0;
  border-radius: 8px;
  overflow-y: auto;
  overflow-x: hidden;
  max-height: 480px;
}

.card-code-body::-webkit-scrollbar { width: 5px; }
.card-code-body::-webkit-scrollbar-thumb {
  background: #d0d3d7;
  border-radius: 3px;
}

.card-code {
  margin: 0;
  padding: 16px 18px;
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre;
  color: #1a1a2e;
  tab-size: 2;
}

/* 浅色主题语法高亮 */
.card-code :deep(.tk-keyword) { color: #7c3aed; }
.card-code :deep(.tk-string)  { color: #059669; }
.card-code :deep(.tk-comment) { color: #9ca3af; font-style: italic; }
.card-code :deep(.tk-tag)     { color: #2563eb; }
.card-code :deep(.tk-fn)      { color: #2563eb; }
.card-code :deep(.tk-num)     { color: #d97706; }
.card-code :deep(.tk-bool)    { color: #dc2626; }
.card-code :deep(.tk-prop)    { color: #0891b2; }

/* ====== 横向滚动按钮栏（浅色主题） ====== */
.card-h-scroll-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 3px 10px;
  margin: 0 8px 8px;
  border-top: 1px solid #e8eaed;
}

.card-h-scroll-btn {
  width: 20px;
  height: 20px;
  border: 1px solid #e0e3e8;
  background: #fff;
  color: #888;
  cursor: pointer;
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}

.card-h-scroll-btn:hover:not(:disabled) {
  background: #f5f7fa;
  border-color: #d0d3d7;
  color: #444;
}

.card-h-scroll-btn:disabled {
  opacity: 0.25;
  cursor: default;
}

/* ====== 流式徽章（浅色主题） ====== */
.streaming-badge-light {
  font-size: 11px;
  color: #1677ff;
  padding: 3px 10px;
  border-radius: 10px;
  background: #e8f0ff;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.7; }
  50% { opacity: 1; }
}

/* ====== 流式光标 ====== */
.stream-cursor-light {
  display: inline;
  color: #1677ff;
  font-weight: 400;
  font-size: 14px;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  animation: cursorBlink 0.7s step-end infinite;
  vertical-align: baseline;
}

.stream-cursor {
  display: inline;
  color: #1677ff;
  font-weight: 300;
  animation: cursorBlink 0.7s step-end infinite;
  font-size: 16px;
}

@keyframes cursorBlink {
  50% { opacity: 0; }
}

/* ====== 流式时卡片代码区无滚动限制 ====== */
.card-code-body.streaming {
  max-height: none;
}

/* ====== 底部按钮 ====== */
.msg-actions {
  display: flex;
  gap: 6px;
  margin-top: 8px;
}

.act-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #666;
  font-size: 12px;
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.15s;
}

.act-btn:hover {
  background: #f5f7fa;
  border-color: #d0d3d7;
  color: #333;
}

.act-btn.delete-btn:hover {
  color: #f5222d;
  border-color: #ffa39e;
  background: #fff2f0;
}

/* ====== 错误 ====== */
.error-text {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #f5222d;
  font-size: 14px;
  padding: 8px 0;
}

/* ====== Markdown 文本样式 ====== */
.md-text :deep(.md-heading) {
  margin: 18px 0 8px;
  font-weight: 600;
  color: #1a1a2e;
  line-height: 1.4;
}

.md-text :deep(h1) { font-size: 1.45em; border-bottom: 1px solid #edf0f5; padding-bottom: 6px; }
.md-text :deep(h2) { font-size: 1.25em; }
.md-text :deep(h3) { font-size: 1.1em; }
.md-text :deep(h4) { font-size: 1em; }

.md-text :deep(.md-p) { margin: 4px 0; }
.md-text :deep(.md-spacer) { height: 10px; }

.md-text :deep(.md-li) {
  margin: 3px 0 3px 18px;
  display: list-item;
  list-style-type: disc;
}

.md-text :deep(.md-li-ol) { list-style-type: decimal; }

.md-text :deep(.inline-code) {
  background: #eef1f5;
  border-radius: 4px;
  padding: 2px 7px;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', 'Monaco', monospace;
  font-size: 0.88em;
  color: #d63384;
  overflow-x: auto;
}

.md-text :deep(strong) { font-weight: 600; color: #1a1a2e; }
.md-text :deep(em) { font-style: italic; }

.md-text :deep(.md-quote) {
  border-left: 3px solid #1677ff;
  padding: 4px 14px;
  margin: 10px 0;
  color: #666;
  background: #f7f8fb;
  border-radius: 0 8px 8px 0;
  font-size: 0.95em;
}

.md-text :deep(.md-hr) {
  border: none;
  border-top: 1px solid #edf0f5;
  margin: 16px 0;
}

.md-text :deep(a) {
  color: #1677ff;
  text-decoration: none;
}

.md-text :deep(a:hover) { text-decoration: underline; }
</style>
<template>
  <div class="deep-workspace">
    <!-- ====== 文件树区域 ====== -->
    <div class="workspace-tree-panel" :class="{ collapsed: treeCollapsed }" :style="treeCollapsed ? {} : { width: `${treeWidth}px` }">
      <div class="tree-header">
        <FolderOutlined />
        <span v-if="!treeCollapsed">项目文件</span>
        <button class="tree-collapse-btn" @click="treeCollapsed = !treeCollapsed" :title="treeCollapsed ? '展开文件树' : '收起文件树'">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline v-if="treeCollapsed" points="9 18 15 12 9 6" />
            <polyline v-else points="15 18 9 12 15 6" />
          </svg>
        </button>
      </div>
      <div v-if="!treeCollapsed" class="tree-body">
        <div v-if="!files.length" class="tree-empty">
          <a-spin v-if="generating" size="small" />
          <span v-else>暂无文件</span>
        </div>
        <FileTreeNode
          v-for="node in tree"
          :key="node.path"
          :node="node"
          :selected-path="selectedPath"
          :expanded-paths="expandedPaths"
          @select="handleSelect"
          @toggle="toggleExpand"
        />
      </div>
    </div>

    <!-- 文件树 ↔ 预览 分割线 -->
    <ColumnResizer v-if="!treeCollapsed" @start="$emit('resize-tree-start', $event)" />

    <!-- ====== 预览区域 ====== -->
    <div class="workspace-main" :class="{ collapsed: previewCollapsed }">
      <div class="workspace-toolbar">
        <div class="toolbar-left">
          <div class="view-tabs">
            <button class="view-tab" :class="{ active: viewMode === 'preview' }" @click="viewMode = 'preview'">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2" ry="2" /><circle cx="12" cy="12" r="3" />
              </svg>
              <span v-if="!previewCollapsed">网页预览</span>
            </button>
            <button class="view-tab" :class="{ active: viewMode === 'code' }" @click="viewMode = 'code'">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="16 18 22 12 16 6" /><polyline points="8 6 2 12 8 18" />
              </svg>
              <span v-if="!previewCollapsed">代码</span>
            </button>
          </div>
        </div>
        <button class="preview-collapse-btn" @click="previewCollapsed = !previewCollapsed" :title="previewCollapsed ? '展开预览' : '收起预览'">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline v-if="previewCollapsed" points="15 18 9 12 15 6" />
            <polyline v-else points="9 18 15 12 9 6" />
          </svg>
        </button>
      </div>

      <div v-if="!previewCollapsed" class="workspace-content">
        <!-- 生成中 -->
        <div v-if="generating && !files.length" class="workspace-loading">
          <a-spin size="large" />
          <p>正在生成项目代码，请稍候...</p>
        </div>

        <!-- 不支持预览：项目包含非 HTML 文件 -->
        <div v-else-if="files.length && !allHtml && viewMode === 'preview'" class="workspace-unsupported">
          <div class="unsupported-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#d0d5dd" stroke-width="1" stroke-linecap="round" stroke-linejoin="round">
              <rect x="2" y="3" width="20" height="14" rx="2" ry="2" /><line x1="8" y1="21" x2="16" y2="21" /><line x1="12" y1="17" x2="12" y2="21" />
              <line x1="2" y1="3" x2="22" y2="17" stroke="#ff7875" stroke-width="1.5" />
            </svg>
          </div>
          <p class="unsupported-title">该项目不支持预览</p>
          <button class="unsupported-btn" @click="viewMode = 'code'">查看代码</button>
        </div>

        <!-- 网页预览 — 纯 HTML 项目直接渲染 -->
        <iframe
          v-else-if="viewMode === 'preview' && previewHtml"
          :key="'preview-' + previewKey"
          :srcdoc="previewHtml"
          class="preview-iframe"
          sandbox="allow-scripts allow-same-origin"
          title="项目预览"
        />

        <!-- 代码视图 -->
        <div v-else-if="selectedFile" class="code-pane">
          <div class="code-pane-header">{{ selectedFile.path }}</div>
          <pre class="code-pane-body"><code>{{ selectedFile.content }}</code></pre>
        </div>

        <!-- 预览模式代码视图（多文件源码） -->
        <div v-else-if="viewMode === 'code' && files.length" class="code-pane">
          <div class="code-pane-header">项目源码（{{ files.length }} 个文件）</div>
          <pre class="code-pane-body"><code v-html="previewHighlightedCode" /></pre>
        </div>

        <!-- 空状态 -->
        <div v-else class="workspace-empty">
          <p>请选择文件或等待代码生成</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { FolderOutlined } from '@ant-design/icons-vue'
import type { CodeFile } from '@/types/codegen'
import { formatCodeFilesToContent } from '@/utils/formatCodeFiles'
import { buildFileTree } from '@/utils/fileTreeUtils'
import FileTreeNode from './FileTreeNode.vue'
import ColumnResizer from './ColumnResizer.vue'
import { mergeToPreviewHtml } from '@/utils/previewMerge'
import { highlightCode } from '@/utils/syntaxHighlight'

defineEmits<{
  'resize-tree-start': [event: MouseEvent]
}>()

const props = withDefaults(defineProps<{
  files: CodeFile[]
  generating?: boolean
  treeWidth?: number
}>(), {
  generating: false,
  treeWidth: 240,
})

const viewMode = ref<'preview' | 'code'>('preview')
const selectedPath = ref('')
const treeCollapsed = ref(false)
const previewCollapsed = ref(false)

const expandedPaths = ref<Set<string>>(new Set())
const tree = computed(() => buildFileTree(props.files))

const previewKey = ref(0)

/** 项目是否全部为 HTML 文件（纯 HTML 项目才可渲染） */
const allHtml = computed(() => {
  if (!props.files.length) return false
  return props.files.every(f => /\.html?$/i.test(f.path))
})

/** 将项目文件合并为可预览的 HTML 字符串 */
const previewHtml = computed(() => {
  if (!props.files.length) return null
  return mergeToPreviewHtml(formatCodeFilesToContent(props.files))
})

/** 预览用的纯文本源码（代码视图用） */
const previewSourceCode = computed(() => {
  if (!props.files.length) return null
  return props.files.map(f => `// ====== ${f.path} ======\n\n${f.content}`).join('\n\n')
})

/** 预览模式下的代码高亮 */
const previewHighlightedCode = computed(() => {
  const code = previewSourceCode.value
  if (!code) return ''
  return highlightCode(code, 'text')
})

const selectedFile = computed(() =>
  props.files.find(file => file.path === selectedPath.value),
)

watch(
  () => props.files,
  (files) => {
    // 展开所有文件夹
    const next = new Set(expandedPaths.value)
    for (const file of files) {
      const parts = file.path.replace(/\\/g, '/').split('/').filter(Boolean)
      let current = ''
      for (let i = 0; i < parts.length - 1; i++) {
        current = current ? `${current}/${parts[i]}` : parts[i]
        next.add(current)
      }
    }
    expandedPaths.value = next

    if (!files.length) {
      selectedPath.value = ''
      return
    }
    // 自动选中第一个 HTML 文件或第一个文件
    if (!selectedPath.value || !files.some(file => file.path === selectedPath.value)) {
      const preferred = files.find(file => /index\.html$/i.test(file.path))
        ?? files.find(file => file.path.endsWith('.vue'))
        ?? files[0]
      selectedPath.value = preferred.path
    }
    // 只有全部可预览时才默认打开预览模式
    if (files.length) {
      viewMode.value = 'preview'
    }
  },
  { immediate: true, deep: true },
)

function handleSelect(path: string) {
  selectedPath.value = path
  viewMode.value = 'code'
}

/** 内容变化时刷新 iframe */
watch(previewHtml, (val, prev) => {
  if (val && val !== prev) previewKey.value++
})


function toggleExpand(path: string) {
  const next = new Set(expandedPaths.value)
  if (next.has(path)) next.delete(path)
  else next.add(path)
  expandedPaths.value = next
}
</script>

<style scoped>
.deep-workspace {
  display: flex;
  height: 100%;
  background: #fff;
  overflow: hidden;
  min-width: 0;
}

/* ====== 文件树面板 ====== */
.workspace-tree-panel {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fafbfc;
  border-right: 1px solid #eceef2;
  min-width: 160px;
}

.workspace-tree-panel.collapsed {
  min-width: auto;
  width: 38px;
}

.tree-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #333;
  border-bottom: 1px solid #eceef2;
  flex-shrink: 0;
  min-height: 41px;
}

.workspace-tree-panel.collapsed .tree-header {
  justify-content: center;
  padding: 10px 6px;
}

.tree-collapse-btn {
  margin-left: auto;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  transition: all 0.15s;
  flex-shrink: 0;
}

.workspace-tree-panel.collapsed .tree-collapse-btn {
  margin-left: 0;
}

.tree-collapse-btn:hover {
  background: #e8eaed;
  color: #555;
}

.tree-body {
  flex: 1;
  overflow: auto;
  padding: 6px 0;
}

.tree-empty {
  padding: 20px 12px;
  font-size: 13px;
  color: #999;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ====== 预览区域 ====== */
.workspace-main {
  flex: 1;
  min-width: 320px;
  display: flex;
  flex-direction: column;
}

.workspace-main.collapsed {
  min-width: auto;
  flex: none;
  width: 100px;
}

.workspace-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #eceef2;
  flex-shrink: 0;
  min-height: 41px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.view-tabs {
  display: flex;
  gap: 4px;
}

.view-tab {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #666;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.view-tab:hover {
  background: #f2f3f5;
}

.view-tab.active {
  background: #e6f4ff;
  color: #1677ff;
}

.preview-collapse-btn {
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  transition: all 0.15s;
  flex-shrink: 0;
}

.preview-collapse-btn:hover {
  background: #e8eaed;
  color: #555;
}

/* ====== 内容区 ====== */
.workspace-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.workspace-loading,
.workspace-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #999;
}

/* 不支持预览 */
.workspace-unsupported {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #fafbfc;
}

.unsupported-icon {
  margin-bottom: 4px;
}

.unsupported-title {
  font-size: 15px;
  font-weight: 600;
  color: #555;
  margin: 0;
}

.unsupported-desc {
  font-size: 13px;
  color: #999;
  margin: 0;
}

.unsupported-btn {
  margin-top: 8px;
  padding: 6px 18px;
  border: 1px solid #1677ff;
  border-radius: 8px;
  background: #fff;
  color: #1677ff;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.unsupported-btn:hover {
  background: #e6f4ff;
}

.preview-iframe {
  flex: 1;
  width: 100%;
  min-height: 300px;
  border: none;
  background: #fff;
}

/* 代码视图 */
.code-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
}

.code-pane-header {
  padding: 10px 14px;
  border-bottom: 1px solid #eceef2;
  font-size: 13px;
  color: #555;
  font-family: Consolas, Monaco, monospace;
  flex-shrink: 0;
}

.code-pane-body {
  flex: 1;
  margin: 0;
  padding: 16px;
  overflow: auto;
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
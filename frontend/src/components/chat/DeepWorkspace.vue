<template>
  <div class="deep-workspace">
    <FileTree
      class="workspace-tree"
      :style="{ width: `${treeWidth}px` }"
      :files="files"
      :selected-path="selectedPath"
      :loading="generating"
      @select="selectFile"
    />

    <ColumnResizer @start="$emit('resize-tree-start', $event)" />

    <div class="workspace-main">
      <div class="workspace-toolbar">
        <div class="view-tabs">
          <button class="view-tab" :class="{ active: viewMode === 'preview' }" @click="viewMode = 'preview'">
            应用预览
          </button>
          <button class="view-tab" :class="{ active: viewMode === 'code' }" @click="viewMode = 'code'">
            代码
          </button>
        </div>
      </div>

      <div class="workspace-content">
        <div v-if="generating && !files.length" class="workspace-loading">
          <a-spin size="large" />
          <p>正在生成项目代码，请稍候...</p>
        </div>

        <ChatPreviewPanel
          v-else-if="viewMode === 'preview'"
          :content="previewContent"
          :visible="true"
          :width="9999"
          :collapsed="false"
          class="embedded-preview"
        />

        <div v-else-if="selectedFile" class="code-pane">
          <div class="code-pane-header">{{ selectedFile.path }}</div>
          <pre class="code-pane-body"><code>{{ selectedFile.content }}</code></pre>
        </div>

        <div v-else class="workspace-empty">
          <p>请选择文件或等待代码生成</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CodeFile } from '@/types/codegen'
import { formatCodeFilesToContent } from '@/utils/formatCodeFiles'
import FileTree from './FileTree.vue'
import ColumnResizer from './ColumnResizer.vue'
import ChatPreviewPanel from './ChatPreviewPanel.vue'

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

const previewContent = computed(() => formatCodeFilesToContent(props.files))

const selectedFile = computed(() =>
  props.files.find(file => file.path === selectedPath.value),
)

watch(
  () => props.files,
  (files) => {
    if (!files.length) {
      selectedPath.value = ''
      return
    }
    if (!selectedPath.value || !files.some(file => file.path === selectedPath.value)) {
      const preferred = files.find(file => /index\.html$/i.test(file.path))
        ?? files.find(file => file.path.endsWith('.vue'))
        ?? files[0]
      selectedPath.value = preferred.path
    }
    if (files.length) viewMode.value = 'preview'
  },
  { immediate: true, deep: true },
)

function selectFile(path: string) {
  selectedPath.value = path
  viewMode.value = 'code'
}
</script>

<style scoped>
.deep-workspace {
  flex: 1;
  min-width: 320px;
  display: flex;
  height: 100%;
  background: #fff;
  overflow: hidden;
}

.workspace-tree {
  flex-shrink: 0;
  min-width: 160px;
}

.workspace-main {
  flex: 1;
  min-width: 320px;
  display: flex;
  flex-direction: column;
}

.workspace-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid #eceef2;
}

.view-tabs {
  display: flex;
  gap: 4px;
}

.view-tab {
  padding: 6px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #666;
  font-size: 13px;
  cursor: pointer;
}

.view-tab.active {
  background: #e6f4ff;
  color: #1677ff;
}

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

.embedded-preview {
  flex: 1;
  min-height: 0;
  width: 100% !important;
}

.embedded-preview :deep(.toggle-btn-wrap) {
  display: none;
}

.embedded-preview :deep(.resize-grip) {
  display: none;
}

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

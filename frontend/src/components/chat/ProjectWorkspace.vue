<template>
  <div class="project-workspace">
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
          <button
            class="view-tab"
            :class="{ active: viewMode === 'preview' }"
            @click="viewMode = 'preview'"
          >
            <GlobalOutlined />
            应用预览
          </button>
          <button
            class="view-tab"
            :class="{ active: viewMode === 'code' }"
            @click="viewMode = 'code'"
          >
            <CodeOutlined />
            代码
          </button>
        </div>
        <div class="toolbar-actions">
          <a-button v-if="viewMode === 'preview'" size="small" @click="refreshPreview">刷新</a-button>
          <a-button v-if="viewMode === 'preview'" size="small" type="primary" @click="openInNewTab">
            新窗口打开
          </a-button>
        </div>
      </div>

      <div class="workspace-content">
        <div v-if="generating && !files.length" class="workspace-loading">
          <a-spin size="large" />
          <p>正在生成项目代码，请稍候...</p>
        </div>

        <WorkflowPreviewPanel
          v-else-if="viewMode === 'preview'"
          ref="previewRef"
          embedded
          hide-header
          :app-id="appId"
          :files="files"
          :title="title"
          :auto-load="files.length > 0"
        />

        <div v-else-if="selectedFile" class="code-pane">
          <div class="code-pane-header">
            <FileOutlined />
            <span>{{ selectedFile.path }}</span>
          </div>
          <pre class="code-pane-body"><code>{{ selectedFile.content }}</code></pre>
        </div>

        <div v-else class="workspace-empty">
          <FileSearchOutlined class="empty-icon" />
          <p>请选择文件展示</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CodeOutlined, FileOutlined, FileSearchOutlined, GlobalOutlined } from '@ant-design/icons-vue'
import type { CodeFile } from '@/types/codegen'
import FileTree from './FileTree.vue'
import ColumnResizer from './ColumnResizer.vue'
import WorkflowPreviewPanel from './WorkflowPreviewPanel.vue'

defineEmits<{
  'resize-tree-start': [event: MouseEvent]
}>()

const props = withDefaults(defineProps<{
  appId: number
  files: CodeFile[]
  title?: string
  generating?: boolean
  treeWidth?: number
}>(), {
  title: '应用预览',
  generating: false,
  treeWidth: 240,
})

const viewMode = ref<'preview' | 'code'>('preview')
const selectedPath = ref('')
const previewRef = ref<InstanceType<typeof WorkflowPreviewPanel> | null>(null)

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
    if (files.length && viewMode.value === 'preview') {
      previewRef.value?.refreshPreview?.()
    }
  },
  { immediate: true, deep: true },
)

function selectFile(path: string) {
  selectedPath.value = path
  viewMode.value = 'code'
}

async function refreshPreview() {
  await previewRef.value?.refreshPreview?.(true)
}

function openInNewTab() {
  previewRef.value?.openInNewTab?.()
}

defineExpose({ refreshPreview })
</script>

<style scoped>
.project-workspace {
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
  background: #fff;
}

.view-tabs {
  display: flex;
  gap: 4px;
}

.view-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
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

.toolbar-actions {
  display: flex;
  gap: 8px;
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

.empty-icon {
  font-size: 42px;
  color: #d9d9d9;
}

.code-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
}

.code-pane-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid #eceef2;
  font-size: 13px;
  color: #555;
  font-family: 'Consolas', 'Monaco', monospace;
}

.code-pane-body {
  flex: 1;
  margin: 0;
  padding: 16px;
  overflow: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  color: #1f2329;
}
</style>

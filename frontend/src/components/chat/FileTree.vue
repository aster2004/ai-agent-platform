<template>
  <div class="file-tree">
    <div class="file-tree-header">
      <FolderOutlined />
      <span>项目文件</span>
    </div>
    <div v-if="!files.length" class="file-tree-empty">
      <a-spin v-if="loading" size="small" />
      <span v-else>暂无文件</span>
    </div>
    <div v-else class="file-tree-body">
      <FileTreeNode
        v-for="node in tree"
        :key="node.path"
        :node="node"
        :selected-path="selectedPath"
        :expanded-paths="expandedPaths"
        @select="$emit('select', $event)"
        @toggle="toggleExpand"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { FolderOutlined } from '@ant-design/icons-vue'
import type { CodeFile } from '@/types/codegen'
import { buildFileTree } from '@/utils/fileTreeUtils'
import FileTreeNode from './FileTreeNode.vue'

const props = defineProps<{
  files: CodeFile[]
  selectedPath?: string
  loading?: boolean
}>()

defineEmits<{
  select: [path: string]
}>()

const expandedPaths = ref<Set<string>>(new Set())

const tree = computed(() => buildFileTree(props.files))

watch(
  () => props.files,
  (files) => {
    if (!files.length) return
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
  },
  { immediate: true, deep: true },
)

function toggleExpand(path: string) {
  const next = new Set(expandedPaths.value)
  if (next.has(path)) next.delete(path)
  else next.add(path)
  expandedPaths.value = next
}
</script>

<style scoped>
.file-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fafbfc;
  border-right: 1px solid #eceef2;
}

.file-tree-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  font-size: 13px;
  font-weight: 600;
  color: #333;
  border-bottom: 1px solid #eceef2;
}

.file-tree-empty {
  padding: 24px 14px;
  font-size: 13px;
  color: #999;
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-tree-body {
  flex: 1;
  overflow: auto;
  padding: 8px 0;
}
</style>

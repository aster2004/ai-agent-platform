<template>
  <div class="tree-node">
    <div
      class="tree-row"
      :class="{ selected: node.isFile && node.path === selectedPath }"
      :style="{ paddingLeft: `${depth * 14 + 10}px` }"
      @click="handleClick"
    >
      <span v-if="!node.isFile" class="tree-toggle" @click.stop="$emit('toggle', node.path)">
        <CaretRightOutlined :class="{ expanded: expandedPaths.has(node.path) }" />
      </span>
      <span v-else class="tree-toggle placeholder" />
      <component :is="node.isFile ? FileOutlined : FolderOutlined" class="tree-icon" />
      <span class="tree-label">{{ node.name }}</span>
    </div>
    <template v-if="!node.isFile && node.children && expandedPaths.has(node.path)">
      <FileTreeNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :depth="depth + 1"
        :selected-path="selectedPath"
        :expanded-paths="expandedPaths"
        @select="$emit('select', $event)"
        @toggle="$emit('toggle', $event)"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { CaretRightOutlined, FileOutlined, FolderOutlined } from '@ant-design/icons-vue'
import type { FileTreeNode as TreeNode } from '@/utils/fileTreeUtils'

defineOptions({ name: 'FileTreeNode' })

const props = defineProps<{
  node: TreeNode
  depth?: number
  selectedPath?: string
  expandedPaths: Set<string>
}>()

const emit = defineEmits<{
  select: [path: string]
  toggle: [path: string]
}>()

const depth = props.depth ?? 0

function handleClick() {
  if (props.node.isFile) {
    emit('select', props.node.path)
    return
  }
  emit('toggle', props.node.path)
}
</script>

<style scoped>
.tree-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding-right: 10px;
  cursor: pointer;
  color: #444;
  font-size: 13px;
}

.tree-row:hover {
  background: #f0f2f5;
}

.tree-row.selected {
  background: #e6f4ff;
  color: #1677ff;
}

.tree-toggle {
  width: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #999;
}

.tree-toggle.placeholder {
  visibility: hidden;
}

.tree-toggle :deep(.expanded) {
  transform: rotate(90deg);
  transition: transform 0.15s;
}

.tree-icon {
  font-size: 13px;
  color: #888;
}

.tree-row.selected .tree-icon {
  color: #1677ff;
}

.tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

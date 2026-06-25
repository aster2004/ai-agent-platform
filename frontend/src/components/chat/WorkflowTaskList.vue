<template>
  <div class="task-list" v-if="tasks.length">
    <button class="task-header" @click="expanded = !expanded">
      <span class="task-count">已执行 {{ tasks.length }} 个任务</span>
      <DownOutlined class="chevron" :class="{ expanded }" />
    </button>
    <div v-show="expanded" class="task-body">
      <div v-for="(task, idx) in tasks" :key="idx" class="task-item">
        <span class="task-icon">{{ taskIcon(task.type) }}</span>
        <div class="task-text">
          <span class="task-label">{{ task.label }}</span>
          <span v-if="task.detail" class="task-detail">{{ task.detail }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { DownOutlined } from '@ant-design/icons-vue'
import type { WorkflowTask } from '@/types/codegen'

defineProps<{
  tasks: WorkflowTask[]
}>()

const expanded = ref(true)

function taskIcon(type: string): string {
  const map: Record<string, string> = {
    skill_call: '⚡',
    command: '⌘',
    save_file: '📄',
    read_file: '📖',
  }
  return map[type] ?? '•'
}
</script>

<style scoped>
.task-list {
  margin-bottom: 12px;
  border-radius: 10px;
  background: #f7f8fa;
  border: 1px solid #eceef2;
}

.task-header {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: #555;
}

.task-count {
  font-weight: 500;
}

.chevron {
  font-size: 12px;
  color: #999;
  transition: transform 0.2s;
}

.chevron.expanded {
  transform: rotate(180deg);
}

.task-body {
  padding: 0 14px 12px 14px;
}

.task-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 6px 0;
  border-top: 1px solid #eceef2;
}

.task-item:first-child {
  border-top: none;
}

.task-icon {
  font-size: 14px;
  margin-top: 2px;
}

.task-text {
  flex: 1;
}

.task-label {
  display: block;
  font-size: 13px;
  color: #333;
}

.task-detail {
  display: block;
  font-size: 12px;
  color: #999;
  margin-top: 2px;
  font-family: 'Consolas', monospace;
}
</style>

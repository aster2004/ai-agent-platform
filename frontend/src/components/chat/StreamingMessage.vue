<template>
  <div class="streaming-msg">
    <ThinkingBlock
      :running="true"
      :active-step="'generate'"
      :step-descriptions="{ generate: '正在流式生成代码...' }"
    />
    <CodeBlock
      v-if="content"
      :content="content"
      filename="generating"
      lang="html"
    />
    <div v-else class="placeholder">正在生成代码...</div>
  </div>
</template>

<script setup lang="ts">
import ThinkingBlock from './ThinkingBlock.vue'
import CodeBlock from './CodeBlock.vue'

defineProps<{
  content: string
}>()
</script>

<style scoped>
.streaming-msg {
  margin: 16px 0;
  max-width: 100%;
}

.placeholder {
  font-size: 13px;
  color: #888;
  padding: 8px 0;
}
</style>

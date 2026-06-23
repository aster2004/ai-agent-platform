<template>
  <div class="msg-box" :class="{ user: msg.messageType === 'user' }">
    <div class="msg-content" v-html="formatContent(msg.content)"></div>
  </div>
</template>

<script setup lang="ts">
import type { ChatMessage as ChatMessageItem } from '@/types/chat'
defineProps<{ msg: ChatMessageItem }>();

const formatContent = (text: string) => {
  // 核心：把接口返回的换行符 \n 转换成HTML可见换行
  const breakText = text.replaceAll('\n', '<br>')

  if (text.includes('<html>') || text.includes('<body>')) {
    return `<pre style="background:#f0f0f0;padding:6px;border-radius:6px;white-space:pre-wrap;word-break:break-all;">${breakText}</pre>`
  }
  return breakText
}
</script>

<style scoped>
/* AI回复消息（左侧）最大宽度 */
.msg-box {
  margin: 12px 0;
  max-width: 70%;
}

/* 用户提问消息（右侧）单独设置最大宽度 */
.msg-box.user {
  max-width: 40%;
  margin-left: auto;
}

.msg-content {
  padding: 8px 14px;
  border-radius: 12px;
  background: #f5f5f5;
  /* 强制长文本自动换行，不横向撑出盒子 */
  word-wrap: break-word;
  word-break: break-all;
}
.user .msg-content {
  background: #1677ff;
  color: white;
}

</style>
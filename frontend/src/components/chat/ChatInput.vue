<template>
  <!-- 不再根据isHome切换样式，统一使用卡片布局 -->
  <div class="input-area home-input">
    <textarea
        v-model="text"
        @keydown.enter.prevent="handleSend"
        :placeholder="isHome ? '输入你的需求，直接开始对话...' : '说说你的想法...'"
    ></textarea>

    <!-- 内置圆形箭头发送按钮 -->
    <button class="send-btn-inner" :class="{ active: text.trim() }" @click="handleSend">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="12" y1="19" x2="12" y2="5"></line>
        <polyline points="5 12 12 5 19 12"></polyline>
      </svg>
    </button>

    <!-- 底部模式选择按钮栏 -->
    <div class="mode-buttons">
      <button
          class="mode-btn"
          :class="{ active: runMode === 'fast' }"
          @click="runMode = 'fast'"
      >
        快速生成
      </button>
      <button
          class="mode-btn"
          :class="{ active: runMode === 'deep' }"
          @click="runMode = 'deep'"
      >
        深度工作流
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, defineEmits, defineProps } from 'vue'

const { isHome } = defineProps({
  isHome: {
    type: Boolean,
    default: false
  }
})

const text = ref('')
const runMode = ref<'fast' | 'deep'>('fast')
const emit = defineEmits(['send'])

const handleSend = (e?: Event) => {
  if (e) e.preventDefault()
  const val = text.value.trim()
  if (!val) return
  emit('send', {
    content: val,
    mode: runMode.value
  })
  text.value = ''
}

function setText(val: string) {
  text.value = val
}

function setMode(mode: 'fast' | 'deep') {
  runMode.value = mode
}

function getMode() {
  return runMode.value
}

defineExpose({ setText, setMode, getMode })
</script>

<style scoped>
/* 全局统一使用首页卡片样式，删除底部窄输入框样式 */
.input-area {
  width: 720px;
  padding: 24px;
  gap: 0;
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid #e1dfff;
  box-shadow: 0 4px 20px rgba(174, 167, 255, 0.08);
  position: relative;
  display: flex;
  flex-direction: column;
}

textarea {
  flex: 1;
  height: 180px;
  border: none;
  padding: 16px 70px 16px 16px;
  font-size: 15px;
  background: transparent;
  resize: none;
  outline: none;
}
textarea:focus {
  border: none;
}

/* 圆形发送按钮 */
.send-btn-inner {
  position: absolute;
  right: 24px;
  bottom: 70px;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  background: #e8ebf0;
  color: #9499a5;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}
.send-btn-inner.active {
  background: #1677ff;
  color: #ffffff;
}

/* 模式按钮容器，统一卡片内边距 */
.mode-buttons {
  display: flex;
  gap: 10px;
  padding: 4px 0 0 16px;
  position: relative;
  z-index: 10;
}
.mode-btn {
  padding: 5px 14px;
  border-radius: 16px;
  border: 1px solid #dcdfe6;
  background: #f5f7fa;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.mode-btn.active {
  background: #1677ff;
  color: #fff;
  border-color: #1677ff;
}

/* 废弃原来的home-input差异化样式，直接统一 */
.home-input {
  /* 样式已经合并到 .input-area，这里清空防止冲突 */
}
</style>
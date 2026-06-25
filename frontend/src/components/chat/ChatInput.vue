<template>
  <!-- 不再根据isHome切换样式，统一使用卡片布局 -->
  <div class="input-area home-input" :class="{ compact }">
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
          @click="selectDeepMode"
      >
        深度分析
      </button>
      <template v-if="runMode === 'fast'">
        <span class="mode-divider" />
        <button
            class="mode-btn"
            :class="{ active: outputMode === 'stream' }"
            @click="outputMode = 'stream'"
        >
          流式输出
        </button>
        <button
            class="mode-btn"
            :class="{ active: outputMode === 'sync' }"
            @click="outputMode = 'sync'"
        >
          同步输出
        </button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { GenerationOutput } from '@/types/codegen'

const props = defineProps({
  isHome: {
    type: Boolean,
    default: false,
  },
  compact: {
    type: Boolean,
    default: false,
  },
})

const { isHome, compact } = props

const text = ref('')
const runMode = ref<'fast' | 'deep'>('fast')
const outputMode = ref<GenerationOutput>('stream')
const emit = defineEmits(['send'])

function selectDeepMode() {
  runMode.value = 'deep'
}

function resolveOutput(): GenerationOutput {
  return runMode.value === 'deep' ? 'stream' : outputMode.value
}

const handleSend = (e?: Event) => {
  if (e) e.preventDefault()
  const val = text.value.trim()
  if (!val) return
  emit('send', {
    content: val,
    mode: runMode.value,
    output: resolveOutput(),
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

function getOutput() {
  return resolveOutput()
}

defineExpose({ setText, setMode, getMode, getOutput })
</script>

<style scoped>
/* 全局统一使用首页卡片样式，删除底部窄输入框样式 */
.input-area {
  width: 720px;
  max-width: 100%;
  padding: 24px;
  gap: 0;
  background: #ffffff;
  border-radius: 20px;
  border: 1px solid #e1dfff;
  box-shadow: 0 4px 20px rgba(174, 167, 255, 0.08);
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.input-area.compact {
  width: 100%;
  padding: 12px 14px 10px;
  border-radius: 14px;
  box-shadow: 0 2px 10px rgba(174, 167, 255, 0.06);
}

.input-area.compact textarea {
  height: 88px;
  padding: 10px 52px 10px 10px;
  font-size: 14px;
}

.input-area.compact .send-btn-inner {
  right: 14px;
  bottom: 52px;
  width: 36px;
  height: 36px;
}

.input-area.compact .mode-buttons {
  gap: 6px;
  padding: 4px 0 0 4px;
  flex-wrap: wrap;
}

.input-area.compact .mode-btn {
  padding: 4px 10px;
  font-size: 12px;
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

.mode-divider {
  width: 1px;
  height: 20px;
  background: #e5e7eb;
  margin: 0 2px;
  align-self: center;
}

/* 废弃原来的home-input差异化样式，直接统一 */
.home-input {
  /* 样式已经合并到 .input-area，这里清空防止冲突 */
}
</style>
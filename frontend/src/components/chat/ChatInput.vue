<template>
  <div class="input-area home-input" :class="{ compact }">
    <!-- 引用代码显示条（豆包风格） -->
    <div v-if="quotedCode" class="quoted-code-bar">
      <div class="quoted-code-info">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M17 1l4 0l0 4" /><path d="M3 11V9a4 4 0 0 1 4-4h14" /><path d="M7 23l-4 0l0-4" /><path d="M21 13v2a4 4 0 0 1-4 4H3" />
        </svg>
        <span class="quoted-code-label">已引用代码</span>
      </div>
      <button class="quoted-code-close" @click="quotedCode = ''" title="取消引用">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      </button>
    </div>
    <textarea
        v-model="text"
        @compositionstart="isComposing = true"
        @compositionend="isComposing = false"
        @keydown.enter="onEnterKey"
        :placeholder="isHome ? '输入你的需求，直接开始对话...' : '说说你的想法...'"
    ></textarea>

    <!-- 圆形发送按钮 -->
    <button class="send-btn-inner" :class="{ active: text.trim() }" @click="handleSend">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="12" y1="19" x2="12" y2="5"></line>
        <polyline points="5 12 12 5 19 12"></polyline>
      </svg>
    </button>

    <!-- 底部模式选择按钮 -->
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

      <!-- 输出格式切换（仅快速生成） -->
      <template v-if="runMode === 'fast'">
      <div class="output-trigger" @click="toggleFormatMenu" ref="formatTriggerRef">
        <span class="output-current">{{ displayFormatType }}</span>
        <svg
            class="output-chevron"
            :class="{ open: formatMenuOpen }"
            width="10" height="6" viewBox="0 0 10 6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
        >
          <polyline points="1 5 5 1 9 5" />
        </svg>

        <!-- 弹出菜单（向上展开） -->
        <Transition name="fade">
          <div v-if="formatMenuOpen" class="output-menu" @click.stop>
            <div
                v-for="opt in formatOptions"
                :key="opt.value"
                class="output-option"
                :class="{ active: formatType === opt.value }"
                @click="selectFormat(opt.value)"
            >
              <span>{{ opt.label }}</span>
              <span class="opt-desc">{{ opt.desc }}</span>
            </div>
          </div>
        </Transition>
      </div>
      </template>
      <span v-else class="deep-mode-hint">AI 自动识别代码类型</span>

      <!-- 输出方式切换（仅快速生成） -->
      <template v-if="runMode === 'fast'">
      <div class="output-trigger" @click="toggleOutputMenu" ref="outputTriggerRef">
        <span class="output-current">{{ outputMode === 'stream' ? '流式' : '同步' }}</span>
        <svg
            class="output-chevron"
            :class="{ open: outputMenuOpen }"
            width="10" height="6" viewBox="0 0 10 6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
        >
          <polyline points="1 5 5 1 9 5" />
        </svg>

        <!-- 弹出菜单（向上展开） -->
        <Transition name="fade">
          <div v-if="outputMenuOpen" class="output-menu" @click.stop>
            <div
                class="output-option"
                :class="{ active: outputMode === 'stream' }"
                @click="selectOutput('stream')"
            >
              <span>流式输出</span>
              <span class="opt-desc">逐字实时显示</span>
            </div>
            <div
                class="output-option"
                :class="{ active: outputMode === 'sync' }"
                @click="selectOutput('sync')"
            >
              <span>同步输出</span>
              <span class="opt-desc">完整一次性返回</span>
            </div>
          </div>
        </Transition>
      </div>
      </template>

      <!-- 模型选择（仅快速生成） -->
      <template v-if="runMode === 'fast'">
      <div class="output-trigger" @click="toggleModelMenu" ref="modelTriggerRef">
        <span class="output-current">{{ modelType }}</span>
        <svg
            class="output-chevron"
            :class="{ open: modelMenuOpen }"
            width="10" height="6" viewBox="0 0 10 6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"
        >
          <polyline points="1 5 5 1 9 5" />
        </svg>
        <Transition name="fade">
          <div v-if="modelMenuOpen" class="output-menu" @click.stop>
            <div
                v-for="opt in modelOptions"
                :key="opt.value"
                class="output-option"
                :class="{ active: modelType === opt.value }"
                @click="selectModel(opt.value)"
            >
              <span>{{ opt.label }}</span>
              <span class="opt-desc">{{ opt.desc }}</span>
            </div>
          </div>
        </Transition>
      </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { formatGenerateTypeLabel } from '@/utils/formatCode'

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

const text = ref('')
const isComposing = ref(false)
const quotedCode = ref('')
const runMode = ref<'fast' | 'deep'>('fast')
const outputMode = ref<'stream' | 'sync'>('stream')
const outputMenuOpen = ref(false)
const outputTriggerRef = ref<HTMLElement | null>(null)
const formatType = ref('GENERAL')
const formatMenuOpen = ref(false)
const formatTriggerRef = ref<HTMLElement | null>(null)
const formatOptions = [
  { value: 'HTML', label: 'HTML', desc: '单页HTML应用' },
  { value: 'VUE', label: 'Vue', desc: 'Vue 3 工程项目' },
  { value: 'MULTI_FILE', label: '多文件', desc: 'HTML多文件项目' },
  { value: 'GENERAL', label: '通用', desc: 'AI 自动选择格式' },
]
const modelType = ref('deepseek')
const modelMenuOpen = ref(false)
const modelTriggerRef = ref<HTMLElement | null>(null)
const modelOptions = [
  { value: 'deepseek', label: 'DeepSeek', desc: 'DeepSeek V3' },
  { value: 'qwen', label: '通义千问', desc: 'Qwen 3' },
  { value: 'glm', label: '智谱GLM', desc: 'GLM-4' },
]
const displayFormatType = computed(() => formatGenerateTypeLabel(formatType.value))
const emit = defineEmits(['send'])

function selectDeepMode() {
  runMode.value = 'deep'
}

function toggleOutputMenu() {
  outputMenuOpen.value = !outputMenuOpen.value
  if (outputMenuOpen.value) formatMenuOpen.value = false
}

function selectOutput(mode: 'stream' | 'sync') {
  outputMode.value = mode
  outputMenuOpen.value = false
}

function toggleFormatMenu() {
  formatMenuOpen.value = !formatMenuOpen.value
  if (formatMenuOpen.value) outputMenuOpen.value = false
}

function selectFormat(type: string) {
  formatType.value = type
  formatMenuOpen.value = false
}

function toggleModelMenu() {
  modelMenuOpen.value = !modelMenuOpen.value
  if (modelMenuOpen.value) {
    outputMenuOpen.value = false
    formatMenuOpen.value = false
  }
}

function selectModel(model: string) {
  modelType.value = model
  modelMenuOpen.value = false
}

function handleClickOutside(e: MouseEvent) {
  if (outputTriggerRef.value && !outputTriggerRef.value.contains(e.target as Node)) {
    outputMenuOpen.value = false
  }
  if (formatTriggerRef.value && !formatTriggerRef.value.contains(e.target as Node)) {
    formatMenuOpen.value = false
  }
  if (modelTriggerRef.value && !modelTriggerRef.value.contains(e.target as Node)) {
    modelMenuOpen.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))

/** Enter 发送；Shift+Enter 换行；中文输入法选词时的 Enter 不发送 */
function onEnterKey(e: KeyboardEvent) {
  if (isComposing.value || e.isComposing) return
  if (e.shiftKey) return
  e.preventDefault()
  handleSend(e)
}

const handleSend = (e?: Event) => {
  if (e) e.preventDefault()
  const val = text.value.trim()
  if (!val) return
  // 「通用」模式前端映射为 MULTI_FILE — 约束最少，AI 可自由输出任意文件组合
  const actualFormat = formatType.value === 'GENERAL' ? 'MULTI_FILE' : formatType.value
  const code = quotedCode.value
  emit('send', {
    content: val,
    mode: runMode.value,
    output: outputMode.value,
    format: actualFormat,
    model: modelType.value,
    quotedCode: code || undefined,
  })
  text.value = ''
  quotedCode.value = ''
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
  return outputMode.value
}

function getFormat() {
  return formatType.value
}

function setQuotedCode(code: string) {
  quotedCode.value = code
}

function getModel() {
  return modelType.value
}

defineExpose({ setText, setMode, getMode, getOutput, getFormat, getModel, setQuotedCode })
</script>

<style scoped>
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

.input-area.compact .output-trigger {
  padding: 4px 10px;
  font-size: 12px;
  line-height: 1.4;
  border-radius: 12px;
}

.input-area.compact .output-current {
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

/* ====== 输出方式切换按钮 ====== */
.output-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 14px;
  border-radius: 16px;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  cursor: pointer;
  user-select: none;
  transition: all 0.2s;
  font-size: 13px;
  line-height: 1.4;
}

.output-trigger:hover {
  background: #eceef2;
  border-color: #d0d3d7;
}

.output-current {
  font-size: 13px;
  color: #666;
  font-weight: 500;
  line-height: 1.4;
}

.output-chevron {
  color: #999;
  transition: transform 0.2s;
  flex-shrink: 0;
}

.output-chevron.open {
  transform: rotate(180deg);
}

/* ====== 弹出菜单（向上展开） ====== */
.output-menu {
  position: absolute;
  bottom: calc(100% + 6px);
  right: 0;
  width: 170px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.10), 0 -2px 6px rgba(0, 0, 0, 0.05);
  padding: 6px;
  z-index: 20;
}

.output-option {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.12s;
  font-size: 13px;
  font-weight: 500;
  color: #333;
}

.output-option:hover {
  background: #f5f7fa;
}

.output-option.active {
  background: #e8eeff;
  color: #1677ff;
}

.opt-desc {
  font-size: 11px;
  font-weight: 400;
  color: #999;
}

.output-option.active .opt-desc {
  color: #7ba5f7;
}

/* ====== 过渡动画 ====== */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s, transform 0.15s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

/* ====== 发送按钮 ====== */
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

/* ====== 模式按钮 ====== */
.mode-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
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

.home-input {
  /* 占位 */
}

.deep-mode-hint {
  padding: 5px 12px;
  border-radius: 16px;
  background: #f0f5ff;
  border: 1px solid #d6e4ff;
  color: #1677ff;
  font-size: 12px;
}

/* ====== 引用代码条（豆包风格） ====== */
.quoted-code-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  margin-bottom: 8px;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 12px;
}

.quoted-code-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #1677ff;
  overflow: hidden;
}

.quoted-code-label {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 280px;
}

.quoted-code-close {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  transition: all 0.15s;
}

.quoted-code-close:hover {
  background: #e5e7eb;
  color: #555;
}
</style>
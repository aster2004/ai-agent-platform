<template>
  <div class="preview-overlay" :class="{ embedded }">
    <div class="preview-panel">
      <div v-if="!hideHeader" class="preview-header">
        <div class="preview-title">
          <GlobalOutlined />
          <span>{{ title }}</span>
        </div>
        <div class="preview-actions">
          <a-button size="small" @click="refreshPreview(true)" :loading="loading">刷新</a-button>
          <a-button size="small" type="primary" @click="openInNewTab">新窗口打开</a-button>
          <button v-if="!embedded" class="close-btn" @click="$emit('close')">×</button>
        </div>
      </div>

      <div class="preview-toolbar">
        <div class="device-tabs">
          <button
            v-for="device in devices"
            :key="device.key"
            class="device-tab"
            :class="{ active: deviceKey === device.key }"
            :title="device.label"
            @click="deviceKey = device.key"
          >
            <component :is="device.icon" />
            <span>{{ device.short }}</span>
          </button>
        </div>
        <div class="toolbar-right">
          <span class="status-dot" :class="statusClass" />
          <span class="status-text">{{ statusText }}</span>
          <a-button
            v-if="consoleEntries.length"
            size="small"
            type="text"
            @click="consoleOpen = !consoleOpen"
          >
            {{ consoleOpen ? '隐藏日志' : `日志(${consoleEntries.length})` }}
          </a-button>
        </div>
      </div>

      <div v-if="previewError" class="preview-alert preview-alert-error">
        <WarningOutlined />
        <div class="alert-body">
          <strong>预览运行异常</strong>
          <span>{{ previewError }}</span>
        </div>
        <a-button size="small" @click="refreshPreview(true)">重试</a-button>
      </div>

      <div v-else-if="previewWarning" class="preview-alert preview-alert-warn">
        <InfoCircleOutlined />
        <div class="alert-body">
          <strong>预览可能不完整</strong>
          <span>{{ previewWarning }}</span>
        </div>
        <a-button size="small" @click="refreshPreview(true)">刷新</a-button>
      </div>

      <div class="preview-body">
        <div v-if="loading" class="preview-loading">
          <a-spin />
          <span>正在同步预览...</span>
        </div>
        <div v-else-if="previewUrl" class="preview-stage" :class="`device-${deviceKey}`">
          <div class="device-frame">
            <iframe
              :key="iframeKey"
              :src="previewUrl"
              class="preview-frame"
              sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
              @load="onIframeLoad"
            />
          </div>
        </div>
        <a-empty v-else description="暂无预览内容" />
      </div>

      <div v-if="consoleOpen && consoleEntries.length" class="preview-console">
        <div class="console-header">预览日志</div>
        <div class="console-body">
          <div
            v-for="entry in consoleEntries"
            :key="entry.id"
            class="console-line"
            :class="`level-${entry.level}`"
          >
            <span class="console-time">{{ entry.time }}</span>
            <span class="console-msg">{{ entry.message }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  GlobalOutlined,
  InfoCircleOutlined,
  MobileOutlined,
  TabletOutlined,
  WarningOutlined,
  DesktopOutlined,
} from '@ant-design/icons-vue'
import type { CodeFile } from '@/types/codegen'
import { syncFilesAndGetPreviewUrl } from '@/utils/previewApp'
import {
  isPreviewBridgeMessage,
  type PreviewConsoleEntry,
} from '@/utils/previewBridge'

const props = withDefaults(defineProps<{
  appId: number
  files: CodeFile[]
  title?: string
  embedded?: boolean
  autoLoad?: boolean
  hideHeader?: boolean
}>(), {
  title: '应用预览',
  embedded: false,
  autoLoad: true,
  hideHeader: false,
})

defineEmits<{
  close: []
}>()

type DeviceKey = 'desktop' | 'tablet' | 'mobile'
type PreviewStatus = 'idle' | 'loading' | 'ready' | 'warn' | 'error'

const devices = [
  { key: 'desktop' as const, label: '桌面', short: '桌面', icon: DesktopOutlined },
  { key: 'tablet' as const, label: '平板 768px', short: '平板', icon: TabletOutlined },
  { key: 'mobile' as const, label: '手机 375px', short: '手机', icon: MobileOutlined },
]

const previewUrl = ref('')
const loading = ref(false)
const iframeKey = ref(0)
const deviceKey = ref<DeviceKey>('desktop')
const previewStatus = ref<PreviewStatus>('idle')
const previewError = ref('')
const previewWarning = ref('')
const consoleEntries = ref<PreviewConsoleEntry[]>([])
const consoleOpen = ref(false)
let consoleSeq = 0
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let loadTimeout: ReturnType<typeof setTimeout> | null = null

const statusText = computed(() => {
  switch (previewStatus.value) {
    case 'loading': return '加载中'
    case 'ready': return '运行正常'
    case 'warn': return '内容异常'
    case 'error': return '运行失败'
    default: return '等待预览'
  }
})

const statusClass = computed(() => previewStatus.value)

function resetPreviewState() {
  previewError.value = ''
  previewWarning.value = ''
  consoleEntries.value = []
  consoleOpen.value = false
  previewStatus.value = 'loading'
}

function pushConsole(level: PreviewConsoleEntry['level'], msg: string) {
  consoleEntries.value = [
    ...consoleEntries.value.slice(-49),
    {
      id: ++consoleSeq,
      level,
      message: msg,
      time: new Date().toLocaleTimeString(),
    },
  ]
}

function onBridgeMessage(event: MessageEvent) {
  if (!isPreviewBridgeMessage(event.data)) return
  const payload = event.data

  switch (payload.type) {
    case 'preview-error':
      previewStatus.value = 'error'
      previewError.value = payload.message || '未知 JavaScript 错误'
      if (payload.filename) {
        previewError.value += ` (${payload.filename}:${payload.lineno ?? 0})`
      }
      pushConsole('error', previewError.value)
      consoleOpen.value = true
      break
    case 'preview-empty':
      previewStatus.value = 'warn'
      previewWarning.value = payload.message
        || '页面几乎没有可交互内容，可能是 Vue 转换失败或只生成了背景样式'
      pushConsole('warn', previewWarning.value)
      break
    case 'preview-ready':
      previewStatus.value = 'ready'
      previewError.value = ''
      previewWarning.value = ''
      if (loadTimeout) {
        clearTimeout(loadTimeout)
        loadTimeout = null
      }
      break
    case 'preview-console':
      if (payload.level && payload.message) {
        pushConsole(payload.level, payload.message)
        if (payload.level === 'error') {
          previewStatus.value = 'error'
          previewError.value = payload.message
        }
      }
      break
  }
}

function onIframeLoad() {
  if (loadTimeout) clearTimeout(loadTimeout)
  loadTimeout = setTimeout(() => {
    if (previewStatus.value === 'loading') {
      previewStatus.value = 'warn'
      previewWarning.value = '预览页未响应健康检测，请尝试刷新或检查代码'
    }
  }, 3000)
}

async function loadPreview(force = false) {
  const files = props.files ?? []
  if (!files.length) return

  resetPreviewState()
  loading.value = true
  try {
    previewUrl.value = await syncFilesAndGetPreviewUrl(props.appId, files, { force })
    iframeKey.value++
  } catch (e: any) {
    previewUrl.value = ''
    previewStatus.value = 'error'
    previewError.value = e?.message || '预览加载失败'
    message.error(previewError.value)
  } finally {
    loading.value = false
  }
}

async function refreshPreview(force = false) {
  await loadPreview(force)
}

function openInNewTab() {
  if (previewUrl.value) {
    window.open(previewUrl.value, '_blank')
  }
}

function scheduleLoad(force = false) {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    if (props.autoLoad && (props.files?.length ?? 0) > 0) {
      loadPreview(force)
    }
  }, 400)
}

watch(
  () => [props.files ?? [], props.appId, props.autoLoad] as const,
  () => scheduleLoad(false),
  { immediate: true, deep: true },
)

onMounted(() => {
  window.addEventListener('message', onBridgeMessage)
})

onUnmounted(() => {
  window.removeEventListener('message', onBridgeMessage)
  if (debounceTimer) clearTimeout(debounceTimer)
  if (loadTimeout) clearTimeout(loadTimeout)
})

defineExpose({ loadPreview, refreshPreview, openInNewTab })
</script>

<style scoped>
.preview-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 1100;
  display: flex;
  align-items: stretch;
  justify-content: flex-end;
  padding: 16px;
}

.preview-overlay.embedded {
  position: static;
  background: transparent;
  z-index: auto;
  padding: 0;
  flex: 1;
  min-width: 0;
  height: 100%;
}

.preview-panel {
  width: min(52vw, 720px);
  background: #fff;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.12);
}

.embedded .preview-panel {
  width: 100%;
  height: 100%;
  border-radius: 0;
  box-shadow: none;
  border-left: none;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.preview-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #333;
}

.preview-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.close-btn {
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  font-size: 20px;
  color: #999;
  cursor: pointer;
}

.preview-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  background: #fafafa;
}

.device-tabs {
  display: flex;
  gap: 4px;
}

.device-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #666;
  font-size: 12px;
  cursor: pointer;
}

.device-tab.active {
  background: #fff;
  border-color: #d9d9d9;
  color: #1677ff;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #888;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d9d9d9;
}

.status-dot.loading { background: #1677ff; animation: pulse 1.2s infinite; }
.status-dot.ready { background: #52c41a; }
.status-dot.warn { background: #faad14; }
.status-dot.error { background: #ff4d4f; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.preview-alert {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  font-size: 13px;
}

.preview-alert-error {
  background: #fff2f0;
  border-bottom: 1px solid #ffccc7;
  color: #cf1322;
}

.preview-alert-warn {
  background: #fffbe6;
  border-bottom: 1px solid #ffe58f;
  color: #ad6800;
}

.alert-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.alert-body strong {
  font-size: 13px;
}

.alert-body span {
  font-size: 12px;
  opacity: 0.9;
}

.preview-body {
  flex: 1;
  min-height: 320px;
  background: #eef1f6;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.embedded .preview-body {
  min-height: 0;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #1677ff;
}

.preview-stage {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  overflow: auto;
}

.device-frame {
  width: 100%;
  height: 100%;
  min-height: 420px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  transition: width 0.2s ease, max-width 0.2s ease;
}

.preview-stage.device-tablet .device-frame {
  width: 768px;
  max-width: 100%;
  height: 100%;
  min-height: 480px;
}

.preview-stage.device-mobile .device-frame {
  width: 375px;
  max-width: 100%;
  height: 100%;
  min-height: 640px;
  border-radius: 16px;
  border: 6px solid #1f1f1f;
}

.preview-frame {
  width: 100%;
  height: 100%;
  min-height: 420px;
  border: none;
  background: #fff;
  display: block;
}

.preview-console {
  border-top: 1px solid #f0f0f0;
  max-height: 140px;
  display: flex;
  flex-direction: column;
  background: #1e1e1e;
  color: #d4d4d4;
}

.console-header {
  padding: 6px 12px;
  font-size: 12px;
  background: #2d2d2d;
  border-bottom: 1px solid #3c3c3c;
}

.console-body {
  flex: 1;
  overflow: auto;
  padding: 6px 0;
  font-family: Consolas, Monaco, monospace;
  font-size: 11px;
}

.console-line {
  display: flex;
  gap: 8px;
  padding: 2px 12px;
}

.console-time {
  color: #808080;
  flex-shrink: 0;
}

.console-line.level-error .console-msg { color: #f48771; }
.console-line.level-warn .console-msg { color: #dcdcaa; }
.console-line.level-log .console-msg { color: #d4d4d4; }
</style>

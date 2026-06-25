<template>
  <!-- ===== 成员4 快速模式：prompt → 代码 ===== -->
  <a-row :gutter="24">
    <!-- ========== 左侧：配置面板 ========== -->
    <a-col :span="10">
      <a-card title="代码生成配置" class="config-card">
        <!-- 目标应用 -->
        <div class="form-item">
          <label class="form-label">目标应用</label>
          <a-select
            v-model:value="appId"
            placeholder="选择要保存代码的应用"
            :loading="loadingApps"
            allow-clear
            style="width: 100%"
          >
            <a-select-option v-for="app in appList" :key="app.id" :value="app.id!">
              {{ app.appName }}
            </a-select-option>
          </a-select>
        </div>

        <!-- 生成类型 -->
        <div class="form-item">
          <label class="form-label">生成类型</label>
          <a-radio-group v-model:value="generateType" button-style="solid" size="small">
            <a-radio-button value="HTML">
              <CodeOutlined style="margin-right: 2px" />HTML
            </a-radio-button>
            <a-radio-button value="VUE">
              <BlockOutlined style="margin-right: 2px" />Vue
            </a-radio-button>
            <a-radio-button value="MULTI_FILE">
              <FolderOutlined style="margin-right: 2px" />多文件
            </a-radio-button>
          </a-radio-group>
        </div>

        <!-- 模型选择 -->
        <div class="form-item">
          <label class="form-label">AI 模型</label>
          <a-select v-model:value="modelName" style="width: 100%">
            <a-select-option value="deepseek">DeepSeek Coder</a-select-option>
            <a-select-option value="bailian">通义千问 (百炼)</a-select-option>
            <a-select-option value="openai">GPT-4o Mini</a-select-option>
          </a-select>
        </div>

        <!-- 需求描述 -->
        <div class="form-item">
          <label class="form-label">需求描述</label>
          <a-textarea
            v-model:value="prompt"
            placeholder="请描述你的代码生成需求，例如：创建一个 Spring Boot REST API，包含用户 CRUD 功能..."
            :rows="10"
            :maxlength="5000"
            show-count
          />
        </div>

        <!-- 操作按钮 -->
        <div class="actions">
          <a-button type="primary" size="large" :loading="generating" @click="handleStreamGenerate">
            <ThunderboltOutlined />流式生成
          </a-button>
          <a-button size="large" :loading="generating" @click="handleSyncGenerate">
            同步生成
          </a-button>
          <a-button size="large" @click="handleClear">
            <ClearOutlined />清空
          </a-button>
        </div>
      </a-card>

      <!-- 生成信息卡片（完成后显示） -->
      <a-card v-if="genMeta" title="生成信息" class="meta-card" size="small">
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="模型">{{ genMeta.modelName || '-' }}</a-descriptions-item>
          <a-descriptions-item label="类型">{{ genMeta.generateType || '-' }}</a-descriptions-item>
          <a-descriptions-item label="耗时">{{ genMeta.duration ? genMeta.duration + 'ms' : '-' }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="genMeta.generateStatus === 1 ? 'green' : 'red'">
              {{ genMeta.generateStatus === 1 ? '成功' : '失败' }}
            </a-tag>
          </a-descriptions-item>
        </a-descriptions>
        <div style="margin-top: 12px">
          <a-button type="primary" size="small" @click="handleSaveToApp" :disabled="!appId || !result">
            <SaveOutlined />保存到应用
          </a-button>
          <a-button size="small" style="margin-left: 8px" @click="copyResult">
            <CopyOutlined />复制代码
          </a-button>
        </div>
      </a-card>
    </a-col>

    <!-- ========== 右侧：结果面板 ========== -->
    <a-col :span="14">
      <a-card class="result-card" :bodyStyle="{ padding: '0' }">
        <a-tabs v-model:activeKey="activeTab" class="result-tabs" @change="onTabChange">
          <!-- Tab 1: 生成结果 -->
          <a-tab-pane key="result" tab="生成结果">
            <div class="result-area">
              <!-- 流式生成中提示 -->
              <a-alert
                v-if="generating"
                type="info"
                show-icon
                message="正在生成中..."
                banner
                style="margin-bottom: 12px"
              >
                <template #icon><LoadingOutlined spin /></template>
              </a-alert>

              <!-- 错误提示 -->
              <a-alert
                v-if="errorMsg && !generating"
                type="error"
                show-icon
                :message="errorMsg"
                closable
                @close="errorMsg = ''"
                style="margin-bottom: 12px"
              />

              <!-- 多文件 Tab 展示 -->
              <template v-if="parsedFiles.length > 1">
                <a-tabs v-model:activeKey="activeFileTab" type="card" size="small" class="file-tabs">
                  <a-tab-pane
                    v-for="(file, idx) in parsedFiles"
                    :key="String(idx)"
                    :tab="file.path"
                  >
                    <pre class="code-block"><code>{{ file.content }}</code></pre>
                  </a-tab-pane>
                </a-tabs>
              </template>

              <!-- 单文件展示 -->
              <template v-else>
                <pre v-if="result" class="code-block"><code>{{ result }}</code></pre>
                <a-empty
                  v-else
                  description="生成结果将在这里实时展示"
                  :image="simpleImage"
                />
              </template>
            </div>
          </a-tab-pane>

          <!-- Tab 2: 生成记录 -->
          <a-tab-pane key="history" tab="生成记录">
            <div class="history-area">
              <a-table
                :columns="historyColumns"
                :data-source="historyList"
                :loading="loadingHistory"
                :pagination="historyPagination"
                row-key="id"
                size="small"
                @change="handleHistoryPageChange"
              >
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'prompt'">
                    <a-tooltip :title="record.prompt">
                      <span class="prompt-ellipsis">{{ truncateText(record.prompt, 40) }}</span>
                    </a-tooltip>
                  </template>
                  <template v-if="column.key === 'generateType'">
                    <a-tag :color="typeColor(record.generateType)">{{ record.generateType }}</a-tag>
                  </template>
                  <template v-if="column.key === 'generateStatus'">
                    <a-tag :color="record.generateStatus === 1 ? 'green' : record.generateStatus === 0 ? 'processing' : 'red'">
                      {{ record.generateStatus === 1 ? '成功' : record.generateStatus === 0 ? '生成中' : '失败' }}
                    </a-tag>
                  </template>
                  <template v-if="column.key === 'action'">
                    <a-button type="link" size="small" @click="viewRecord(record)">查看</a-button>
                  </template>
                </template>
              </a-table>
            </div>
          </a-tab-pane>
        </a-tabs>
      </a-card>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Empty, message } from 'ant-design-vue'
import {
  CodeOutlined,
  BlockOutlined,
  FolderOutlined,
  ThunderboltOutlined,
  ClearOutlined,
  SaveOutlined,
  CopyOutlined,
  LoadingOutlined,
} from '@ant-design/icons-vue'
import { generateCode, generateCodeStream, getRecordList } from '@/api/codegen'
import { getAppList, updateAppCode } from '@/api/app'
import { readSseStream } from '@/utils/codegenStream'
import type { CodeGenVO, CodeFile } from '@/types/codegen'
import type { AppVO } from '@/types/app'

// ==================== 表单状态 ====================
const prompt = ref('')
const appId = ref<number | undefined>(undefined)
const generateType = ref<'HTML' | 'VUE' | 'MULTI_FILE'>('HTML')
const modelName = ref('deepseek')
const generating = ref(false)

// ==================== 结果状态 ====================
const result = ref('')
const errorMsg = ref('')
const genMeta = ref<CodeGenVO | null>(null)
const activeTab = ref('result')
const activeFileTab = ref('0')

// ==================== 应用列表 ====================
const appList = ref<AppVO[]>([])
const loadingApps = ref(false)

// ==================== 多文件解析 ====================
interface ParsedFile {
  path: string
  content: string
}

const parsedFiles = computed<ParsedFile[]>(() => {
  if (!result.value) return []
  try {
    const trimmed = result.value.trim()
    if (trimmed.startsWith('[')) {
      const parsed = JSON.parse(trimmed) as CodeFile[]
      if (Array.isArray(parsed) && parsed.length > 0 && parsed[0].path) {
        return parsed.map(f => ({ path: f.path, content: f.content || '' }))
      }
    }
  } catch {
    // Not JSON, return as single file
  }
  return []
})

// ==================== 生成记录 ====================
const historyList = ref<CodeGenVO[]>([])
const loadingHistory = ref(false)
const historyPagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

const historyColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '需求', dataIndex: 'prompt', key: 'prompt', ellipsis: true },
  { title: '类型', dataIndex: 'generateType', key: 'generateType', width: 90 },
  { title: '模型', dataIndex: 'modelName', key: 'modelName', width: 100 },
  { title: '状态', dataIndex: 'generateStatus', key: 'generateStatus', width: 80 },
  { title: '时间', dataIndex: 'createTime', key: 'createTime', width: 160 },
  { title: '操作', key: 'action', width: 60 },
]

// ==================== 空状态简化图片 ====================
const simpleImage = Empty.PRESENTED_IMAGE_SIMPLE

// ==================== 初始化：加载应用列表 ====================
onMounted(() => {
  fetchApps()
})

async function fetchApps() {
  loadingApps.value = true
  try {
    const res = await getAppList(0, 50)
    appList.value = res.data?.content ?? []
  } catch {
    // 忽略加载失败
  } finally {
    loadingApps.value = false
  }
}

// ==================== 流式生成 ====================
async function handleStreamGenerate() {
  if (!validatePrompt()) return
  startGeneration()
  try {
    const response = await generateCodeStream({
      prompt: prompt.value,
      appId: appId.value,
      generateType: generateType.value,
      modelName: modelName.value,
    })
    if (!response.ok) {
      const errData = await response.json().catch(() => ({}))
      throw new Error(errData.message || `HTTP ${response.status}`)
    }
    await readSseStream(response, handleSseEvent)
  } catch (e: any) {
    if (!result.value) {
      errorMsg.value = e.message || '流式生成失败'
    }
    finishGeneration(undefined, e.message || '流式生成失败')
  }
}

// ==================== 同步生成 ====================
async function handleSyncGenerate() {
  if (!validatePrompt()) return
  startGeneration()
  try {
    const res = await generateCode({
      prompt: prompt.value,
      appId: appId.value,
      generateType: generateType.value,
      modelName: modelName.value,
    })
    const vo = res.data!
    result.value = vo.codeContent || ''
    genMeta.value = vo
    if (vo.generateStatus === 2) {
      errorMsg.value = vo.errorMsg || '生成失败'
    }
    message.success('代码生成完成')
  } catch (e: any) {
    errorMsg.value = e.message || '同步生成失败'
  } finally {
    generating.value = false
  }
}

// ==================== SSE 事件处理 ====================
function handleSseEvent(eventName: string, data: string) {
  switch (eventName) {
    case 'code_chunk':
      result.value += data
      break
    case 'finish':
      finishGeneration(data)
      message.success('代码生成完成')
      break
    case 'error':
      errorMsg.value = data
      finishGeneration(undefined, data)
      break
  }
}

// ==================== 辅助方法 ====================
function validatePrompt(): boolean {
  if (!prompt.value.trim()) {
    message.warning('请输入需求描述')
    return false
  }
  return true
}

function startGeneration() {
  generating.value = true
  result.value = ''
  errorMsg.value = ''
  genMeta.value = null
  activeTab.value = 'result'
  activeFileTab.value = '0'
}

function finishGeneration(_data?: string, error?: string) {
  generating.value = false
  genMeta.value = {
    modelName: modelName.value,
    generateType: generateType.value,
    generateStatus: error ? 2 : 1,
    errorMsg: error,
    codeContent: result.value,
  }
}

async function handleSaveToApp() {
  if (!appId.value || !result.value) {
    message.warning('请先选择目标应用')
    return
  }
  try {
    await updateAppCode(appId.value, { codeContent: result.value })
    message.success('代码已保存到应用')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  }
}

async function copyResult() {
  try {
    await navigator.clipboard.writeText(result.value)
    message.success('已复制到剪贴板')
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = result.value
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    message.success('已复制到剪贴板')
  }
}

function handleClear() {
  prompt.value = ''
  result.value = ''
  errorMsg.value = ''
  genMeta.value = null
  activeFileTab.value = '0'
}

// ==================== 生成记录 ====================
async function fetchHistory(pageNum = 1, pageSize = 10) {
  loadingHistory.value = true
  try {
    const res = await getRecordList(pageNum, pageSize)
    historyList.value = res.data?.list ?? []
    historyPagination.total = res.data?.total ?? 0
    historyPagination.current = res.data?.pageNum ?? pageNum
    historyPagination.pageSize = res.data?.pageSize ?? pageSize
  } catch {
    // 忽略
  } finally {
    loadingHistory.value = false
  }
}

function handleHistoryPageChange(pag: { current: number; pageSize: number }) {
  fetchHistory(pag.current, pag.pageSize)
}

function viewRecord(record: CodeGenVO) {
  result.value = record.codeContent || ''
  genMeta.value = record
  errorMsg.value = record.errorMsg || ''
  activeTab.value = 'result'
  activeFileTab.value = '0'
}

// ==================== 工具函数 ====================
function truncateText(text?: string, max = 40): string {
  if (!text) return '-'
  return text.length > max ? text.slice(0, max) + '…' : text
}

function typeColor(type?: string): string {
  switch (type) {
    case 'HTML': return 'blue'
    case 'VUE': return 'green'
    case 'MULTI_FILE': return 'orange'
    default: return 'default'
  }
}

// 监听 tab 切换，切到记录时自动加载
function onTabChange(key: string) {
  if (key === 'history' && historyList.value.length === 0) {
    fetchHistory()
  }
}
</script>

<style scoped>
.config-card,
.result-card {
  height: 100%;
}

.form-item {
  margin-bottom: 16px;
}

.form-label {
  display: block;
  margin-bottom: 6px;
  font-weight: 500;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.85);
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.meta-card {
  margin-top: 16px;
}

.result-area {
  padding: 16px;
  min-height: 450px;
  max-height: 650px;
  overflow: auto;
}

.code-block {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 16px;
  border-radius: 6px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-x: auto;
  margin: 0;
}

.file-tabs {
  margin-bottom: 0;
}

.file-tabs :deep(.ant-tabs-content) {
  padding: 0;
}

.history-area {
  padding: 16px;
  min-height: 450px;
}

.prompt-ellipsis {
  max-width: 200px;
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
  padding: 0 16px;
}

/* 流式生成中的加载动画 */
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.generating-cursor::after {
  content: '▋';
  animation: blink 1s infinite;
  color: #1677ff;
}
</style>

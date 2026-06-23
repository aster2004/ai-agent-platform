<template>
  <div class="deploy-page">
    <div class="page-header">
      <div>
        <h2 class="title">{{ appName || '应用部署分享' }}</h2>
        <p class="subtitle">{{ appId ? `应用 ID：${appId}` : '请输入应用 ID' }} · 成员3：预览 / 部署 / 下载</p>
      </div>
      <a-button type="link" @click="$router.push('/app')">返回应用管理</a-button>
    </div>

    <div class="toolbar preview-toolbar">
      <span class="toolbar-label">预览</span>
      <a-space wrap>
        <a-input-number
          v-model:value="appId"
          :min="1"
          placeholder="请输入应用 ID"
          style="width: 160px"
        />
        <a-button type="primary" :loading="previewLoading" @click="loadPreview">
          刷新预览
        </a-button>
        <a-button @click="handleDownload">下载源码</a-button>
        <a-button
          :disabled="!previewUrl"
          :loading="coverLoading"
          @click="handleCaptureCover"
        >
          <template #icon><CameraOutlined /></template>
          生成封面
        </a-button>
      </a-space>
    </div>

    <div class="toolbar deploy-toolbar">
      <span class="toolbar-label">部署</span>
      <div class="deploy-panel">
        <a-radio-group v-model:value="deployMode" class="deploy-mode-group">
          <a-radio
            v-for="item in deployModes"
            :key="item.code"
            :value="item.code"
            class="deploy-mode-item"
          >
            <div class="mode-content">
              <span class="mode-label">{{ item.label }}</span>
              <span class="mode-desc">{{ item.description }}</span>
            </div>
          </a-radio>
        </a-radio-group>
        <a-space wrap class="deploy-actions">
          <a-button type="primary" :loading="deployLoading" @click="handleDeploy">
            一键部署
          </a-button>
          <a-button :disabled="!hasDeployed || !deployUrl" @click="copyShareLink">复制分享链接</a-button>
          <a-button v-if="hasDeployed && deployUrl" @click="openDeploySite">打开部署站点</a-button>
        </a-space>
      </div>
    </div>

    <a-alert
      v-if="hasDeployed && deployUrl"
      type="success"
      show-icon
      class="deploy-alert"
    >
      <template #message>
        <span>
          【{{ deployModeLabel || '已部署' }}】部署地址：{{ fullDeployUrl }}
        </span>
      </template>
    </a-alert>

    <div class="content-row">
      <div class="preview-wrapper">
        <div v-if="!hasPreviewed || !previewUrl" class="preview-empty">
          <a-empty description="请输入应用 ID，点击「刷新预览」">
            <template #image>
              <EyeOutlined style="font-size: 48px; color: #1677ff" />
            </template>
          </a-empty>
        </div>
        <iframe
          v-else
          :key="iframePreviewUrl"
          :src="iframePreviewUrl"
          class="preview-frame"
          title="应用预览"
        />
      </div>

      <div v-if="hasPreviewed && previewUrl" class="cover-panel">
        <div class="cover-panel-header">
          <span class="cover-title">应用封面</span>
          <a-tag color="blue">1280 × 720</a-tag>
        </div>
        <p class="cover-desc">自动截取预览页面首屏，写入 app.cover_img，供应用列表展示</p>
        <div class="cover-preview-box">
          <a-image
            v-if="coverImg"
            :src="fullCoverUrl"
            alt="应用封面"
            class="cover-image"
          />
          <div v-else class="cover-placeholder">
            <CameraOutlined style="font-size: 32px; color: #bfbfbf" />
            <span>点击「生成封面」截取预览页</span>
          </div>
        </div>
        <p v-if="coverImg" class="cover-zoom-hint">点击封面可放大查看</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { CameraOutlined, EyeOutlined } from '@ant-design/icons-vue'
import { resolveAssetUrl, toBackendAssetUrl } from '@/utils/assetUrl'
import {
  captureCover,
  deployApp,
  downloadAppSource,
  getAppPreview,
  getDeployModes,
} from '@/api/appDeploy'
import type { DeployModeCode, DeployModeVO } from '@/types/appDeploy'

const route = useRoute()
const appId = ref<number | null>(null)
const appName = ref('')
const previewUrl = ref('')
const deployUrl = ref('')
const coverImg = ref('')
const deployMode = ref<DeployModeCode>('local')
const deployModeLabel = ref('')
const deployModes = ref<DeployModeVO[]>([
  { code: 'local', label: '本地静态目录', description: 'Spring Boot /sites' },
  { code: 'nginx', label: 'Nginx 部署', description: 'Nginx 8888 端口' },
  { code: 'docker', label: 'Docker 部署', description: '独立容器' },
])
const coverVersion = ref(0)
const previewVersion = ref(0)
const hasPreviewed = ref(false)
const hasDeployed = ref(false)
const previewLoading = ref(false)
const deployLoading = ref(false)
const coverLoading = ref(false)

const iframePreviewUrl = computed(() => {
  if (!previewUrl.value) return ''
  const base = toBackendAssetUrl(previewUrl.value)
  return `${base}${base.includes('?') ? '&' : '?'}v=${previewVersion.value}`
})

const fullDeployUrl = computed(() => toBackendAssetUrl(deployUrl.value))

const fullCoverUrl = computed(() => {
  if (!coverImg.value) return ''
  return `${toBackendAssetUrl(coverImg.value)}?v=${coverVersion.value}`
})

/** 仅刷新 iframe 预览，不触发部署 */
async function loadPreview() {
  if (!appId.value) {
    message.warning('请输入应用 ID')
    return
  }
  previewLoading.value = true
  try {
    const res = await getAppPreview(appId.value)
    appName.value = res.data.appName
    previewUrl.value = resolveAssetUrl(res.data.previewUrl)
    coverImg.value = res.data.coverImg ? resolveAssetUrl(res.data.coverImg) : ''
    previewVersion.value = Date.now()
    hasPreviewed.value = true
    message.success('预览已刷新')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '预览失败'
    if (msg === '应用不存在') {
      message.error('应用不存在：请确认 app 表中有该 ID 的记录（可执行 sql/init.sql 或先在应用管理创建）')
    } else {
      message.error(msg)
    }
  } finally {
    previewLoading.value = false
  }
}

async function handleDeploy() {
  if (!appId.value) {
    message.warning('请输入应用 ID')
    return
  }
  deployLoading.value = true
  try {
    const res = await deployApp(appId.value, deployMode.value)
    deployUrl.value = resolveAssetUrl(res.data.deployUrl)
    deployModeLabel.value = res.data.deployModeLabel || ''
    if (res.data.deployMode) {
      deployMode.value = res.data.deployMode as DeployModeCode
    }
    hasDeployed.value = true
    message.success(res.data.message || '部署成功')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '部署失败')
  } finally {
    deployLoading.value = false
  }
}

async function handleCaptureCover() {
  if (!appId.value) {
    message.warning('请输入应用 ID')
    return
  }
  if (!hasPreviewed.value || !previewUrl.value) {
    message.warning('请先刷新预览')
    return
  }
  coverLoading.value = true
  try {
    const res = await captureCover(appId.value)
    coverImg.value = resolveAssetUrl(res.data.coverImg)
    coverVersion.value = Date.now()
    message.success('封面截图已生成，已写入 cover_img')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '封面截图失败')
  } finally {
    coverLoading.value = false
  }
}

/** 切换应用 ID 时清空预览与部署展示，需用户手动点击按钮 */
function resetAppState() {
  previewUrl.value = ''
  deployUrl.value = ''
  deployModeLabel.value = ''
  coverImg.value = ''
  appName.value = ''
  hasPreviewed.value = false
  hasDeployed.value = false
}

watch(appId, () => {
  resetAppState()
})

async function loadDeployModes() {
  try {
    const res = await getDeployModes()
    if (res.data?.length) {
      deployModes.value = res.data
    }
  } catch {
    // 使用默认选项
  }
}

function handleDownload() {
  if (!appId.value) {
    message.warning('请输入应用 ID')
    return
  }
  window.open(downloadAppSource(appId.value), '_blank')
}

async function copyShareLink() {
  if (!hasDeployed.value || !deployUrl.value) {
    message.warning('请先一键部署')
    return
  }
  await navigator.clipboard.writeText(fullDeployUrl.value)
  message.success('分享链接已复制')
}

function openDeploySite() {
  if (!deployUrl.value) return
  window.open(fullDeployUrl.value, '_blank')
}

function syncAppIdFromRoute() {
  const rawId = route.params.id
  if (!rawId) {
    appId.value = null
    return
  }
  const id = Number(rawId)
  appId.value = !Number.isNaN(id) && id > 0 ? id : null
}

onMounted(() => {
  loadDeployModes()
  syncAppIdFromRoute()
})
watch(() => route.fullPath, syncAppIdFromRoute)
</script>

<style scoped>
.deploy-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 600px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.subtitle {
  margin: 4px 0 0;
  color: rgba(0, 0, 0, 0.45);
  font-size: 14px;
}

.toolbar {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 12px;
}

.preview-toolbar {
  border-bottom: 1px solid #f0f0f0;
}

.deploy-toolbar {
  padding-top: 4px;
}

.toolbar-label {
  flex-shrink: 0;
  width: 36px;
  padding-top: 6px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.65);
}

.deploy-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.deploy-mode-group {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.deploy-mode-group :deep(.ant-radio-wrapper) {
  margin-inline-end: 0;
  padding: 10px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  transition: border-color 0.2s, background 0.2s;
}

.deploy-mode-group :deep(.ant-radio-wrapper-checked) {
  border-color: #1677ff;
  background: #f0f7ff;
}

.mode-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-left: 4px;
}

.mode-label {
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
}

.mode-desc {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.deploy-actions {
  padding-left: 2px;
}

.deploy-alert {
  margin-bottom: 0;
}

.content-row {
  display: flex;
  gap: 16px;
  align-items: stretch;
  flex: 1;
}

.preview-wrapper {
  flex: 1;
  min-height: 520px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
}

.preview-frame {
  width: 100%;
  height: 520px;
  border: none;
  background: #fff;
}

.preview-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 520px;
}

.cover-panel {
  width: 300px;
  flex-shrink: 0;
  padding: 16px;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.cover-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.cover-title {
  font-weight: 600;
  font-size: 15px;
}

.cover-desc {
  margin: 0 0 12px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  line-height: 1.5;
}

.cover-preview-box {
  border: 1px dashed #d9d9d9;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
  aspect-ratio: 16 / 9;
}

.cover-preview-box :deep(.ant-image) {
  display: block;
  width: 100%;
  height: 100%;
}

.cover-preview-box :deep(.ant-image-img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: zoom-in;
}

.cover-zoom-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
  text-align: center;
}

.cover-image {
  display: block;
  width: 100%;
  height: 100%;
}

.cover-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 100%;
  min-height: 160px;
  color: rgba(0, 0, 0, 0.35);
  font-size: 12px;
  text-align: center;
  padding: 16px;
}

@media (max-width: 960px) {
  .content-row {
    flex-direction: column;
  }

  .cover-panel {
    width: 100%;
  }
}
</style>

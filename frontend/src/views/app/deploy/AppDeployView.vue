<template>
  <div class="deploy-page">
    <div class="page-header">
      <div>
        <h2 class="title">{{ appName || '应用部署分享' }}</h2>
        <p class="subtitle">{{ appId ? `应用 ID：${appId}` : '请输入应用 ID' }} · 成员3：预览 / 部署 / 下载</p>
      </div>
      <a-button type="link" @click="$router.push('/app')">返回应用管理</a-button>
    </div>

    <div class="toolbar">
      <a-space wrap>
        <a-input-number
          v-model:value="appId"
          :min="1"
          placeholder="应用 ID"
          style="width: 140px"
        />
        <a-button type="primary" :loading="previewLoading" @click="loadPreview">
          刷新预览
        </a-button>
        <a-button type="primary" ghost :loading="deployLoading" @click="handleDeploy">
          一键部署
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
        <a-button :disabled="!deployUrl" @click="copyShareLink">复制分享链接</a-button>
        <a-button v-if="deployUrl" @click="openDeploySite">打开部署站点</a-button>
      </a-space>
    </div>

    <a-alert
      v-if="deployUrl"
      type="success"
      show-icon
      class="deploy-alert"
      :message="`部署地址：${fullDeployUrl}`"
    />

    <div class="content-row">
      <div class="preview-wrapper">
        <div v-if="!previewUrl" class="preview-empty">
          <a-empty description="输入应用 ID 后点击「刷新预览」">
            <template #image>
              <EyeOutlined style="font-size: 48px; color: #1677ff" />
            </template>
          </a-empty>
        </div>
        <iframe
          v-else
          :key="previewUrl"
          :src="previewUrl"
          class="preview-frame"
          title="应用预览"
          sandbox="allow-scripts allow-same-origin allow-forms"
        />
      </div>

      <div v-if="previewUrl" class="cover-panel">
        <div class="cover-panel-header">
          <span class="cover-title">应用封面</span>
          <a-tag color="blue">1280 × 720</a-tag>
        </div>
        <p class="cover-desc">自动截取预览页面首屏，写入 app.cover_img，供应用列表展示</p>
        <div class="cover-preview-box">
          <img
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
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { CameraOutlined, EyeOutlined } from '@ant-design/icons-vue'
import {
  captureCover,
  deployApp,
  downloadAppSource,
  getAppPreview,
  getDeployUrl,
} from '@/api/appDeploy'

const appId = ref<number | null>(null)
const appName = ref('')
const previewUrl = ref('')
const deployUrl = ref('')
const coverImg = ref('')
const coverVersion = ref(0)
const previewLoading = ref(false)
const deployLoading = ref(false)
const coverLoading = ref(false)

const fullDeployUrl = computed(() => {
  if (!deployUrl.value) return ''
  return deployUrl.value.startsWith('http')
    ? deployUrl.value
    : `${window.location.origin}${deployUrl.value}`
})

const fullCoverUrl = computed(() => {
  if (!coverImg.value) return ''
  const base = coverImg.value.startsWith('http')
    ? coverImg.value
    : `${window.location.origin}${coverImg.value}`
  return `${base}?v=${coverVersion.value}`
})

async function loadPreview() {
  if (!appId.value) {
    message.warning('请输入应用 ID')
    return
  }
  previewLoading.value = true
  try {
    const res = await getAppPreview(appId.value)
    appName.value = res.data.appName
    previewUrl.value = res.data.previewUrl
    coverImg.value = res.data.coverImg || ''
    await fetchDeployUrl()
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
    const res = await deployApp(appId.value)
    deployUrl.value = res.data.deployUrl
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
  if (!previewUrl.value) {
    message.warning('请先刷新预览')
    return
  }
  coverLoading.value = true
  try {
    const res = await captureCover(appId.value)
    coverImg.value = res.data.coverImg
    coverVersion.value = Date.now()
    message.success('封面截图已生成，已写入 cover_img')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '封面截图失败')
  } finally {
    coverLoading.value = false
  }
}

async function fetchDeployUrl() {
  if (!appId.value) return
  try {
    const res = await getDeployUrl(appId.value)
    deployUrl.value = res.data || ''
  } catch {
    deployUrl.value = ''
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
  if (!deployUrl.value) {
    message.warning('请先部署应用')
    return
  }
  await navigator.clipboard.writeText(fullDeployUrl.value)
  message.success('分享链接已复制')
}

function openDeploySite() {
  if (!deployUrl.value) return
  window.open(fullDeployUrl.value, '_blank')
}

watch(appId, () => {
  previewUrl.value = ''
  deployUrl.value = ''
  coverImg.value = ''
  appName.value = ''
})
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
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
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

.cover-image {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
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

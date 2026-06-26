<template>
  <div class="app-gallery-page">
    <div class="page-header">
      <h2 class="page-title">精选广场</h2>
      <span class="page-subtitle">发现优质 AI 生成应用</span>
    </div>

    <a-spin :spinning="loading">
      <a-empty v-if="!loading && featuredList.length === 0" description="暂无精选应用" />

      <a-row v-else :gutter="[16, 16]">
        <a-col
          v-for="app in featuredList"
          :key="app.id"
          :xs="24"
          :sm="12"
          :md="8"
          :lg="6"
        >
          <a-card hoverable class="app-card" @click="openAppDeploy(app)">
            <template #cover>
              <AppCardCover :cover-img="app.coverImg" :alt="app.appName" />
            </template>
            <a-card-meta :title="app.appName">
              <template #description>
                <p class="card-desc">{{ app.description || '暂无描述' }}</p>
                <p class="card-meta">
                  <span class="card-creator">{{ resolveCreatorName(app) }}</span>
                  <span class="card-time">{{ formatTime(app.createTime) }}</span>
                </p>
              </template>
            </a-card-meta>
          </a-card>
        </a-col>
      </a-row>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import AppCardCover from '@/components/app/AppCardCover.vue'
import { getFeaturedApps } from '@/api/app'
import type { AppVO } from '@/types/app'

const router = useRouter()
const loading = ref(false)
const featuredList = ref<AppVO[]>([])

function formatTime(time: string) {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

function resolveCreatorName(app: AppVO) {
  return app.creatorName?.trim() || `用户 #${app.userId}`
}

function openAppDeploy(app: AppVO) {
  router.push(`/app/${app.id}/deploy`)
}

async function loadFeatured() {
  loading.value = true
  try {
    const res = await getFeaturedApps()
    featuredList.value = res.data
  } catch (e: any) {
    message.error(e.message || '加载精选应用失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadFeatured()
})
</script>

<style scoped>
.app-gallery-page {
  min-height: 400px;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
}

.page-subtitle {
  color: rgba(0, 0, 0, 0.45);
  font-size: 14px;
}

.app-card {
  height: 100%;
  cursor: pointer;
}

.card-desc {
  margin: 0 0 8px;
  color: rgba(0, 0, 0, 0.65);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 0;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.card-creator {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-time {
  flex-shrink: 0;
}
</style>

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
          <a-card hoverable class="app-card">
            <template #cover>
              <div class="card-cover">
                <img
                  v-if="app.coverImg"
                  :src="app.coverImg"
                  :alt="app.appName"
                  class="cover-img"
                />
                <div v-else class="cover-placeholder">
                  <AppstoreOutlined />
                </div>
              </div>
            </template>
            <a-card-meta :title="app.appName">
              <template #description>
                <p class="card-desc">{{ app.description || '暂无描述' }}</p>
                <p class="card-time">{{ formatTime(app.createTime) }}</p>
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
import { message } from 'ant-design-vue'
import { AppstoreOutlined } from '@ant-design/icons-vue'
import { getFeaturedApps } from '@/api/app'
import type { AppVO } from '@/types/app'

const loading = ref(false)
const featuredList = ref<AppVO[]>([])

function formatTime(time: string) {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
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
}

.card-cover {
  height: 160px;
  overflow: hidden;
  background: #f5f5f5;
}

.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 48px;
  color: #bfbfbf;
}

.card-desc {
  margin: 0 0 8px;
  color: rgba(0, 0, 0, 0.65);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-time {
  margin: 0;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}
</style>

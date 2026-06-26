<template>
  <div class="app-manage-page">
    <div class="page-header">
      <h2 class="page-title">应用管理</h2>
      <a-space>
        <template v-if="isAdmin">
          <span class="filter-label">精选筛选</span>
          <a-select
            v-model:value="featuredFilter"
            style="width: 120px"
            @change="handleFilterChange"
          >
            <a-select-option :value="undefined">全部</a-select-option>
            <a-select-option :value="1">已精选</a-select-option>
            <a-select-option :value="0">未精选</a-select-option>
          </a-select>
        </template>
        <a-button @click="goDeploy()">部署分享</a-button>
      </a-space>
    </div>

    <!-- 管理员：表格布局 -->
    <a-table
      v-if="isAdmin"
      :columns="columns"
      :data-source="appList"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'creatorName'">
          {{ record.creatorName || `用户 #${record.userId}` }}
        </template>
        <template v-else-if="column.key === 'description'">
          {{ record.description || '—' }}
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="record.status === 'normal' ? 'green' : 'default'">
            {{ record.status === 'normal' ? '正常' : record.status === 'offline' ? '下架' : record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'isFeatured'">
          <a-tag :color="record.isFeatured === 1 ? 'gold' : 'default'">
            {{ record.isFeatured === 1 ? '精选' : '普通' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="goDeploy(record.id)">部署分享</a-button>
            <template v-if="canManageApp(record)">
              <a-button type="link" size="small" @click="openEditModal(record)">编辑</a-button>
              <a-popconfirm
                title="确定下架该应用吗？下架后数据仍保留，仅不再显示。"
                ok-text="确定"
                cancel-text="取消"
                @confirm="handleDelete(record.id)"
              >
                <a-button type="link" size="small" danger>下架</a-button>
              </a-popconfirm>
            </template>
            <a-button
              v-if="isAdmin"
              type="link"
              size="small"
              :loading="featuredLoadingId === record.id"
              :disabled="record.status !== 'normal'"
              @click="handleFeaturedToggle(record)"
            >
              {{ record.isFeatured === 1 ? '取消精选' : '设为精选' }}
            </a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 普通用户：卡片布局 -->
    <template v-else>
      <a-spin :spinning="loading">
        <a-empty v-if="!loading && appList.length === 0" description="暂无应用" />

        <a-row v-else :gutter="[16, 16]">
          <a-col
            v-for="app in appList"
            :key="app.id"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
          >
            <div class="app-card-wrapper" @click="openAppChat(app)">
              <a-card hoverable class="app-card">
                <template #cover>
                  <div class="card-cover-wrap">
                    <AppCardCover :cover-img="app.coverImg" :alt="app.appName" />
                    <div class="card-action-overlay" @click.stop>
                      <ul class="action-menu">
                        <li @click.stop="goDeploy(app.id)">部署分享</li>
                        <template v-if="canManageApp(app)">
                          <li @click.stop="openEditModal(app)">编辑</li>
                          <li class="action-danger" @click.stop="confirmDelete(app.id)">下架</li>
                        </template>
                      </ul>
                    </div>
                  </div>
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
            </div>
          </a-col>
        </a-row>
      </a-spin>

      <div v-if="pagination.total > 0" class="card-pagination">
        <a-pagination
          v-model:current="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :show-size-changer="true"
          :show-total="(total: number) => `共 ${total} 条`"
          @change="handleCardPageChange"
          @show-size-change="handleCardPageChange"
        />
      </div>
    </template>

    <a-modal
      v-model:open="modalVisible"
      title="编辑应用"
      :confirm-loading="modalLoading"
      ok-text="确定"
      cancel-text="取消"
      @ok="handleSubmit"
      @cancel="closeModal"
    >
      <a-form layout="vertical">
        <a-form-item label="应用名称" required>
          <a-input
            v-model:value="formState.appName"
            placeholder="请输入应用名称"
            :maxlength="100"
          />
        </a-form-item>
        <a-form-item label="应用描述">
          <a-textarea
            v-model:value="formState.description"
            placeholder="请输入应用描述（可选）"
            :rows="4"
            :maxlength="500"
            show-count
          />
        </a-form-item>
        <a-form-item label="应用封面">
          <div class="cover-upload">
            <a-upload
              list-type="picture-card"
              :show-upload-list="false"
              accept="image/jpeg,image/png,image/webp,image/gif"
              :before-upload="beforeCoverUpload"
              :custom-request="handleCoverUpload"
            >
              <img
                v-if="formState.coverImg"
                :src="resolveCoverUrl(formState.coverImg)"
                alt="封面预览"
                class="cover-preview"
              />
              <div v-else class="cover-upload-placeholder">
                <LoadingOutlined v-if="coverUploading" />
                <PlusOutlined v-else />
                <div class="cover-upload-text">{{ coverUploading ? '上传中' : '上传封面' }}</div>
              </div>
            </a-upload>
            <a-button
              v-if="formState.coverImg"
              type="link"
              size="small"
              danger
              class="cover-clear-btn"
              @click="clearCover"
            >
              清除封面
            </a-button>
          </div>
          <div class="cover-hint">支持 JPG/PNG/WebP/GIF，不超过 2MB，用于精选广场展示</div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import type { TablePaginationConfig, UploadProps } from 'ant-design-vue'
import { LoadingOutlined, PlusOutlined } from '@ant-design/icons-vue'
import AppCardCover from '@/components/app/AppCardCover.vue'
import {
  deleteApp,
  getAdminAppList,
  getAppList,
  getAppSessionId,
  setAppFeatured,
  updateApp,
  uploadAppCover,
} from '@/api/app'
import { getSessionList } from '@/api/chat'
import { useUserStore } from '@/stores/user'
import { resolveCoverUrl } from '@/utils/assetUrl'
import type { AppVO } from '@/types/app'

const router = useRouter()
const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin())

function goDeploy(id?: number) {
  router.push(id ? `/app/${id}/deploy` : '/app/deploy')
}

async function openAppChat(app: AppVO) {
  try {
    const res = await getAppSessionId(app.id)
    if (res.code === 200 && res.data) {
      router.push(`/chat/session/${res.data}`)
      return
    }

    const sessRes = await getSessionList(app.id)
    const sessions = sessRes.data ?? []
    if (sessions.length > 0) {
      router.push(`/chat/session/${sessions[0].id}`)
      return
    }

    const allSessRes = await getSessionList()
    const matched = (allSessRes.data ?? []).find((s) => s.sessionTitle === app.appName)
    if (matched) {
      router.push(`/chat/session/${matched.id}`)
      return
    }

    message.error('未找到该应用对应的对话')
  } catch (e: any) {
    message.error(e.message || '跳转对话失败')
  }
}

const loading = ref(false)
const modalLoading = ref(false)
const modalVisible = ref(false)
const editingId = ref<number | null>(null)
const featuredLoadingId = ref<number | null>(null)
const featuredFilter = ref<number | undefined>(undefined)
const coverUploading = ref(false)
const appList = ref<AppVO[]>([])
let coverPollTimer: ReturnType<typeof setInterval> | null = null

const formState = reactive({
  appName: '',
  description: '',
  coverImg: '',
})

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => `共 ${total} 条`,
})

const columns = computed(() => {
  const base = [
    { title: '应用名称', dataIndex: 'appName', key: 'appName', ellipsis: true },
  ]
  if (isAdmin.value) {
    base.push({ title: '创建者', dataIndex: 'creatorName', key: 'creatorName', width: 120 })
  }
  base.push(
    { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
    { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  )
  if (isAdmin.value) {
    base.push({ title: '精选', dataIndex: 'isFeatured', key: 'isFeatured', width: 90 })
  }
  base.push(
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
    { title: '操作', key: 'action', width: isAdmin.value ? 260 : 200 },
  )
  return base
})

function canManageApp(record: AppVO) {
  return isAdmin.value || record.userId === userStore.userId
}

function formatTime(time: string) {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

function resolveCreatorName(app: AppVO) {
  return app.creatorName?.trim() || `用户 #${app.userId}`
}

function handleCardPageChange(page: number, pageSize: number) {
  pagination.current = page
  pagination.pageSize = pageSize
  loadList()
}

function confirmDelete(id: number) {
  Modal.confirm({
    title: '确定下架该应用吗？',
    content: '下架后数据仍保留，仅不再显示。',
    okText: '确定',
    cancelText: '取消',
    onOk: () => handleDelete(id),
  })
}

function hasMissingCover(app: AppVO) {
  return !app.coverImg?.trim()
}

function stopCoverPolling() {
  if (coverPollTimer) {
    clearInterval(coverPollTimer)
    coverPollTimer = null
  }
}

function startCoverPolling() {
  stopCoverPolling()
  if (!appList.value.some(hasMissingCover)) {
    return
  }
  let attempts = 0
  coverPollTimer = setInterval(async () => {
    attempts += 1
    if (attempts > 12 || !appList.value.some(hasMissingCover)) {
      stopCoverPolling()
      return
    }
    await loadList({ silent: true })
  }, 5000)
}

async function loadList(options: { silent?: boolean } = {}) {
  if (!options.silent) {
    loading.value = true
  }
  try {
    if (isAdmin.value) {
      try {
        const res = await getAdminAppList(pagination.current - 1, pagination.pageSize, featuredFilter.value)
        appList.value = res.data.content
        pagination.total = res.data.totalElements
        if (!options.silent) {
          startCoverPolling()
        }
        return
      } catch {
        // localStorage 显示管理员但 JWT 无 ADMIN 权限时，降级为普通列表
        const res = await getAppList(pagination.current - 1, pagination.pageSize)
        appList.value = res.data.content
        pagination.total = res.data.totalElements
        if (!options.silent) {
          startCoverPolling()
        }
        return
      }
    }
    const res = await getAppList(pagination.current - 1, pagination.pageSize)
    appList.value = res.data.content
    pagination.total = res.data.totalElements
    if (!options.silent) {
      startCoverPolling()
    }
  } catch (e: any) {
    if (!options.silent) {
      message.error(e.message || '加载应用列表失败')
    }
  } finally {
    if (!options.silent) {
      loading.value = false
    }
  }
}

function handleTableChange(pag: TablePaginationConfig) {
  pagination.current = pag.current || 1
  pagination.pageSize = pag.pageSize || 10
  loadList()
}

function handleFilterChange() {
  pagination.current = 1
  loadList()
}

function resetForm() {
  formState.appName = ''
  formState.description = ''
  formState.coverImg = ''
}

function openEditModal(record: AppVO) {
  editingId.value = record.id
  formState.appName = record.appName
  formState.description = record.description || ''
  formState.coverImg = record.coverImg || ''
  modalVisible.value = true
}

const beforeCoverUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)
  if (!isImage) {
    message.error('仅支持 JPG、PNG、WebP、GIF 格式')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    message.error('封面图片不能超过 2MB')
    return false
  }
  return true
}

const handleCoverUpload: UploadProps['customRequest'] = async (options) => {
  const file = options.file as File
  coverUploading.value = true
  try {
    const res = await uploadAppCover(file)
    formState.coverImg = res.data
    message.success('封面上传成功')
    options.onSuccess?.(res.data)
  } catch (e: any) {
    message.error(e.message || '封面上传失败')
    options.onError?.(e)
  } finally {
    coverUploading.value = false
  }
}

function clearCover() {
  formState.coverImg = ''
}

function closeModal() {
  modalVisible.value = false
  resetForm()
}

async function handleSubmit() {
  if (!formState.appName.trim()) {
    message.warning('请输入应用名称')
    return
  }

  modalLoading.value = true
  try {
    if (editingId.value === null) {
      return
    }

    const payload = {
      appName: formState.appName.trim(),
      description: formState.description.trim() || undefined,
      coverImg: formState.coverImg,
    }

    await updateApp(editingId.value, payload)
    message.success('更新成功')

    closeModal()
    await loadList()
  } catch (e: any) {
    message.error(e.message || '操作失败')
  } finally {
    modalLoading.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await deleteApp(id)
    message.success('下架成功')
    if (appList.value.length === 1 && pagination.current > 1) {
      pagination.current -= 1
    }
    await loadList()
  } catch (e: any) {
    message.error(e.message || '下架失败')
  }
}

async function handleFeaturedToggle(record: AppVO) {
  const nextFeatured = record.isFeatured !== 1
  featuredLoadingId.value = record.id
  try {
    await setAppFeatured(record.id, { featured: nextFeatured })
    record.isFeatured = nextFeatured ? 1 : 0
    message.success(nextFeatured ? '已设为精选' : '已取消精选')
  } catch (e: any) {
    message.error(e.message || '更新精选状态失败')
  } finally {
    featuredLoadingId.value = null
  }
}

watch(
  () => userStore.role,
  () => {
    pagination.current = 1
    featuredFilter.value = undefined
    loadList()
  },
)

onMounted(() => {
  loadList()
})

onBeforeUnmount(() => {
  stopCoverPolling()
})
</script>

<style scoped>
.app-manage-page {
  min-height: 400px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.filter-label {
  color: rgba(0, 0, 0, 0.65);
  font-size: 14px;
}

.cover-upload {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.cover-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-upload-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: rgba(0, 0, 0, 0.45);
}

.cover-upload-text {
  margin-top: 8px;
  font-size: 12px;
}

.cover-clear-btn {
  padding-left: 0;
}

.cover-hint {
  margin-top: 8px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.app-card-wrapper {
  height: 100%;
  cursor: pointer;
}

.app-card {
  height: 100%;
}

.card-cover-wrap {
  position: relative;
}

.card-action-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.2s;
}

.app-card-wrapper:hover .card-action-overlay {
  opacity: 1;
}

.action-menu {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin: 0;
  padding: 4px 8px;
  list-style: none;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.action-menu li {
  padding: 8px 12px;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
  cursor: pointer;
  transition: background 0.2s;
  white-space: nowrap;
}

.action-menu li + li {
  border-left: 1px solid rgba(0, 0, 0, 0.06);
}

.action-menu li:hover {
  background: rgba(0, 0, 0, 0.04);
}

.action-menu li.action-danger {
  color: #ff4d4f;
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

.card-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

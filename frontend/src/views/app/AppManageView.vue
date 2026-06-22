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
        <a-button type="primary" @click="openCreateModal">新建应用</a-button>
      </a-space>
    </div>

    <a-table
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

    <a-modal
      v-model:open="modalVisible"
      :title="isEdit ? '编辑应用' : '新建应用'"
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
        <a-form-item v-if="isEdit" label="应用封面">
          <div class="cover-upload">
            <a-upload
              list-type="picture-card"
              :show-upload-list="false"
              accept="image/jpeg,image/png,image/webp,image/gif"
              :before-upload="beforeCoverUpload"
              :custom-request="handleCoverUpload"
            >
              <img v-if="formState.coverImg" :src="formState.coverImg" alt="封面预览" class="cover-preview" />
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import type { UploadProps } from 'ant-design-vue'
import { LoadingOutlined, PlusOutlined } from '@ant-design/icons-vue'
import type { TablePaginationConfig } from 'ant-design-vue'
import {
  createApp,
  deleteApp,
  getAdminAppList,
  getAppList,
  setAppFeatured,
  updateApp,
  uploadAppCover,
} from '@/api/app'
import { useUserStore } from '@/stores/user'
import type { AppVO } from '@/types/app'

const userStore = useUserStore()
const isAdmin = computed(() => userStore.isAdmin())

const loading = ref(false)
const modalLoading = ref(false)
const modalVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const featuredLoadingId = ref<number | null>(null)
const featuredFilter = ref<number | undefined>(undefined)
const coverUploading = ref(false)
const appList = ref<AppVO[]>([])

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
    { title: '操作', key: 'action', width: isAdmin.value ? 200 : 140 },
  )
  return base
})

function canManageApp(record: AppVO) {
  if (!isAdmin.value) {
    return true
  }
  return record.userId === userStore.userId
}

async function loadList() {
  loading.value = true
  try {
    const res = isAdmin.value
      ? await getAdminAppList(pagination.current - 1, pagination.pageSize, featuredFilter.value)
      : await getAppList(pagination.current - 1, pagination.pageSize)
    appList.value = res.data.content
    pagination.total = res.data.totalElements
  } catch (e: any) {
    message.error(e.message || '加载应用列表失败')
  } finally {
    loading.value = false
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

function openCreateModal() {
  isEdit.value = false
  editingId.value = null
  resetForm()
  modalVisible.value = true
}

function openEditModal(record: AppVO) {
  isEdit.value = true
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
    const payload = {
      appName: formState.appName.trim(),
      description: formState.description.trim() || undefined,
      ...(isEdit.value ? { coverImg: formState.coverImg } : {}),
    }

    if (isEdit.value && editingId.value !== null) {
      await updateApp(editingId.value, payload)
      message.success('更新成功')
    } else {
      await createApp(payload)
      message.success('创建成功')
    }

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
</style>

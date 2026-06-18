﻿﻿﻿﻿﻿<template>
  <div class="user-manage-page">
    <a-card title="用户管理" class="user-card">
      <div class="search-bar" style="margin-bottom: 16px;">
        <a-input-search v-model:value="searchKeyword" placeholder="按用户名搜索" style="width: 300px;" @search="handleSearch" />
      </div>
      <a-table :columns="columns" :data-source="dataSource" :pagination="pagination" @change="handleTableChange">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'avatar'">
            <a-avatar :size="40" :src="record.avatar" />
          </template>
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'normal' ? 'success' : 'error'">
              {{ record.status === 'normal' ? '正常' : '禁用' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'role'">
            <a-tag :color="record.role === 'admin' ? 'gold' : 'default'">
              {{ record.role === 'admin' ? '管理员' : '普通用户' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'level'">
            <a-tag color="gold">{{ record.level }}</a-tag>
          </template>
          <template v-if="column.key === 'actions'">
            <a-space>
              <a-button type="primary" size="small" @click="handleEnable(record)" v-if="record.status === 'disabled'">
                启用
              </a-button>
              <a-button danger size="small" @click="handleDisable(record)" v-if="record.status === 'normal'">
                禁用
              </a-button>
              <a-button danger size="small" @click="handleDelete(record)">
                删除
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import type { UserVO } from '@/types/user'
import { getUserList, disableUser, enableUser, deleteUser } from '@/api/user'

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id' },
  { title: '头像', dataIndex: 'avatar', key: 'avatar', width: 80 },
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '昵称', dataIndex: 'nickname', key: 'nickname' },
  { title: '角色', dataIndex: 'role', key: 'role' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '等级', dataIndex: 'level', key: 'level' },
  { title: '积分', dataIndex: 'points', key: 'points' },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '操作', dataIndex: 'actions', key: 'actions', width: 200 },
]

const dataSource = ref<UserVO[]>([])
const pagination = ref({ current: 1, pageSize: 10, total: 0 })
const searchKeyword = ref('')

onMounted(() => {
  loadUserList()
})

async function loadUserList() {
  try {
    const params: { page: number; size: number; keyword?: string } = {
      page: pagination.value.current - 1,
      size: pagination.value.pageSize
    }
    if (searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }
    const res = await getUserList(params)
    dataSource.value = res.data.content
    pagination.value.total = res.data.totalElements
  } catch (e: any) {
    message.error(e.message || '加载用户列表失败')
  }
}

function handleSearch() {
  pagination.value.current = 1
  loadUserList()
}

function handleTableChange(paginationInfo: { current: number; pageSize: number }) {
  pagination.value.current = paginationInfo.current
  pagination.value.pageSize = paginationInfo.pageSize
  loadUserList()
}

async function handleDisable(record: UserVO) {
  Modal.confirm({
    title: '确认禁用',
    content: `确定要禁用用户 ${record.username} 吗？`,
    async onOk() {
      try {
        await disableUser(record.id)
        message.success('禁用成功')
        loadUserList()
      } catch (e: any) {
        message.error(e.message || '禁用失败')
      }
    },
  })
}

async function handleEnable(record: UserVO) {
  Modal.confirm({
    title: '确认启用',
    content: `确定要启用用户 ${record.username} 吗？`,
    async onOk() {
      try {
        await enableUser(record.id)
        message.success('启用成功')
        loadUserList()
      } catch (e: any) {
        message.error(e.message || '启用失败')
      }
    },
  })
}

async function handleDelete(record: UserVO) {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除用户 ${record.username} 吗？此操作不可恢复。`,
    async onOk() {
      try {
        await deleteUser(record.id)
        message.success('删除成功')
        loadUserList()
      } catch (e: any) {
        message.error(e.message || '删除失败')
      }
    },
  })
}
</script>

<style scoped>
.user-manage-page {
  padding: 24px;
}

.user-card {
  border-radius: 8px;
}
</style>

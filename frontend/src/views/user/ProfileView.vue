<template>
  <div class="profile-page">
    <a-card title="个人信息" class="profile-card">
      <div class="profile-content">
        <div class="avatar-section">
          <div class="avatar-wrapper">
            <a-avatar :size="128" :src="userStore.avatar" class="big-avatar">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <a-button type="primary" size="small" @click="triggerFileInput">更换头像</a-button>
            <input ref="fileInputRef" type="file" accept="image/*" @change="handleAvatarChange" class="file-input" />
          </div>
        </div>
        <div class="info-section">
          <a-descriptions :column="2" bordered>
            <a-descriptions-item label="用户名">{{ userStore.username }}</a-descriptions-item>
            <a-descriptions-item label="昵称">{{ userStore.nickname || '-' }}</a-descriptions-item>
            <a-descriptions-item label="角色">{{ userStore.role === 'admin' ? '管理员' : '普通用户' }}</a-descriptions-item>
            <a-descriptions-item label="等级">
              <a-tag color="gold">{{ userStore.level }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="积分">
              <a-tag color="blue">{{ userStore.points }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="状态">{{ userStore.isLoggedIn() ? '已登录' : '未登录' }}</a-descriptions-item>
          </a-descriptions>
        </div>
      </div>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { UserOutlined } from '@ant-design/icons-vue'
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { uploadAvatar } from '@/api/user'

const userStore = useUserStore()
const fileInputRef = ref<HTMLInputElement | null>(null)

function triggerFileInput() {
  fileInputRef.value?.click()
}

async function handleAvatarChange(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    message.error('请选择图片文件')
    return
  }
  try {
    const res = await uploadAvatar(file)
    const avatarUrl = res.data + '?t=' + Date.now()
    userStore.avatar = avatarUrl
    localStorage.setItem('avatar', avatarUrl)
    message.success('头像上传成功')
  } catch (e: any) {
    message.error(e.message || '上传失败')
  }
  target.value = ''
}
</script>

<style scoped>
.profile-page {
  padding: 24px;
}

.profile-card {
  border-radius: 8px;
}

.profile-content {
  display: flex;
  gap: 48px;
}

.avatar-section {
  flex-shrink: 0;
}

.avatar-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.big-avatar {
  border: 2px solid #f0f0f0;
}

.file-input {
  display: none;
}

.info-section {
  flex: 1;
}
</style>

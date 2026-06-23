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
            <a-descriptions-item label="昵称">
                <template v-if="editingNickname">
                  <a-input v-model:value="editNickname" @pressEnter="saveNickname" style="width: 200px; margin-right: 8px;" />
                  <a-button type="primary" size="small" @click="saveNickname">保存</a-button>
                  <a-button size="small" @click="cancelEditNickname">取消</a-button>
                </template>
                <template v-else>
                  <span>{{ nickname || '-' }}</span>
                  <a-button type="primary" size="small" @click="startEditNickname">编辑</a-button>
                </template>
            </a-descriptions-item>
            <a-descriptions-item label="手机号">
              <template v-if="editingPhone">
                <a-input v-model:value="editPhone" placeholder="请输入手机号" style="width: 200px; margin-right: 8px;" />
                <a-button type="primary" size="small" @click="savePhone">绑定</a-button>
                <a-button size="small" @click="cancelEditPhone">取消</a-button>
              </template>
              <template v-else-if="phone">
                <span>{{ phone }}</span>
              </template>
              <template v-else>
                <span style="color: #999;">未绑定</span>
                <a-button type="link" size="small" @click="startEditPhone">绑定</a-button>
              </template>
            </a-descriptions-item>
            <a-descriptions-item label="邮箱">
              <template v-if="editingEmail">
                <a-input v-model:value="editEmail" placeholder="请输入邮箱" style="width: 200px; margin-right: 8px;" />
                <a-button type="primary" size="small" @click="saveEmail">绑定</a-button>
                <a-button size="small" @click="cancelEditEmail">取消</a-button>
              </template>
              <template v-else-if="email">
                <span>{{ email }}</span>
              </template>
              <template v-else>
                <span style="color: #999;">未绑定</span>
                <a-button type="link" size="small" @click="startEditEmail">绑定</a-button>
              </template>
            </a-descriptions-item>
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
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { uploadAvatar, updateProfile, getCurrentUser } from '@/api/user'

const userStore = useUserStore()
const fileInputRef = ref<HTMLInputElement | null>(null)
const editingNickname = ref(false)
const editNickname = ref('')
const nickname = ref(userStore.nickname || userStore.username)
const phone = ref(userStore.phone)
const email = ref(userStore.email)
const editingPhone = ref(false)
const editPhone = ref('')
const editingEmail = ref(false)
const editEmail = ref('')

onMounted(async () => {
  try {
    const res = await getCurrentUser()
    const user = res.data
    nickname.value = user.nickname || user.username
    phone.value = user.phone || ''
    email.value = user.email || ''
  } catch (e: any) {
    message.error(e.message || '获取用户信息失败')
  }
})

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

function startEditNickname() {
  console.log('startEditNickname called, nickname:', nickname.value)
  editNickname.value = nickname.value || ''
  editingNickname.value = true
}

async function cancelEditNickname() {
  editingNickname.value = false
}

async function saveNickname() {
  if (!editNickname.value.trim()) {
    message.warning('昵称不能为空')
    return
  }
  console.log('saveNickname called, editNickname:', editNickname.value)
  try {
    const res = await updateProfile({ nickname: editNickname.value.trim() })
    console.log('updateProfile result:', res)
    if (res && res.data) {
      nickname.value = res.data.nickname || res.data.username
    } else {
      nickname.value = editNickname.value.trim()
    }
    userStore.nickname = nickname.value
    localStorage.setItem('nickname', nickname.value)
    editingNickname.value = false
    message.success('昵称更新成功')
  } catch (e: any) {
    console.error('saveNickname error:', e)
    message.error(e.message || '更新失败')
  }
}

function startEditPhone() {
  editPhone.value = phone.value || ''
  editingPhone.value = true
}

function cancelEditPhone() {
  editingPhone.value = false
}

async function savePhone() {
  if (!editPhone.value.trim()) {
    message.warning('手机号不能为空')
    return
  }
  try {
    const res = await updateProfile({ phone: editPhone.value.trim() })
    if (res && res.data) {
      phone.value = res.data.phone || ''
      userStore.updateProfile({ phone: res.data.phone || '' })
    }
    editingPhone.value = false
    message.success('手机号绑定成功')
  } catch (e: any) {
    console.error('savePhone error:', e)
    message.error(e.message || '绑定失败')
  }
}

function startEditEmail() {
  editEmail.value = email.value || ''
  editingEmail.value = true
}

function cancelEditEmail() {
  editingEmail.value = false
}

async function saveEmail() {
  if (!editEmail.value.trim()) {
    message.warning('邮箱不能为空')
    return
  }
  try {
    const res = await updateProfile({ email: editEmail.value.trim() })
    if (res && res.data) {
      email.value = res.data.email || ''
      userStore.updateProfile({ email: res.data.email || '' })
    }
    editingEmail.value = false
    message.success('邮箱绑定成功')
  } catch (e: any) {
    console.error('saveEmail error:', e)
    message.error(e.message || '绑定失败')
  }
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

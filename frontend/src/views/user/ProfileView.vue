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

    <div class="section-row">
      <a-card title="每日签到" class="section-card">
        <div class="checkin-content">
          <div class="checkin-main">
            <template v-if="checkinStats.checkedInToday">
              <a-tag color="green">今日已签到</a-tag>
              <a-divider />
              <p>连续签到 <span class="highlight">{{ checkinStats.consecutiveDays }}</span> 天</p>
              <p>本月签到 <span class="highlight">{{ checkinStats.monthCheckins }}</span> 天</p>
              <p>累计签到 <span class="highlight">{{ checkinStats.totalCheckins }}</span> 天</p>
            </template>
            <template v-else>
              <a-button type="primary" size="large" @click="doCheckin">
                <template #icon><CalendarOutlined /></template>
                签到
              </a-button>
              <a-divider />
              <p>连续签到 <span class="highlight">{{ checkinStats.consecutiveDays }}</span> 天</p>
              <p>本月签到 <span class="highlight">{{ checkinStats.monthCheckins }}</span> 天</p>
              <p>累计签到 <span class="highlight">{{ checkinStats.totalCheckins }}</span> 天</p>
            </template>
          </div>
          <div class="checkin-rules">
            <h4>签到规则</h4>
            <ul>
              <li>每日签到 +5 积分</li>
              <li>连续签到7天额外 +20 积分</li>
              <li>连续签到30天额外 +100 积分</li>
            </ul>
          </div>
        </div>
      </a-card>

      <a-card title="新手任务" class="section-card">
        <div class="progress-info">
          <span>进度：{{ newbieTasks.completedCount }}/{{ newbieTasks.totalCount }}</span>
          <span>已获得：{{ newbieTasks.earnedPoints }}/{{ newbieTasks.totalPoints }} 积分</span>
        </div>
        <a-progress :percent="(newbieTasks.completedCount / newbieTasks.totalCount) * 100" :show-info="false" />
        <a-list :data-source="newbieTasks.tasks" class="task-list">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta>
                <template #title>
                  <span>{{ item.name }}</span>
                  <a-tag :color="item.completed ? 'green' : 'default'" class="task-tag">
                    {{ item.completed ? '已完成' : '未完成' }}
                  </a-tag>
                </template>
                <template #description>{{ item.description }}</template>
              </a-list-item-meta>
              <span class="task-points">+{{ item.points }} 积分</span>
              <template v-if="item.completed">
                <CheckCircleOutlined class="completed-icon" />
              </template>
            </a-list-item>
          </template>
        </a-list>
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { UserOutlined, CalendarOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'
import { uploadAvatar, updateProfile, getCurrentUser, checkin, getCheckinStats, getNewbieTasks } from '@/api/user'

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

const checkinStats = ref({
  checkedInToday: false,
  consecutiveDays: 0,
  totalCheckins: 0,
  monthCheckins: 0
})

const newbieTasks = ref({
  tasks: [],
  completedCount: 0,
  totalCount: 0,
  totalPoints: 0,
  earnedPoints: 0
})

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
  loadCheckinStats()
  loadNewbieTasks()
})

async function loadCheckinStats() {
  try {
    const res = await getCheckinStats()
    checkinStats.value = res.data
  } catch (e: any) {
    console.error('loadCheckinStats error:', e)
  }
}

async function loadNewbieTasks() {
  try {
    const res = await getNewbieTasks()
    newbieTasks.value = res.data
  } catch (e: any) {
    console.error('loadNewbieTasks error:', e)
  }
}

async function doCheckin() {
  try {
    const res = await checkin()
    message.success(res.data.message)
    await loadCheckinStats()
    await loadUserInfo()
  } catch (e: any) {
    message.error(e.message || '签到失败')
  }
}

async function loadUserInfo() {
  try {
    const res = await getCurrentUser()
    const user = res.data
    userStore.points = user.points
    userStore.level = user.level
    localStorage.setItem('points', String(user.points))
    localStorage.setItem('level', user.level)
  } catch (e: any) {
    console.error('loadUserInfo error:', e)
  }
}

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
    await loadNewbieTasks()
    await loadUserInfo()
  } catch (e: any) {
    message.error(e.message || '上传失败')
  }
  target.value = ''
}

function startEditNickname() {
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
  try {
    const res = await updateProfile({ nickname: editNickname.value.trim() })
    if (res && res.data) {
      nickname.value = res.data.nickname || res.data.username
    } else {
      nickname.value = editNickname.value.trim()
    }
    userStore.nickname = nickname.value
    localStorage.setItem('nickname', nickname.value)
    editingNickname.value = false
    message.success('昵称更新成功')
    await loadNewbieTasks()
    await loadUserInfo()
  } catch (e: any) {
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
    await loadNewbieTasks()
    await loadUserInfo()
  } catch (e: any) {
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
    await loadNewbieTasks()
    await loadUserInfo()
  } catch (e: any) {
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
  margin-bottom: 24px;
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

.section-row {
  display: flex;
  gap: 24px;
}

.section-card {
  flex: 1;
  border-radius: 8px;
}

.checkin-content {
  display: flex;
  gap: 32px;
}

.checkin-main {
  flex: 1;
  text-align: center;
  padding-top: 16px;
}

.highlight {
  font-size: 20px;
  font-weight: bold;
  color: #1890ff;
}

.checkin-rules {
  width: 200px;
  padding-left: 16px;
  border-left: 1px solid #f0f0f0;
}

.checkin-rules h4 {
  margin-top: 0;
  margin-bottom: 12px;
}

.checkin-rules ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.checkin-rules li {
  padding: 6px 0;
  font-size: 13px;
  color: #666;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
}

.task-list {
  margin-top: 16px;
}

.task-tag {
  margin-left: 8px;
}

.task-points {
  color: #faad14;
  font-weight: bold;
}

.completed-icon {
  color: #52c41a;
  font-size: 18px;
  margin-left: 8px;
}
</style>
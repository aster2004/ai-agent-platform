<template>
  <a-layout class="layout">
    <a-layout-header class="header">
      <div class="header-left">
        <span class="logo">AI Agent</span>
        <a-menu
            v-model:selectedKeys="selectedKeys"
            mode="horizontal"
            class="nav-menu"
            @click="handleMenuClick"
        >

          <!-- 成员7：新增首页导航菜单 -->
          <a-menu-item key="/chat">
            <HomeOutlined />
            首页
          </a-menu-item>
          <a-menu-item key="/app">
            <AppstoreOutlined />
            应用管理
          </a-menu-item>
          <a-menu-item key="/app/deploy">
            <CloudUploadOutlined />
            部署分享
          </a-menu-item>
          <a-menu-item key="/app/gallery">
            <StarOutlined />
            精选广场
          </a-menu-item>
          <a-menu-item v-if="userStore.isAdmin()" key="/user">
            <UserOutlined />
            用户管理
          </a-menu-item>
        </a-menu>
      </div>
      <div class="header-right">
        <a-dropdown v-if="userStore.isLoggedIn()">
          <a-space class="user-info">
            <a-avatar :size="32" :src="userStore.avatar" class="user-avatar">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <span class="username">{{ displayName }}</span>
            <a-tag v-if="userStore.isAdmin()" color="gold">管理员</a-tag>
          </a-space>
          <template #overlay>
            <a-menu @click="handleUserMenuClick">
              <a-menu-item key="profile">
                <UserOutlined />
                个人信息
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout">
                <LogoutOutlined />
                退出登录
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <template v-else>
          <a-button type="primary" class="login-btn" @click="goLogin">
            登录
          </a-button>
        </template>
      </div>
    </a-layout-header>
    <a-layout-content class="content">
      <router-view />
    </a-layout-content>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  HomeOutlined,
  AppstoreOutlined,
  CloudUploadOutlined,
  StarOutlined,
  UserOutlined,
  LogoutOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'
import { logout as logoutApi } from '@/api/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const selectedKeys = ref<string[]>([route.path])

const displayName = computed(() => userStore.nickname || userStore.username || '用户')

watch(() => route.path, (path) => {
  selectedKeys.value = path.includes('/deploy') ? ['/app/deploy'] : [path]
})

function handleMenuClick({ key }: { key: string }) {
  router.push(key)
}

function goLogin() {
  router.push('/login')
}

async function handleUserMenuClick({ key }: { key: string }) {
  if (key === 'profile') {
    router.push('/profile')
  } else if (key === 'logout') {
    try {
      await logoutApi()
    } catch (e) {
      // ignore
    }
    userStore.logout()
    message.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  min-height: 100vh;
  background: #f0f2f5;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.logo {
  font-size: 18px;
  font-weight: 600;
  color: #1677ff;
  margin-right: 32px;
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
  line-height: 64px;
  background: transparent;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 24px;
}

.user-info {
  cursor: pointer;
}

.user-avatar {
  background: #1677ff;
}

.username {
  color: rgba(0, 0, 0, 0.65);
  font-size: 14px;
}

.login-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  color: rgba(0, 0, 0, 0.65);
}

.content {
  margin: 24px;
  padding: 24px;
  background: #fff;
  border-radius: 8px;
  min-height: calc(100vh - 64px - 48px);
}
</style>

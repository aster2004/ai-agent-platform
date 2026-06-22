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
          <a-menu-item key="/codegen">
            <CodeOutlined />
            代码生成
          </a-menu-item>
          <a-menu-item key="/app">
            <AppstoreOutlined />
            应用管理
          </a-menu-item>
          <a-menu-item key="/app/gallery">
            <StarOutlined />
            精选广场
          </a-menu-item>
          <a-menu-item key="/user">
            <UserOutlined />
            用户管理
          </a-menu-item>
        </a-menu>
      </div>
      <div class="header-right">
        <a-dropdown v-if="isLoggedIn">
          <a-space class="user-info">
            <a-avatar :size="32" class="user-avatar">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <span class="username">{{ displayName }}</span>
            <a-tag v-if="userStore.isAdmin()" color="gold">管理员</a-tag>
          </a-space>
          <template #overlay>
            <a-menu @click="handleRoleMenuClick">
              <a-menu-item key="user">切换为普通用户</a-menu-item>
              <a-menu-item key="admin">切换为管理员</a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout">退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <template v-else>
          <a-button type="text" class="login-btn" @click="handleLogin">
            <UserOutlined />
            <span>登录</span>
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
  AppstoreOutlined,
  CodeOutlined,
  StarOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const selectedKeys = ref<string[]>([route.path])

const isLoggedIn = computed(() => userStore.isLoggedIn() || !!userStore.username)
const displayName = computed(() => userStore.nickname || userStore.username || '演示用户')

watch(() => route.path, (path) => {
  selectedKeys.value = [path]
})

function handleMenuClick({ key }: { key: string }) {
  router.push(key)
}

function handleLogin() {
  userStore.setUser({
    userId: 1,
    username: 'dev_user',
    nickname: '开发测试用户',
    token: 'mock-token',
    role: 'user',
  })
  message.success('已登录为普通用户（演示）')
}

function handleRoleMenuClick({ key }: { key: string }) {
  if (key === 'logout') {
    userStore.logout()
    message.info('已退出登录')
    return
  }

  localStorage.setItem('role', key)
  userStore.role = key as 'user' | 'admin'
  message.success(key === 'admin' ? '已切换为管理员' : '已切换为普通用户')
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

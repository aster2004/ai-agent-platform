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
          <a-menu-item key="/user">
            <UserOutlined />
            用户管理
          </a-menu-item>
        </a-menu>
      </div>
      <div class="header-right">
        <template v-if="isLoggedIn">
          <a-avatar :size="32" class="user-avatar">
            <template #icon><UserOutlined /></template>
          </a-avatar>
          <span class="username">{{ username }}</span>
          <a-button type="link" @click="handleLogout">退出</a-button>
        </template>
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
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { CodeOutlined, AppstoreOutlined, UserOutlined } from '@ant-design/icons-vue'

const route = useRoute()
const router = useRouter()
const selectedKeys = ref<string[]>([route.path])

// 静态登录状态，后续接入真实登录逻辑
const isLoggedIn = ref(false)
const username = ref('演示用户')

watch(() => route.path, (path) => {
  selectedKeys.value = [path]
})

function handleMenuClick({ key }: { key: string }) {
  router.push(key)
}

function handleLogin() {
  isLoggedIn.value = true
  message.info('登录功能开发中，当前为静态演示')
}

function handleLogout() {
  isLoggedIn.value = false
  message.info('退出功能开发中，当前为静态演示')
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

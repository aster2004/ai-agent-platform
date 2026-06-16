<template>
  <div class="login-page">
    <a-card title="注册" class="login-card">
      <a-form :model="form" @finish="handleRegister">
        <a-form-item name="username" :rules="[{ required: true, message: '请输入用户名' }]">
          <a-input v-model:value="form.username" placeholder="用户名" size="large" />
        </a-form-item>
        <a-form-item name="password" :rules="[{ required: true, min: 6, message: '密码至少 6 位' }]">
          <a-input-password v-model:value="form.password" placeholder="密码" size="large" />
        </a-form-item>
        <a-form-item name="nickname">
          <a-input v-model:value="form.nickname" placeholder="昵称（可选）" size="large" />
        </a-form-item>
        <a-form-item name="email">
          <a-input v-model:value="form.email" placeholder="邮箱（可选）" size="large" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block size="large" :loading="loading">
            注册
          </a-button>
        </a-form-item>
        <div class="login-footer">
          已有账号？<router-link to="/login">去登录</router-link>
        </div>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { register } from '@/api/user'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', password: '', nickname: '', email: '' })

async function handleRegister() {
  loading.value = true
  try {
    await register(form)
    message.success('注册成功，请登录')
    router.push('/login')
  } catch (e: any) {
    message.error(e.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
}

.login-footer {
  text-align: center;
}
</style>

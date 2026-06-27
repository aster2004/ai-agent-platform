<template>
  <div class="login-page">
    <a-card title="注册" class="login-card">
      <a-form :model="form" @finish="handleRegister">
        <a-form-item name="username" :rules="[{ required: true, message: '请输入用户名' }, { min: 2, max: 50, message: '用户名长度必须在2-50位之间' }]">
          <a-input v-model:value="form.username" placeholder="用户名" size="large">
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item>
          <a-radio-group v-model:value="registerType" button-style="solid">
            <a-radio-button value="phone">手机号注册</a-radio-button>
            <a-radio-button value="email">邮箱注册</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item :rules="[{ required: true, message: inputPlaceholder }]">
          <a-input v-model:value="inputValue" :placeholder="inputPlaceholder" size="large">
            <template #prefix>
              <PhoneOutlined v-if="registerType === 'phone'" />
              <MailOutlined v-else />
            </template>
          </a-input>
        </a-form-item>
        <a-form-item name="password" :rules="[{ required: true, message: '请输入密码' }, { min: 6, max: 20, message: '密码长度必须在6-20位之间' }]">
          <a-input-password v-model:value="form.password" placeholder="密码" size="large">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item name="nickname">
          <a-input v-model:value="form.nickname" placeholder="昵称（选填）" size="large">
            <template #prefix><TrophyOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" block size="large" :loading="loading">
            注册
          </a-button>
        </a-form-item>
        <div class="login-footer">
          已有账号？<router-link to="/login">立即登录</router-link>
        </div>
      </a-form>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { PhoneOutlined, MailOutlined, LockOutlined, TrophyOutlined, UserOutlined } from '@ant-design/icons-vue'
import { register, login } from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const registerType = ref('phone')
const form = reactive({ username: '', phone: '', email: '', password: '', nickname: '' })

const inputPlaceholder = computed(() => {
  return registerType.value === 'phone' ? '请输入手机号' : '请输入邮箱'
})

const inputValue = computed({
  get: () => {
    return registerType.value === 'phone' ? form.phone : form.email
  },
  set: (value) => {
    if (registerType.value === 'phone') {
      form.phone = value
    } else {
      form.email = value
    }
  }
})

async function handleRegister() {
  loading.value = true
  try {
    const data = {
      username: form.username,
      phone: registerType.value === 'phone' ? form.phone : undefined,
      email: registerType.value === 'email' ? form.email : undefined,
      password: form.password,
      nickname: form.nickname || undefined,
    }
    await register(data)
    const loginRes = await login({ username: form.username, password: form.password })
    userStore.setUser(loginRes.data)
    message.success('注册成功')
    router.push('/chat')
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

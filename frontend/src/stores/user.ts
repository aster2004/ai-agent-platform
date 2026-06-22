import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginVO } from '@/types/user'

export type UserRole = 'user' | 'admin'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(Number(localStorage.getItem('userId') || 0))
  const username = ref(localStorage.getItem('username') || '')
  const nickname = ref(localStorage.getItem('nickname') || '')
  const role = ref<UserRole>((localStorage.getItem('role') as UserRole) || 'user')

  function setUser(data: LoginVO) {
    token.value = data.token
    userId.value = data.userId
    username.value = data.username
    nickname.value = data.nickname
    role.value = data.role || 'user'
    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', String(data.userId))
    localStorage.setItem('username', data.username)
    localStorage.setItem('nickname', data.nickname)
    localStorage.setItem('role', role.value)
  }

  function logout() {
    token.value = ''
    userId.value = 0
    username.value = ''
    nickname.value = ''
    role.value = 'user'
    localStorage.clear()
  }

  function isLoggedIn() {
    return !!token.value
  }

  function isAdmin() {
    return role.value === 'admin'
  }

  return { token, userId, username, nickname, role, setUser, logout, isLoggedIn, isAdmin }
})

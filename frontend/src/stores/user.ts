import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginVO } from '@/types/user'

export type UserRole = 'user' | 'admin'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userId = ref(Number(localStorage.getItem('userId') || 0))
  const username = ref(localStorage.getItem('username') || '')
  const nickname = ref(localStorage.getItem('nickname') || '')
  const avatar = ref(localStorage.getItem('avatar') || '')
  const phone = ref(localStorage.getItem('phone') || '')
  const email = ref(localStorage.getItem('email') || '')
  const role = ref<UserRole>((localStorage.getItem('role') as UserRole) || 'user')
  const points = ref(Number(localStorage.getItem('points') || 0))
  const level = ref(localStorage.getItem('level') || '')

  function setUser(data: LoginVO) {
    token.value = data.token
    userId.value = data.userId
    username.value = data.username
    nickname.value = data.nickname || ''
    avatar.value = data.avatar || ''
    phone.value = data.phone || ''
    email.value = data.email || ''
    role.value = (data.role as UserRole) || 'user'
    points.value = data.points || 0
    level.value = data.level || ''

    localStorage.setItem('token', data.token)
    localStorage.setItem('userId', String(data.userId))
    localStorage.setItem('username', data.username)
    localStorage.setItem('nickname', data.nickname || '')
    localStorage.setItem('avatar', data.avatar || '')
    localStorage.setItem('phone', data.phone || '')
    localStorage.setItem('email', data.email || '')
    localStorage.setItem('role', role.value)
    localStorage.setItem('points', String(data.points || 0))
    localStorage.setItem('level', data.level || '')
  }

  function logout() {
    token.value = ''
    userId.value = 0
    username.value = ''
    nickname.value = ''
    avatar.value = ''
    phone.value = ''
    email.value = ''
    role.value = 'user'
    points.value = 0
    level.value = ''
    localStorage.clear()
  }

  function isLoggedIn() {
    return !!token.value
  }

  function isAdmin() {
    return role.value === 'admin'
  }

  function updateProfile(data: { nickname?: string; phone?: string; email?: string; avatar?: string }) {
    if (data.nickname !== undefined) {
      nickname.value = data.nickname
      localStorage.setItem('nickname', data.nickname)
    }
    if (data.phone !== undefined) {
      phone.value = data.phone
      localStorage.setItem('phone', data.phone)
    }
    if (data.email !== undefined) {
      email.value = data.email
      localStorage.setItem('email', data.email)
    }
    if (data.avatar !== undefined) {
      avatar.value = data.avatar
      localStorage.setItem('avatar', data.avatar)
    }
  }

  function addPoints(amount: number) {
    points.value += amount
    localStorage.setItem('points', String(points.value))
  }

  function updatePoints(newPoints: number) {
    points.value = newPoints
    localStorage.setItem('points', String(newPoints))
  }

  return { token, userId, username, nickname, avatar, phone, email, role, points, level, setUser, logout, isLoggedIn, isAdmin, updateProfile, addPoints, updatePoints }
})

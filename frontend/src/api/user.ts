import request from '@/utils/request'
import type { LoginParams, LoginVO, RegisterParams, UserVO, UserListParams } from '@/types/user'
import type { Result, PageResult } from '@/types/common'

export function login(data: LoginParams) {
  return request.post<any, Result<LoginVO>>('/api/user/login', data)
}

export function register(data: RegisterParams) {
  return request.post<any, Result<UserVO>>('/api/user/register', data)
}

export function getCurrentUser() {
  return request.get<any, Result<UserVO>>('/api/user/current')
}

export function updateProfile(data: { nickname?: string; phone?: string; email?: string }) {
  return request.put<any, Result<UserVO>>('/api/user/profile', data)
}

export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, Result<string>>('/api/user/avatar/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function logout() {
  return request.post<any, Result<void>>('/api/user/logout')
}

export function getUserList(params: UserListParams) {
  return request.get<any, Result<PageResult<UserVO>>>('/api/user/admin/list', { params })
}

export function disableUser(id: number) {
  return request.put<any, Result<void>>(`/api/user/admin/${id}/disable`)
}

export function enableUser(id: number) {
  return request.put<any, Result<void>>(`/api/user/admin/${id}/enable`)
}

export function deleteUser(id: number) {
  return request.delete<any, Result<void>>(`/api/user/admin/${id}`)
}

export function checkin() {
  return request.post<any, Result<{ success: boolean; message: string; consecutiveDays: number; todayPoints: number }>>('/api/user/checkin')
}

export function getCheckinStats() {
  return request.get<any, Result<{ checkedInToday: boolean; consecutiveDays: number; totalCheckins: number; monthCheckins: number }>>('/api/user/checkin/stats')
}

export function getNewbieTasks() {
  return request.get<any, Result<{
    tasks: Array<{ name: string; points: number; description: string; type: string; completed: boolean }>
    completedCount: number
    totalCount: number
    totalPoints: number
    earnedPoints: number
  }>>('/api/user/newbie-tasks')
}

export interface PointsLogItem {
  id: number
  userId: number
  points: number
  type: string
  description: string
  recordDate: string
  createTime: string
}

export function getPointsDetail(startDate?: string, endDate?: string) {
  return request.get<any, Result<Record<string, PointsLogItem[]>>>('/api/user/points/detail', {
    params: { startDate, endDate }
  })
}

export function getPointsByDate(date: string) {
  return request.get<any, Result<{ logs: PointsLogItem[]; total: number; date: string }>>('/api/user/points/by-date', {
    params: { date }
  })
}

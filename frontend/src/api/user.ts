import request from '@/utils/request'
import type { LoginParams, LoginVO, RegisterParams, UserVO, UserListParams } from '@/types/user'
import type { Result, PageResult } from '@/types/common'

export function login(data: LoginParams) {
  return request.post<any, Result<LoginVO>>('/user/login', data)
}

export function register(data: RegisterParams) {
  return request.post<any, Result<UserVO>>('/user/register', data)
}

export function getCurrentUser() {
  return request.get<any, Result<UserVO>>('/user/current')
}

export function updateProfile(data: { nickname?: string; phone?: string; email?: string }) {
  return request.put<any, Result<UserVO>>('/user/profile', data)
}

export function uploadAvatar(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, Result<string>>('/user/avatar/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function logout() {
  return request.post<any, Result<void>>('/user/logout')
}

export function getUserList(params: UserListParams) {
  return request.get<any, Result<PageResult<UserVO>>>('/user/admin/list', { params })
}

export function disableUser(id: number) {
  return request.put<any, Result<void>>(`/user/admin/${id}/disable`)
}

export function enableUser(id: number) {
  return request.put<any, Result<void>>(`/user/admin/${id}/enable`)
}

export function deleteUser(id: number) {
  return request.delete<any, Result<void>>(`/user/admin/${id}`)
}

import request from '@/utils/request'
import type { LoginParams, LoginVO, RegisterParams, UserVO } from '@/types/user'
import type { Result } from '@/types/common'

export function login(data: LoginParams) {
  return request.post<any, Result<LoginVO>>('/user/login', data)
}

export function register(data: RegisterParams) {
  return request.post<any, Result<UserVO>>('/user/register', data)
}

export function getUserList() {
  return request.get<any, Result<UserVO[]>>('/user/list')
}

export function deleteUser(id: number) {
  return request.delete<any, Result<void>>(`/user/${id}`)
}

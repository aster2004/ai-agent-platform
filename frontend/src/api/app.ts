import request from '@/utils/request'
import type {
  AppCodeUpdateParams,
  AppCreateParams,
  AppFeaturedUpdateParams,
  AppUpdateParams,
  AppVO,
} from '@/types/app'
import type { PageResult, Result } from '@/types/common'

export function getAppList(page = 0, size = 10) {
  return request.get<any, Result<PageResult<AppVO>>>('/app/list', { params: { page, size } })
}

export function getAdminAppList(page = 0, size = 10, isFeatured?: number) {
  return request.get<any, Result<PageResult<AppVO>>>('/app/admin/list', {
    params: { page, size, isFeatured },
  })
}

export function getAppById(id: number) {
  return request.get<any, Result<AppVO>>(`/app/${id}`)
}

export function createApp(data: AppCreateParams) {
  return request.post<any, Result<AppVO>>('/app', data)
}

export function updateApp(id: number, data: AppUpdateParams) {
  return request.put<any, Result<AppVO>>(`/app/${id}`, data)
}

export function setAppFeatured(id: number, data: AppFeaturedUpdateParams) {
  return request.put<any, Result<AppVO>>(`/app/${id}/featured`, data)
}

export function deleteApp(id: number) {
  return request.delete<any, Result<void>>(`/app/${id}`)
}

export function getFeaturedApps() {
  return request.get<any, Result<AppVO[]>>('/app/featured')
}

export function updateAppCode(id: number, data: AppCodeUpdateParams) {
  return request.put<any, Result<AppVO>>(`/app/${id}/code`, data)
}

export function uploadAppCover(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<any, Result<string>>('/app/upload/cover', formData)
}

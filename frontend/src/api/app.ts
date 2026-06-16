import request from '@/utils/request'
import type { AppCreateParams, AppVO } from '@/types/app'
import type { PageResult, Result } from '@/types/common'

export function getAppList(page = 0, size = 10) {
  return request.get<any, Result<PageResult<AppVO>>>('/app/list', { params: { page, size } })
}

export function createApp(data: AppCreateParams) {
  return request.post<any, Result<AppVO>>('/app', data)
}

export function deleteApp(id: number) {
  return request.delete<any, Result<void>>(`/app/${id}`)
}

import request from '@/utils/request'
import type { CoverResultVO, DeployResultVO, PreviewVO } from '@/types/appDeploy'
import type { Result } from '@/types/common'

export function getAppPreview(appId: number) {
  return request.get<any, Result<PreviewVO>>(`/app/${appId}/preview`)
}

export function deployApp(appId: number) {
  return request.post<any, Result<DeployResultVO>>(`/app/${appId}/deploy`)
}

export function getDeployUrl(appId: number) {
  return request.get<any, Result<string>>(`/app/${appId}/deploy-url`)
}

export function downloadAppSource(appId: number) {
  return `/api/app/${appId}/download`
}

export function captureCover(appId: number) {
  return request.post<any, Result<CoverResultVO>>(`/app/${appId}/cover`)
}

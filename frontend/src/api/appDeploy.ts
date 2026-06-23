import request from '@/utils/request'
import type {
  CoverResultVO,
  DeployModeCode,
  DeployModeVO,
  DeployResultVO,
  PreviewVO,
} from '@/types/appDeploy'
import type { Result } from '@/types/common'

export function getAppPreview(appId: number) {
  return request.get<any, Result<PreviewVO>>(`/api/app/${appId}/preview`)
}

export function getDeployModes() {
  return request.get<any, Result<DeployModeVO[]>>('/api/app/deploy/modes')
}

export function deployApp(appId: number, mode: DeployModeCode = 'local') {
  return request.post<any, Result<DeployResultVO>>(`/api/app/${appId}/deploy`, { mode })
}

export function getDeployUrl(appId: number) {
  return request.get<any, Result<DeployResultVO>>(`/api/app/${appId}/deploy-url`)
}

export function downloadAppSource(appId: number) {
  return `/api/app/${appId}/download`
}

export function captureCover(appId: number) {
  return request.post<any, Result<CoverResultVO>>(`/api/app/${appId}/cover`)
}

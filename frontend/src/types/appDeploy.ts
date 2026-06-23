export interface PreviewVO {
  appId: number
  appName: string
  previewUrl: string
  coverImg?: string
}

export interface DeployModeVO {
  code: string
  label: string
  description: string
}

export interface DeployResultVO {
  appId: number
  deployUrl: string
  message: string
  deployMode: string
  deployModeLabel: string
}

export interface CoverResultVO {
  appId: number
  coverImg: string
}

export type DeployModeCode = 'local' | 'nginx' | 'docker'

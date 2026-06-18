export interface PreviewVO {
  appId: number
  appName: string
  previewUrl: string
  coverImg?: string
}

export interface DeployResultVO {
  appId: number
  deployUrl: string
  message: string
}

export interface CoverResultVO {
  appId: number
  coverImg: string
}

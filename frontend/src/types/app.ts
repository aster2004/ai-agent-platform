export interface AppVO {
  id: number
  appName: string
  description: string
  userId: number
  creatorName?: string
  coverImg?: string
  appCode?: string
  isFeatured?: number
  status: string
  createTime: string
  updateTime: string
}

export interface AppCreateParams {
  appName: string
  description?: string
}

export interface AppUpdateParams {
  appName: string
  description?: string
  coverImg?: string
}

export interface AppFeaturedUpdateParams {
  featured?: boolean
  isFeatured?: number
}

export interface AppCodeUpdateParams {
  codeContent: string
}

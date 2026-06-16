export interface AppVO {
  id: number
  appName: string
  description: string
  userId: number
  status: string
  createTime: string
  updateTime: string
}

export interface AppCreateParams {
  appName: string
  description?: string
}

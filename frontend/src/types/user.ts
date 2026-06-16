export interface UserVO {
  id: number
  username: string
  nickname: string
  email: string
  status: number
  createTime: string
  updateTime: string
}

export interface LoginParams {
  username: string
  password: string
}

export interface RegisterParams extends LoginParams {
  nickname?: string
  email?: string
}

export interface LoginVO {
  userId: number
  username: string
  nickname: string
  token: string
}

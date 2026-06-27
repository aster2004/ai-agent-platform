// 全局枚举，对齐api.md规范，约束固定字符串
export type UserRole = 'user' | 'admin'
export type UserStatus = 'normal' | 'disabled'

// 登录请求参数（完全匹配文档，无问题）
export interface LoginParams {
  username: string
  password: string
}

// 注册参数（保留新增phone/email/avatar，后端需同步支持接收）
export interface RegisterParams {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
}

// 登录返回VO（后端返回扁平结构）
export interface LoginVO {
  token: string
  userId: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
  avatar: string | null
  role: UserRole
  points: number
  level: string
}

// 用户列表分页参数：对齐标准pageNum/pageSize，替换keyword为username，补充status筛选
export interface UserListParams {
  pageNum: number
  pageSize: number
  // 兼容后端旧别名（可选保留）
  page?: number
  size?: number
  // 用户名模糊搜索，和后端参数名统一
  username?: string
  // 用户状态筛选
  status?: UserStatus
}

// 用户返回VO（列表/详情，保留email/phone/avatar，修复level、avatar空值）
export interface UserVO {
  id: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
  avatar: string | null
  status: UserStatus
  role: UserRole
  points: number
  level: string
  createTime: string
  updateTime: string
}

// 获取当前登录用户专用VO（无创建/更新时间）
export interface CurrentUserVO {
  id: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
  avatar: string | null
  status: UserStatus
  role: UserRole
  points: number
  level: string
}

// 管理员启用/禁用用户返回类型
export interface UserStatusVO {
  id: number
  status: UserStatus
}
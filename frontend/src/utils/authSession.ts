import { message } from 'ant-design-vue'

const EXPIRED_MSG = '登录已过期，请重新登录'

let redirecting = false

/** Token 失效：清理本地登录态并跳转登录页 */
export function handleSessionExpired(tip = EXPIRED_MSG) {
  if (redirecting) return
  if (window.location.pathname === '/login' || window.location.pathname === '/register') {
    return
  }

  redirecting = true
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  localStorage.removeItem('userId')
  localStorage.removeItem('username')
  localStorage.removeItem('nickname')
  localStorage.removeItem('avatar')
  localStorage.removeItem('phone')
  localStorage.removeItem('email')
  localStorage.removeItem('role')
  localStorage.removeItem('points')
  localStorage.removeItem('level')

  message.warning(tip)
  window.setTimeout(() => {
    window.location.href = '/login'
  }, 400)
}

export function isSessionExpiredResponse(status: number) {
  return status === 401
}

export { EXPIRED_MSG }

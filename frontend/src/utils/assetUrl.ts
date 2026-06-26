/**
 * 分享链接由后端 ShareUrlResolver 统一生成（局域网 IP），前端直接使用 API 返回值。
 */
export const BACKEND_ORIGIN =
  import.meta.env.VITE_BACKEND_ORIGIN || 'http://localhost:8080'

export function resolveAssetUrl(url: string): string {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url
  }
  return url.startsWith('/') ? url : `/${url}`
}

/** 封面图 URL：转为相对路径走 Vite 代理，兼容数据库中的局域网绝对地址 */
export function resolveCoverUrl(url?: string | null): string {
  if (!url) return ''
  const trimmed = url.trim()
  if (!trimmed) return ''
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    try {
      return resolveAssetUrl(new URL(trimmed).pathname)
    } catch {
      return trimmed
    }
  }
  return resolveAssetUrl(trimmed)
}

export function toBackendAssetUrl(url: string): string {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url
  }
  return `${BACKEND_ORIGIN}${resolveAssetUrl(url)}`
}

export function toFullAssetUrl(url: string): string {
  return toBackendAssetUrl(url)
}

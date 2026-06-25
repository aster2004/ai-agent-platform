import axios from 'axios'
import { message } from 'ant-design-vue'
import { EXPIRED_MSG, handleSessionExpired } from '@/utils/authSession'

// 通用axios实例（全模块共用，适配7人所有业务）
const request = axios.create({
    // 开发环境走 vite proxy：URL 使用 /api/... 前缀，baseURL 留空
    baseURL: import.meta.env.VITE_API_URL || import.meta.env.VITE_API_BASE_URL || '',
    timeout: 20000
})

// 接口白名单：无需携带Token（api.md 1.3.2）
const tokenWhiteList: string[] = [
    '/api/user/register',
    '/api/user/login',
    '/api/app/featured'
]
// 动态匹配预览接口
const isPreviewUrl = (url: string) => /^\/api\/app\/\d+\/preview$/.test(url)

// ========== 请求拦截器 ==========
request.interceptors.request.use(
    (config) => {
        const url = config.url || ''
        const skipToken = tokenWhiteList.some(item => url.startsWith(item)) || isPreviewUrl(url)
        if (!skipToken) {
            // Mock开发环境兜底token，生产环境后端自动校验真实JWT
            const token = localStorage.getItem('token') || 'dev-mock-token'
            if (token) config.headers.Authorization = `Bearer ${token}`
        }
        return config
    },
    (error) => {
        message.error('请求参数异常')
        return Promise.reject(error)
    }
)

// ========== 响应拦截器（修复TS类型错误 + 严格对齐api.md错误码规范） ==========
request.interceptors.response.use(
    (res) => {
        // 类型断言：把headers值强制转为string，解决TS2339报错
        const contentType = (res.headers['content-type'] || '') as string
        // 文件二进制流直接返回原始响应，不解析JSON（下载接口专用）
        if (!contentType.includes('application/json')) return res
        const data = res.data
        if (data.code !== 200) {
            message.error(data.message || '接口请求失败')
            return Promise.reject(data)
        }
        return data
    },
    (err) => {
        const response = err.response
        // 无网络/跨域
        if (!response) {
            message.error('网络异常，请检查后端服务')
            return Promise.reject(err)
        }
        const { status, data } = response
        // 401 Token 过期或未登录
        if (status === 401) {
            handleSessionExpired(data?.message || EXPIRED_MSG)
            return Promise.reject(err)
        }
        // 403 权限不足
        if (status === 403) {
            message.error('当前账号无该操作权限')
            return Promise.reject(err)
        }
        // 404 资源不存在（api.md规范）
        if (status === 404) {
            message.error('请求资源不存在')
            return Promise.reject(err)
        }
        // 500 服务器异常
        if (status === 500) {
            message.error('服务器繁忙，请稍后重试')
            return Promise.reject(err)
        }
        // 其余400等错误展示后端message
        const msg = data?.message || '请求失败'
        message.error(msg)
        return Promise.reject(err)
    }
)

// ========== 统一泛型请求封装，简化各api文件代码 ==========
export function get<T>(url: string, params?: Record<string, any>) {
    return request.get<T>(url, { params })
}

export function post<T>(url: string, data?: Record<string, any>) {
    return request.post<T>(url, data)
}

export function put<T>(url: string, data?: Record<string, any>) {
    return request.put<T>(url, data)
}

export function del<T>(url: string, params?: Record<string, any>) {
    return request.delete<T>(url, { params })
}

// 通用分页工具（api.md 1.5）
type PageParams = Record<string, any> & {
    pageNum?: number
    pageSize?: number
}
export function getPage<T>(url: string, params: PageParams = {}) {
    const { pageNum = 1, pageSize = 1, ...rest } = params
    return get<T>(url, { pageNum, pageSize, ...rest })
}

// ========== SSE流式工具（完全遵循api.md 1.11） ==========
export function chatSSE<T>(
    url: string,
    params: Record<string, any>,
    onMessage: (payload: T) => void
): Promise<ReadableStreamDefaultReader | null> {
    const baseUrl = import.meta.env.VITE_API_URL
    const fullUrl = `${baseUrl}${url}`
    const token = localStorage.getItem('token') || 'dev-mock-token'

    return fetch(fullUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
        },
        body: JSON.stringify(params)
    }).then(async (res) => {
        // 流式接口HTTP错误处理
        if (!res.ok) {
            const errJson = await res.json().catch(() => ({}))
            if (res.status === 401) {
                handleSessionExpired(errJson.message || EXPIRED_MSG)
                return Promise.reject(errJson)
            }
            message.error(errJson.message || '流式连接失败')
            return Promise.reject(errJson)
        }
        const reader = res.body?.getReader()
        if (!reader) return null
        const decoder = new TextDecoder('utf-8')
        let buffer = ''
        while (true) {
            const { done, value } = await reader.read()
            if (done) break
            buffer += decoder.decode(value, { stream: true })
            const chunks = buffer.split('\n\n')
            buffer = chunks.pop() || ''
            for (const chunk of chunks) {
                if (!chunk.trim()) continue
                const jsonRaw = chunk.replace(/^data:\s*/, '')
                try {
                    const result = JSON.parse(jsonRaw) as T
                    if ((result as { code: number }).code !== 200) {
                        message.error((result as { message: string }).message)
                        if ((result as { data?: { type: string } }).data?.type === 'error') {
                            reader.cancel()
                            return reader
                        }
                    }
                    onMessage(result)
                } catch (e) {
                    console.warn('SSE分片JSON解析失败', chunk, e)
                }
            }
        }
        return reader
    })
}

export default request
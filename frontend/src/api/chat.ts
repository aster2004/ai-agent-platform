import request from '@/utils/request'
import type {
  ChatSaveReq,
  ChatSession,
  ChatHistoryRes,
  ChatSaveVO,
  ChatMemoryMessage,
} from '@/types/chat'
import type { PageResult, Result } from '@/types/common'

type ApiResult<T> = Result<T>

/**
 * GET /api/chat/sessions
 */
export async function getSessionList(appId?: number): Promise<ApiResult<ChatSession[]>> {
  const res = await request.get<any, ApiResult<PageResult<ChatSession>>>('/api/chat/sessions', {
    params: { appId, page: 1, size: 50 },
  })
  return {
    code: res.code,
    message: res.message,
    data: res.data?.content ?? [],
  }
}

/**
 * GET /api/chat/history
 */
export function getHistoryMsg(
  sessionId: number,
  cursor?: number,
  size = 50,
): Promise<ApiResult<ChatHistoryRes>> {
  return request.get<any, ApiResult<ChatHistoryRes>>('/api/chat/history', {
    params: { sessionId, cursor, size },
  })
}

/**
 * POST /api/chat/save
 */
export function saveChatMessage(params: ChatSaveReq): Promise<ApiResult<ChatSaveVO>> {
  return request.post<any, ApiResult<ChatSaveVO>>('/api/chat/save', params)
}

export function saveAiMessage(
  sessionId: number,
  appId: number | null,
  content: string,
): Promise<ApiResult<ChatSaveVO>> {
  return saveChatMessage({
    sessionId,
    appId,
    messageType: 'ai',
    content,
  })
}

/**
 * PUT /api/chat/session/{id}
 */
export function renameSession(sessionId: number, title: string): Promise<ApiResult<null>> {
  return request.put<any, ApiResult<null>>(`/api/chat/session/${sessionId}`, { title })
}

/**
 * DELETE /api/chat/message/{id}
 */
export function deleteMessage(messageId: number): Promise<ApiResult<null>> {
  return request.delete<any, ApiResult<null>>(`/api/chat/message/${messageId}`)
}

/**
 * DELETE /api/chat/session/{id}
 */
export function deleteSession(sessionId: number): Promise<ApiResult<null>> {
  return request.delete<any, ApiResult<null>>(`/api/chat/session/${sessionId}`)
}

/**
 * POST /api/chat/session
 */
export function createSession(
  _userId?: number,
  appId?: number | null,
  _title?: string,
): Promise<ApiResult<ChatSession>> {
  return request.post<any, ApiResult<ChatSession>>('/api/chat/session', { appId })
}

/**
 * GET /api/chat/memory/{sessionId}（联调/调试用）
 */
export function getChatMemory(sessionId: number): Promise<ApiResult<ChatMemoryMessage[]>> {
  return request.get<any, ApiResult<ChatMemoryMessage[]>>(`/api/chat/memory/${sessionId}`)
}

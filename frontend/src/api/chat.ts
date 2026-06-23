// 移除 renameMockSession
import { getMockSession, getMockMsg, sendMockMsg, saveMockAiMsg, deleteMockSession, createMockSession } from '@/mock/chat'
import type { ChatSaveReq, ChatSession, ChatMessage, ChatHistoryRes } from '@/types/chat'

/**
 * 7.1 获取当前用户全部会话
 * GET /api/chat/session/list
 */
export function getSessionList(): Promise<{ code: number; message: string; data: ChatSession[] }> {
    return getMockSession()
}

/**
 * 7.3 根据会话ID获取历史消息 游标分页
 * GET /api/chat/history
 */
export function getHistoryMsg(sessionId: number): Promise<{ code: number; message: string; data: ChatHistoryRes }> {
    console.log('查询会话id：', sessionId)
    return getMockMsg(sessionId)
}

/**
 * 7.2 发送消息 / 新建会话共用接口 POST /api/chat/save
 */
export function saveChatMessage(params: ChatSaveReq): Promise<{ code: number; message: string; data: ChatMessage }> {
    return sendMockMsg(params.content, params.sessionId!, params.appId)
}

export function saveAiMessage(sessionId: number, appId: number | null, content: string): Promise<{ code: number; message: string; data: ChatMessage }> {
    return saveMockAiMsg(sessionId, appId, content)
}

/**
 * 7.4 删除会话 DELETE /api/chat/session/{id}
 */
export function deleteSession(sessionId: number): Promise<{ code: number; message: string; data: null }> {
    return deleteMockSession(sessionId)
}

/**
 * 新建会话接口
 */
export function createSession(userId: number, appId: number | null, title: string) {
    return createMockSession(userId, appId, title)
}
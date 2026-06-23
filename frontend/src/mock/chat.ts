// 移除 renameMockSession
import type { ChatSession, ChatMessage, ChatHistoryRes } from '@/types/chat'

export let mockSessionList: ChatSession[] = [
    {
        id: 1,
        userId: 1,
        appId: 1,
        sessionTitle: '登录页面开发',
        lastMessagePreview: '帮我写一个简洁的登录页面HTML',
        messageCount: 2,
        lastMessageTime: '2026-06-20 14:22:00',
        createTime: '2026-06-20 14:20:00',
        updateTime: '2026-06-20 14:22:00'
    },
    {
        id: 2,
        userId: 1,
        appId: null,
        sessionTitle: '首页UI修改需求',
        lastMessagePreview: null,
        messageCount: 0,
        lastMessageTime: null,
        createTime: '2026-06-21 09:10:00',
        updateTime: '2026-06-21 09:10:00'
    }
]

const sessionMsgMap = new Map<number, ChatMessage[]>()
sessionMsgMap.set(1, [
    {
        id: 1,
        sessionId: 1,
        appId: 1,
        messageType: 'user',
        content: '帮我写一个简洁的登录页面HTML',
        createTime: '2026-06-20 14:20:00',
        updateTime: '2026-06-20 14:20:00',
        isDeleted: 0
    },
    {
        id: 2,
        sessionId: 1,
        appId: 1,
        messageType: 'ai',
        content: '<!DOCTYPE html><html><head><meta charset="UTF-8"><title>登录页</title></head><body></body></html>',
        createTime: '2026-06-20 14:22:00',
        updateTime: '2026-06-20 14:22:00',
        isDeleted: 0
    }
])
sessionMsgMap.set(2, [])

function formatNow(): string {
    const d = new Date()
    const pad = (n: number) => n.toString().padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function getMockSession() {
    return Promise.resolve({
        code: 200,
        message: 'success',
        data: mockSessionList
    })
}

export function getMockMsg(sessionId: number): Promise<{ code: number; message: string; data: ChatHistoryRes }> {
    if (!sessionMsgMap.has(sessionId)) {
        sessionMsgMap.set(sessionId, [])
    }
    return Promise.resolve({
        code: 200,
        message: 'success',
        data: {
            list: sessionMsgMap.get(sessionId)!.filter(m => m.isDeleted === 0),
            nextCursor: null,
            hasMore: false
        }
    })
}

export function sendMockMsg(content: string, sessionId: number, appId: number | null): Promise<{ code: number; message: string; data: ChatMessage }> {
    if (!sessionMsgMap.has(sessionId)) {
        sessionMsgMap.set(sessionId, [])
    }
    const list = sessionMsgMap.get(sessionId)!
    const now = formatNow()
    const newUserMsg: ChatMessage = {
        id: Date.now(),
        sessionId,
        appId,
        messageType: 'user',
        content,
        createTime: now,
        updateTime: now,
        isDeleted: 0
    }
    const newAIMsg: ChatMessage = {
        id: Date.now() + 1,
        sessionId,
        appId,
        messageType: 'ai',
        content: `AI已收到你的需求：「${content}」，这里是模拟生成的代码示例`,
        createTime: now,
        updateTime: now,
        isDeleted: 0
    }
    list.push(newUserMsg, newAIMsg)

    const targetSession = mockSessionList.find(s => s.id === sessionId)
    if (targetSession) {
        targetSession.messageCount = list.length
        targetSession.lastMessagePreview = content.length > 20 ? content.slice(0, 20) + '...' : content
        targetSession.lastMessageTime = now
        targetSession.updateTime = now
    }

    return Promise.resolve({
        code: 200,
        message: 'success',
        data: newUserMsg
    })
}

export function deleteMockSession(sessionId: number): Promise<{ code: number; message: string; data: null }> {
    const idx = mockSessionList.findIndex(s => s.id === sessionId)
    if (idx > -1) mockSessionList.splice(idx, 1)
    sessionMsgMap.delete(sessionId)
    return Promise.resolve({
        code: 200,
        message: 'success',
        data: null
    })
}

// 适配api入参：接收对象形式入参（兼容原调用方式）
export function createMockSession(userId: number, appId: number | null, title: string): Promise<{ code: number; message: string; data: ChatSession }> {
    const now = formatNow()
    const newSession: ChatSession = {
        id: Date.now(),
        userId,
        appId,
        sessionTitle: title,
        lastMessagePreview: null,
        messageCount: 0,
        lastMessageTime: null,
        createTime: now,
        updateTime: now
    }
    mockSessionList.unshift(newSession)
    sessionMsgMap.set(newSession.id, [])
    return Promise.resolve({
        code: 200,
        message: 'success',
        data: newSession
    })
}
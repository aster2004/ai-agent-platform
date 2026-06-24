/** 会话 VO，对齐后端 ChatSessionVO */
export interface ChatSession {
    id: number
    userId?: number
    appId: number | null
    sessionTitle: string | null
    lastMessagePreview: string | null
    messageCount: number
    lastMessageTime: string | null
    createTime: string
    updateTime?: string
}

/** 消息 VO，对齐后端 ChatMessageVO */
export interface ChatMessage {
    id: number
    sessionId: number
    appId?: number | null
    messageType: 'user' | 'ai'
    content: string
    createTime: string
    updateTime?: string
    isDeleted?: number
}

/** POST /api/chat/save 返回体 */
export interface ChatSaveVO {
    sessionId: number
    messageId: number
}

/** GET /api/chat/memory/{sessionId} */
export interface ChatMemoryMessage {
    role: string
    content: string
}

/** 聊天历史游标分页返回体（api.md标准结构） */
export interface ChatHistoryRes {
    list: ChatMessage[]
    nextCursor: number | null
    hasMore: boolean
}

/** 发送消息请求体 对应POST /api/chat/save */
export interface ChatSaveReq {
    sessionId: number | null
    appId: number | null
    messageType: 'user' | 'ai'
    content: string
}

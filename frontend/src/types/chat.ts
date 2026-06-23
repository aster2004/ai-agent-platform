/** 会话 chat_session 完全对齐数据库 */
export interface ChatSession {
    id: number
    userId: number
    appId: number | null
    sessionTitle: string | null
    lastMessagePreview: string | null
    messageCount: number
    lastMessageTime: string | null
    createTime: string
    updateTime: string
    // 移除 isTop 字段
}

/** 消息 chat_message 完全对齐数据库 */
export interface ChatMessage {
    id: number
    sessionId: number
    appId: number | null
    messageType: 'user' | 'ai'
    content: string
    createTime: string
    updateTime: string
    isDeleted: number
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
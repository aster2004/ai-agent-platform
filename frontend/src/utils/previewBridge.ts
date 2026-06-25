/** iframe 预览桥接消息类型（与 PreviewBridgeInjector 注入脚本对应） */
export const PREVIEW_BRIDGE_SOURCE = 'ai-agent-preview'

export type PreviewBridgeMessageType =
  | 'preview-error'
  | 'preview-empty'
  | 'preview-ready'
  | 'preview-console'

export interface PreviewBridgeMessage {
  source: typeof PREVIEW_BRIDGE_SOURCE
  type: PreviewBridgeMessageType
  message?: string
  filename?: string
  lineno?: number
  level?: 'log' | 'warn' | 'error'
  nodes?: number
  interactive?: number
  appEmpty?: boolean
  reason?: string
}

export interface PreviewConsoleEntry {
  id: number
  level: 'log' | 'warn' | 'error'
  message: string
  time: string
}

export function isPreviewBridgeMessage(data: unknown): data is PreviewBridgeMessage {
  if (!data || typeof data !== 'object') return false
  const msg = data as Record<string, unknown>
  return msg.source === PREVIEW_BRIDGE_SOURCE && typeof msg.type === 'string'
}

import type { CodeGenParams, CodeGenPageVO, CodeGenVO, WorkflowParams, WorkflowResult } from '@/types/codegen'
import type { Result } from '@/types/common'
import request from '@/utils/request'

function streamHeaders(): HeadersInit {
  const token = localStorage.getItem('token') || 'dev-mock-token'
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  }
}

/** SSE 流式接口直连后端，绕过 Vite 代理（避免 Invalid header token） */
function streamApiUrl(path: string): string {
  const base = import.meta.env.VITE_STREAM_BASE || (import.meta.env.DEV ? 'http://localhost:8080' : '')
  return `${base}${path}`
}

export function generateCode(data: CodeGenParams) {
  return request.post<any, Result<CodeGenVO>>('/api/codegen/generate', data)
}

export function generateCodeStream(data: CodeGenParams, signal?: AbortSignal): Promise<Response> {
  return fetch(streamApiUrl('/api/codegen/stream'), {
    method: 'POST',
    headers: streamHeaders(),
    body: JSON.stringify(data),
    signal,
  })
}

export function getRecordList(pageNum = 1, pageSize = 10) {
  return request.get<any, Result<CodeGenPageVO>>('/api/codegen/record/list', {
    params: { pageNum, pageSize },
  })
}

export function executeWorkflow(data: WorkflowParams) {
  return request.post<any, Result<WorkflowResult>>('/api/codegen/workflow', data)
}

export function executeWorkflowStream(data: WorkflowParams, signal?: AbortSignal): Promise<Response> {
  return fetch(streamApiUrl('/api/codegen/workflow/stream'), {
    method: 'POST',
    headers: streamHeaders(),
    body: JSON.stringify(data),
    signal,
  })
}

/** 深度分析阶段一：生成需求文档 */
export function analyzeWorkflowStream(data: WorkflowParams, signal?: AbortSignal): Promise<Response> {
  return fetch(streamApiUrl('/api/codegen/workflow/analyze/stream'), {
    method: 'POST',
    headers: streamHeaders(),
    body: JSON.stringify(data),
    signal,
  })
}

/** 更新需求文档 */
export function updateWorkflowPrd(generateId: number, prdContent: string) {
  return request.put<any, Result<WorkflowResult>>(`/api/codegen/workflow/${generateId}/prd`, {
    prdContent,
  })
}

/** 深度分析阶段二：确认后生成应用（可携带 PRD 兜底） */
export function continueWorkflowStream(
  generateId: number,
  options?: { prdContent?: string; summary?: string },
  signal?: AbortSignal,
): Promise<Response> {
  const body = options?.prdContent || options?.summary
    ? JSON.stringify({
        prdContent: options.prdContent,
        summary: options.summary,
      })
    : undefined
  return fetch(streamApiUrl(`/api/codegen/workflow/${generateId}/continue/stream`), {
    method: 'POST',
    headers: streamHeaders(),
    body,
    signal,
  })
}

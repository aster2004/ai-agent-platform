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

export function generateCode(data: CodeGenParams) {
  return request.post<any, Result<CodeGenVO>>('/api/codegen/generate', data)
}

export function generateCodeStream(data: CodeGenParams): Promise<Response> {
  return fetch('/api/codegen/stream', {
    method: 'POST',
    headers: streamHeaders(),
    body: JSON.stringify(data),
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

export function executeWorkflowStream(data: WorkflowParams): Promise<Response> {
  return fetch('/api/codegen/workflow/stream', {
    method: 'POST',
    headers: streamHeaders(),
    body: JSON.stringify(data),
  })
}

/** 深度分析阶段一：生成需求文档 */
export function analyzeWorkflowStream(data: WorkflowParams): Promise<Response> {
  return fetch('/api/codegen/workflow/analyze/stream', {
    method: 'POST',
    headers: streamHeaders(),
    body: JSON.stringify(data),
  })
}

/** 更新需求文档 */
export function updateWorkflowPrd(generateId: number, prdContent: string) {
  return request.put<any, Result<WorkflowResult>>(`/api/codegen/workflow/${generateId}/prd`, {
    prdContent,
  })
}

/** 深度分析阶段二：确认后生成应用 */
export function continueWorkflowStream(generateId: number): Promise<Response> {
  return fetch(`/api/codegen/workflow/${generateId}/continue/stream`, {
    method: 'POST',
    headers: streamHeaders(),
  })
}

import type { CodeGenParams, CodeGenVO, WorkflowParams, WorkflowResult } from '@/types/codegen'
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
  return request.post<any, Result<CodeGenVO>>('/codegen/generate', data)
}

export function generateCodeStream(data: CodeGenParams): Promise<Response> {
  return fetch('/api/codegen/stream', {
    method: 'POST',
    headers: streamHeaders(),
    body: JSON.stringify(data),
  })
}

export function executeWorkflow(data: WorkflowParams) {
  return request.post<any, Result<WorkflowResult>>('/codegen/workflow', data)
}

export function executeWorkflowStream(data: WorkflowParams): Promise<Response> {
  return fetch('/api/codegen/workflow/stream', {
    method: 'POST',
    headers: streamHeaders(),
    body: JSON.stringify(data),
  })
}
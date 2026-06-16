import type { CodeGenParams } from '@/types/codegen'
import type { Result } from '@/types/common'
import request from '@/utils/request'

export function generateCode(data: CodeGenParams) {
  return request.post<any, Result<string>>('/codegen', data)
}

export function generateCodeStream(data: CodeGenParams): Promise<Response> {
  return fetch('/api/codegen/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
  })
}

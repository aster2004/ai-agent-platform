import { EXPIRED_MSG, handleSessionExpired } from '@/utils/authSession'

export async function readSseStream(
  response: Response,
  onEvent: (eventName: string, data: string) => void,
) {
  if (!response.ok) {
    if (response.status === 401) {
      handleSessionExpired(EXPIRED_MSG)
    }
    throw new Error(response.status === 401 ? EXPIRED_MSG : `流式请求失败 (${response.status})`)
  }

  const reader = response.body?.getReader()
  const decoder = new TextDecoder()
  if (!reader) throw new Error('无法读取流式响应')

  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const chunks = buffer.split('\n\n')
    buffer = chunks.pop() ?? ''
    for (const chunk of chunks) parseSseChunk(chunk, onEvent)
  }
  if (buffer.trim()) parseSseChunk(buffer, onEvent)
}

function parseSseChunk(chunk: string, onEvent: (eventName: string, data: string) => void) {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of chunk.split('\n')) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim()
    if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
  }
  if (dataLines.length) onEvent(eventName, dataLines.join('\n'))
}

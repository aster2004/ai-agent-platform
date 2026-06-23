import type { CodeFile, WorkflowStepCode } from '@/types/codegen'

export interface ParsedAiMessage {
  type: 'workflow' | 'code' | 'text'
  summary?: string
  strategy?: string
  validated?: boolean
  error?: string
  durationMs?: number
  activeStep?: WorkflowStepCode
  stepDescriptions?: Partial<Record<WorkflowStepCode, string>>
  files: CodeFile[]
  lang?: string
  plainText?: string
}

const WORKFLOW_MARKER = '<!--AI_WORKFLOW:'
const CODE_MARKER = '<!--AI_CODE:'

export function buildWorkflowContent(state: {
  summary?: string
  strategy?: string
  validated?: boolean
  error?: string
  durationMs?: number
  activeStep?: WorkflowStepCode
  stepDescriptions?: Partial<Record<WorkflowStepCode, string>>
  files: CodeFile[]
}): string {
  const meta = {
    type: 'workflow',
    summary: state.summary,
    strategy: state.strategy,
    validated: state.validated,
    error: state.error,
    durationMs: state.durationMs,
    activeStep: state.activeStep ?? 'done',
    stepDescriptions: state.stepDescriptions ?? {},
    files: state.files,
  }
  return `${WORKFLOW_MARKER}${JSON.stringify(meta)}-->`
}

export function buildCodeContent(code: string, lang = 'html'): string {
  const meta = { type: 'code', lang }
  return `${CODE_MARKER}${JSON.stringify(meta)}-->\n${code}`
}

function detectLang(pathOrCode: string): string {
  if (pathOrCode.includes('.')) {
    const ext = pathOrCode.split('.').pop()?.toLowerCase()
    const map: Record<string, string> = {
      vue: 'vue',
      ts: 'typescript',
      js: 'javascript',
      html: 'html',
      css: 'css',
      json: 'json',
    }
    return map[ext ?? ''] ?? ext ?? 'text'
  }
  if (pathOrCode.includes('<html') || pathOrCode.includes('<!DOCTYPE')) return 'html'
  if (pathOrCode.includes('export default') || pathOrCode.includes('<template>')) return 'vue'
  return 'text'
}

export function parseAiMessage(content: string): ParsedAiMessage {
  if (content.startsWith(WORKFLOW_MARKER)) {
    const end = content.indexOf('-->', WORKFLOW_MARKER.length)
    if (end > 0) {
      try {
        const meta = JSON.parse(content.slice(WORKFLOW_MARKER.length, end))
        return {
          type: 'workflow',
          summary: meta.summary,
          strategy: meta.strategy,
          validated: meta.validated,
          error: meta.error,
          durationMs: meta.durationMs,
          activeStep: meta.activeStep,
          stepDescriptions: meta.stepDescriptions,
          files: meta.files ?? [],
        }
      } catch {
        // fall through
      }
    }
  }

  if (content.startsWith(CODE_MARKER)) {
    const end = content.indexOf('-->', CODE_MARKER.length)
    if (end > 0) {
      try {
        const meta = JSON.parse(content.slice(CODE_MARKER.length, end))
        const code = content.slice(end + 3).replace(/^\n/, '')
        return {
          type: 'code',
          lang: meta.lang ?? detectLang(code),
          files: [{ path: meta.lang ?? 'code', content: code }],
          plainText: code,
        }
      } catch {
        // fall through
      }
    }
  }

  // Legacy text format
  const files: CodeFile[] = []
  let summary: string | undefined
  let strategy: string | undefined
  let validated: boolean | undefined

  const fileParts = content.split(/\n--- (.+?) ---\n/)
  if (fileParts.length > 1) {
    const metaText = fileParts[0]
    for (const line of metaText.split('\n')) {
      if (line.startsWith('需求摘要：')) summary = line.slice('需求摘要：'.length)
      if (line.startsWith('策略：')) strategy = line.slice('策略：'.length)
      if (line.startsWith('校验：')) validated = line.includes('通过')
    }
    for (let i = 1; i < fileParts.length; i += 2) {
      if (fileParts[i] && fileParts[i + 1]) {
        files.push({ path: fileParts[i], content: fileParts[i + 1].trim() })
      }
    }
    return {
      type: files.length ? 'workflow' : 'text',
      summary,
      strategy,
      validated,
      files,
      plainText: metaText.trim(),
    }
  }

  const isCode =
    content.includes('<html') ||
    content.includes('<!DOCTYPE') ||
    content.includes('<template>') ||
    content.includes('function ') ||
    content.includes('export ')

  if (isCode) {
    const lang = detectLang(content)
    return {
      type: 'code',
      lang,
      files: [{ path: lang, content: content.trim() }],
      plainText: content,
    }
  }

  return { type: 'text', files: [], plainText: content }
}

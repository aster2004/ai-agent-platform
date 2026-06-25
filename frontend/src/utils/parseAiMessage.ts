import type { CodeFile, WorkflowStepCode, WorkflowTask, WorkflowPhase } from '@/types/codegen'

export interface ParsedAiMessage {
  type: 'workflow' | 'code' | 'text'
  phase?: WorkflowPhase
  generateId?: number
  summary?: string
  prdContent?: string
  strategy?: string
  validated?: boolean
  error?: string
  durationMs?: number
  activeStep?: WorkflowStepCode
  stepDescriptions?: Partial<Record<WorkflowStepCode, string>>
  tasks: WorkflowTask[]
  files: CodeFile[]
  lang?: string
  plainText?: string
}

const WORKFLOW_MARKER = '<!--AI_WORKFLOW:'
const CODE_MARKER = '<!--AI_CODE:'

export function buildWorkflowContent(state: {
  phase?: WorkflowPhase
  generateId?: number
  summary?: string
  prdContent?: string
  strategy?: string
  validated?: boolean
  error?: string
  durationMs?: number
  activeStep?: WorkflowStepCode
  stepDescriptions?: Partial<Record<WorkflowStepCode, string>>
  tasks?: WorkflowTask[]
  files: CodeFile[]
}): string {
  const meta = {
    type: 'workflow',
    phase: state.phase ?? 'done',
    generateId: state.generateId,
    summary: state.summary,
    prdContent: state.prdContent,
    strategy: state.strategy,
    validated: state.validated,
    error: state.error,
    durationMs: state.durationMs,
    activeStep: state.activeStep ?? 'done',
    stepDescriptions: state.stepDescriptions ?? {},
    tasks: state.tasks ?? [],
    files: state.files ?? [],
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

function extractMarkerJson(content: string, marker: string): string | null {
  const trimmed = content.trimStart()
  let jsonStart = -1

  if (content.startsWith(marker)) {
    jsonStart = marker.length
  } else if (trimmed.startsWith(marker.replace('<!--', '<!-- '))) {
    jsonStart = content.indexOf(trimmed) + trimmed.indexOf(':') + 1
  } else {
    return null
  }

  const end = content.lastIndexOf('-->')
  if (end <= jsonStart) return null
  return content.slice(jsonStart, end).trim()
}

export function parseAiMessage(content: string): ParsedAiMessage {
  const workflowJson = extractMarkerJson(content, WORKFLOW_MARKER)
  if (workflowJson) {
    try {
      const meta = JSON.parse(workflowJson)
      return {
        type: 'workflow',
        phase: meta.phase,
        generateId: meta.generateId,
        summary: meta.summary,
        prdContent: meta.prdContent,
        strategy: meta.strategy,
        validated: meta.validated,
        error: meta.error,
        durationMs: meta.durationMs,
        activeStep: meta.activeStep,
        stepDescriptions: meta.stepDescriptions,
        tasks: meta.tasks ?? [],
        files: meta.files ?? [],
      }
    } catch {
      // fall through
    }
  }

  const codeJson = extractMarkerJson(content, CODE_MARKER)
  if (codeJson) {
    try {
      const meta = JSON.parse(codeJson)
      const end = content.lastIndexOf('-->')
      const code = content.slice(end + 3).replace(/^\n/, '')
      return {
        type: 'code',
        lang: meta.lang ?? detectLang(code),
        tasks: [],
        files: [{ path: meta.lang ?? 'code', content: code }],
        plainText: code,
      }
    } catch {
      // fall through
    }
  }

  if (content.includes(WORKFLOW_MARKER) || content.includes('AI_WORKFLOW')) {
    return {
      type: 'workflow',
      phase: 'await_confirm',
      tasks: [],
      files: [],
      plainText: '需求分析已完成，请点击「立即创作」继续生成应用。',
    }
  }

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
      tasks: [],
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
      tasks: [],
      files: [{ path: lang, content: content.trim() }],
      plainText: content,
    }
  }

  return { type: 'text', tasks: [], files: [], plainText: content }
}

export interface CodeGenParams {
  prompt: string
  appId?: number
  sessionId?: number
  generateType?: 'HTML' | 'VUE' | 'MULTI_FILE' | 'WORKFLOW'
}

export interface CodeGenVO {
  id?: number
  codeContent?: string
  generateStatus?: number
  modelName?: string
  costTokens?: number
  duration?: number
}

export type GenerationOutput = 'stream' | 'sync'

export interface CodeFile {
  path: string
  content: string
}

export type WorkflowStepCode = 'analyze' | 'strategy' | 'generate' | 'validate' | 'done'

export interface WorkflowParams {
  prompt: string
  appId?: number
  sessionId?: number
  generateType?: 'HTML' | 'VUE' | 'MULTI_FILE' | 'WORKFLOW'
}

export interface WorkflowResult {
  generateId?: number
  summary?: string
  strategy?: string
  generateType?: string
  validated?: boolean
  error?: string
  codeFiles?: CodeFile[]
  durationMs?: number
}

export interface WorkflowStepEvent {
  type: 'step' | 'done' | 'error'
  step?: WorkflowStepCode
  label?: string
  message?: string
  data?: WorkflowResult
}

export type CodeGenMode = 'quick' | 'workflow'

export interface WorkflowMessageState {
  running: boolean
  activeStep: WorkflowStepCode
  stepDescriptions: Partial<Record<WorkflowStepCode, string>>
  failed: boolean
  summary?: string
  strategy?: string
  validated?: boolean
  files: CodeFile[]
  durationMs?: number
  error?: string
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  kind: 'text' | 'workflow'
  workflow?: WorkflowMessageState
}
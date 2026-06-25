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

export type WorkflowStepCode =
  | 'analyze'
  | 'prd'
  | 'prd_ready'
  | 'strategy'
  | 'generate'
  | 'validate'
  | 'done'

export type WorkflowPhase = 'analyzing' | 'await_confirm' | 'generating' | 'done'

export interface WorkflowTask {
  type: 'skill_call' | 'command' | 'save_file' | 'read_file' | string
  label: string
  detail?: string
}

export interface WorkflowParams {
  prompt: string
  appId?: number
  sessionId?: number
  generateType?: 'HTML' | 'VUE' | 'MULTI_FILE' | 'WORKFLOW'
}

export interface WorkflowResult {
  generateId?: number
  phase?: string
  summary?: string
  prdContent?: string
  strategy?: string
  generateType?: string
  validated?: boolean
  error?: string
  codeFiles?: CodeFile[]
  tasks?: WorkflowTask[]
  durationMs?: number
}

export interface WorkflowStepEvent {
  type: 'step' | 'task' | 'prd_ready' | 'done' | 'error'
  step?: WorkflowStepCode
  label?: string
  message?: string
  data?: WorkflowResult | WorkflowTask
}

export type CodeGenMode = 'quick' | 'workflow'

export interface WorkflowMessageState {
  running: boolean
  phase: WorkflowPhase
  activeStep: WorkflowStepCode
  stepDescriptions: Partial<Record<WorkflowStepCode, string>>
  failed: boolean
  summary?: string
  prdContent?: string
  generateId?: number
  strategy?: string
  validated?: boolean
  files: CodeFile[]
  tasks: WorkflowTask[]
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

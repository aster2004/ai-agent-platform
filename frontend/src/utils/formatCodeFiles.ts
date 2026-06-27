import { detectFileLang } from './formatCode'
import type { CodeFile } from '@/types/codegen'

/** 将工作流返回的文件列表格式化为预览/保存用的 markdown 多文件文本 */
export function formatCodeFilesToContent(files: CodeFile[]): string {
  if (!files.length) return ''
  return files.map(file => {
    const lang = detectFileLang(file.path)
    return `## 📁 ${file.path}\n\n\`\`\`${lang}\n${file.content}\n\`\`\``
  }).join('\n\n')
}

/** 去除 PRD/摘要中的代码、HTML 页面，仅保留可读文字 */
export function sanitizePrdDisplayText(text: string): string {
  let s = (text ?? '').trim()
  if (!s) return ''

  s = s.replace(/```[\w-]*\s*\n?[\s\S]*?```/g, '').trim()
  s = s.replace(/<!DOCTYPE[\s\S]*?<\/html>/gi, '').trim()
  s = s.replace(/<html[\s\S]*?<\/html>/gi, '').trim()
  s = s.replace(/<head[\s\S]*?<\/head>/gi, '').trim()
  s = s.replace(/<style[\s\S]*?<\/style>/gi, '').trim()
  s = s.replace(/<script[\s\S]*?<\/script>/gi, '').trim()
  s = s.replace(/^\s*import\s+.+$/gm, '').trim()
  s = s.replace(/^\s*export\s+.+$/gm, '').trim()
  s = s.replace(/\n{3,}/g, '\n\n').trim()

  if (looksLikeCodeSnippet(s)) return ''
  return s
}

function looksLikeCodeSnippet(text: string): boolean {
  const t = text.trim()
  if (!t) return false
  if (t.includes('```')) return true
  if (/^<(!DOCTYPE|html|template|body|div|style|script)/i.test(t)) return true
  if (t.startsWith('<') && t.includes('</') && (t.includes('class=') || t.includes('function'))) return true
  return false
}

/** 将工作流 PRD 结果格式化为聊天区展示的需求分析（仅文字，不含代码） */
export function formatPrdSummary(data: {
  summary?: string
  prdContent?: string
  strategy?: string
  durationMs?: number
}): string {
  const summary = sanitizePrdDisplayText(data.summary ?? '')
  const prdBody = sanitizePrdDisplayText(data.prdContent ?? '')

  let content = '## 📋 需求分析完成\n\n'
  if (summary) {
    content += `**需求摘要：** ${summary}\n\n`
  }
  if (prdBody) {
    content += prdBody
    content += '\n\n'
  }
  if (data.strategy) content += `**推荐策略：** ${data.strategy}\n\n`
  if (data.durationMs) content += `---\n⏱ 耗时：${(data.durationMs / 1000).toFixed(1)}s\n`
  return content.trim()
}

/** 将深度分析工作流步骤格式化为可持久化的聊天消息 */
export function formatWorkflowLog(phaseText: string): string {
  const body = phaseText.trim()
  if (!body) return ''
  return `## 🔄 AI 工作流记录\n\n${body}`
}

/** 判断是否为可在 iframe 中直接渲染的完整 HTML 文档 */
export function isFullHtmlDocument(content: string): boolean {
  const t = (content ?? '').trim()
  return /^\s*<!DOCTYPE\s+html/i.test(t) || /^\s*<html\b/i.test(t)
}

/** 从历史/批次解析结果判断是否应恢复为可预览的多文件工作区 */
export function isRenderablePreviewProject(files: CodeFile[]): boolean {
  if (!files.length) return false
  if (files.length >= 2) return true
  if (files.some(f => f.path.endsWith('.vue') || f.path === 'preview.html' || f.path === 'package.json')) {
    return true
  }
  const only = files[0]
  return only.path.endsWith('.html') && isFullHtmlDocument(only.content)
}

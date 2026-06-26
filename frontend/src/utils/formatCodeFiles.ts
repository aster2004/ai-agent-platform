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

/** 将工作流 PRD 结果格式化为聊天区展示的简短摘要 */
export function formatPrdSummary(data: {
  summary?: string
  strategy?: string
  durationMs?: number
}): string {
  let content = '## 📋 需求分析完成\n\n'
  if (data.summary) content += `${data.summary}\n\n`
  if (data.strategy) content += `**推荐策略：** ${data.strategy}\n\n`
  if (data.durationMs) content += `---\n⏱ 耗时：${(data.durationMs / 1000).toFixed(1)}s\n`
  return content.trim()
}

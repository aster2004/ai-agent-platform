/**
 * 多文件内容拆分工具
 *
 * 问题：后端将多文件代码整体返回为一段文本（JSON 数组或 markdown 多文件格式），
 * 前端保存为一条 AI 消息 → 渲染为一个气泡框。
 *
 * 本工具在保存前将多文件内容拆分为多条消息，每条对应一个文件，
 * 使每个文件渲染为独立的气泡框。
 */

import { detectFileLang, detectCodeLang } from './formatCode'

/**
 * 将 AI 返回的完整内容拆分为多条消息内容字符串。
 * - 多文件 JSON 数组 → 每个文件一条消息
 * - Markdown 文件标题 + 代码块 → 每个文件一条消息
 * - 混合内容（文字 + 多个代码块）→ 文字一条 + 每个代码块一条
 * - 单文件 / 纯文本 → 原样返回（不拆分）
 *
 * 每条消息内容格式化为 ```lang\ncode\n``` 或带文件头的 markdown，
 * 保证 AiStreamMessage 能正确渲染为代码卡片。
 */
export function splitAiContent(raw: string): string[] {
  if (!raw?.trim()) return []

  const trimmed = raw.trim()

  // ---- 策略1：JSON 数组 [{path, content}] ----
  const jsonFiles = tryExtractJsonFiles(trimmed)
  if (jsonFiles && jsonFiles.length > 1) {
    return jsonFiles.map(f => formatFileMessage(f.path, f.content))
  }

  // ---- 策略2：Markdown ## 文件标题 + 代码块（≥2个文件） ----
  const mdFiles = tryExtractMarkdownFiles(trimmed)
  if (mdFiles && mdFiles.length > 1) {
    // 检查标题之前是否有文字说明
    const firstHeaderIdx = trimmed.search(/##\s+(?:📁\s*)?[^\n]+?\s*\n\s*```/)
    const intro = firstHeaderIdx > 0 ? trimmed.slice(0, firstHeaderIdx).trim() : ''

    const parts: string[] = []
    if (intro) parts.push(intro)
    parts.push(...mdFiles.map(f => formatFileMessage(f.path, f.content)))
    return parts
  }

  // ---- 策略3：混合内容（文字说明 + 多个 markdown 代码块） ----
  // 至少 2 个代码块才拆分
  const codeBlockRegex = /```(\w*)\s*\n?([\s\S]*?)```/g
  const blocks: Array<{ full: string; lang: string; code: string; idx: number }> = []
  let match: RegExpExecArray | null
  while ((match = codeBlockRegex.exec(trimmed)) !== null) {
    blocks.push({
      full: match[0],
      lang: match[1]?.trim() || detectCodeLang(match[2] || ''),
      code: (match[2] || '').replace(/\n$/, ''),
      idx: match.index,
    })
  }

  if (blocks.length >= 2) {
    const parts: string[] = []

    // 第一个代码块之前的文字说明
    if (blocks[0].idx > 0) {
      const before = trimmed.slice(0, blocks[0].idx).trim()
      if (before) parts.push(before)
    }

    for (let i = 0; i < blocks.length; i++) {
      parts.push(formatFileMessage(undefined, blocks[i].code, blocks[i].lang))

      // 代码块之间的文字说明
      if (i < blocks.length - 1) {
        const betweenStart = blocks[i].idx + blocks[i].full.length
        const betweenEnd = blocks[i + 1].idx
        const between = trimmed.slice(betweenStart, betweenEnd).trim()
        if (between) parts.push(between)
      }
    }

    // 最后一个代码块之后的文字
    const lastBlock = blocks[blocks.length - 1]
    const after = trimmed.slice(lastBlock.idx + lastBlock.full.length).trim()
    if (after) parts.push(after)

    return parts
  }

  // ---- 单文件 / 纯文本：不拆分 ----
  return [raw]
}

/**
 * 尝试提取 JSON 数组多文件格式。
 * 多层回退策略：JSON.parse → JSON 修复 → 正则提取
 */
export function tryExtractJsonFiles(text: string): Array<{ path: string; content: string }> | null {
  // 先尝试从 ```json ... ``` 中提取
  let jsonText = text
  const mdMatch = jsonText.match(/```(?:json)?\s*\n?([\s\S]*?)```/)
  if (mdMatch) jsonText = mdMatch[1].trim()

  // 定位 JSON 数组边界
  const jsonStart = jsonText.indexOf('[{')
  const jsonEnd = jsonText.lastIndexOf('}]')
  if (jsonStart < 0 || jsonEnd <= jsonStart) return null

  const candidate = jsonText.slice(jsonStart, jsonEnd + 2)

  // 策略1：直接 JSON.parse
  let parsed: any[] | null = null
  try {
    parsed = JSON.parse(candidate)
  } catch {
    // 策略2：修复常见 JSON 问题后重试
    let repaired = candidate
        .replace(/,\s*\]/g, ']')       // 移除数组尾部多余逗号
        .replace(/,\s*}/g, '}')        // 移除对象尾部多余逗号
    try {
      parsed = JSON.parse(repaired)
    } catch {
      // 策略3：正则兜底提取（从 ChatPreviewPanel 验证过的逻辑）
      return regexExtractFiles(candidate)
    }
  }

  if (!Array.isArray(parsed) || parsed.length === 0) return null

  // 校验格式
  const files: Array<{ path: string; content: string }> = []
  for (const item of parsed) {
    if (item && typeof item.path === 'string' && typeof item.content === 'string') {
      if (item.content.trim()) {
        files.push({ path: item.path, content: item.content })
      }
    }
  }
  return files.length > 0 ? files : null
}

/** 正则兜底：从原始文本中提取文件路径和内容 */
export function regexExtractFiles(text: string): Array<{ path: string; content: string }> | null {
  // 匹配每个文件条目: {"path":"...","content":"..."
  const entryRe = /\{[^{}]*"path"\s*:\s*"([^"]+)"\s*,\s*"content"\s*:\s*"/g
  const entries: Array<{ matchEnd: number; path: string }> = []
  let m: RegExpExecArray | null
  while ((m = entryRe.exec(text)) !== null) {
    entries.push({ matchEnd: m.index + m[0].length, path: m[1] })
  }
  if (entries.length === 0) return null

  const files: Array<{ path: string; content: string }> = []
  for (let i = 0; i < entries.length; i++) {
    const { matchEnd, path } = entries[i]
    let content = ''
    let inString = false
    let escaped = false

    for (let j = matchEnd; j < text.length; j++) {
      const ch = text[j]
      if (escaped) { content += ch; escaped = false; continue }
      if (ch === '\\') { content += ch; escaped = true; continue }
      if (ch === '"' && !inString) { inString = true; continue }
      if (ch === '"' && inString) {
        // 检查下一个非空白字符是否为 } 或 ,
        let k = j + 1
        while (k < text.length && /\s/.test(text[k])) k++
        if (k < text.length && (text[k] === '}' || text[k] === ',')) {
          inString = false
          break
        }
        content += ch
        continue
      }
      if (inString) { content += ch }
    }

    if (content) {
      content = content
          .replace(/\\n/g, '\n')
          .replace(/\\t/g, '\t')
          .replace(/\\r/g, '\r')
          .replace(/\\"/g, '"')
          .replace(/\\\\/g, '\\')
          .replace(/\\u[\da-fA-F]{4}/g, (u) => String.fromCharCode(parseInt(u.slice(2), 16)))
      files.push({ path, content })
    }
  }

  return files.length > 0 ? files : null
}

/**
 * 尝试提取 markdown ## 文件标题 + 代码块格式
 */
export function tryExtractMarkdownFiles(text: string): Array<{ path: string; content: string }> | null {
  const pattern = /##\s+(?:📁\s*)?([^\n]+?)\s*\n\s*```(\w*)\s*\n([\s\S]*?)```/g
  const files: Array<{ path: string; content: string }> = []
  let m: RegExpExecArray | null
  while ((m = pattern.exec(text)) !== null) {
    const rawPath = m[1].trim()
    const code = m[3]?.trim() || ''
    // 过滤非文件路径的标题
    if (!rawPath.includes('.') && !rawPath.includes('/')) continue
    if (!code) continue
    files.push({ path: rawPath, content: code })
  }
  return files.length > 0 ? files : null
}

/**
 * 将单个文件格式化为可直接渲染的消息内容
 */
function formatFileMessage(path: string | undefined, code: string, lang?: string): string {
  const l = lang || (path ? detectFileLang(path) : detectCodeLang(code))
  if (path) {
    return `## 📁 ${path}\n\n\`\`\`${l}\n${code}\n\`\`\``
  }
  return `\`\`\`${l}\n${code}\n\`\`\``
}

/**
 * 统一的文件解析器：依次尝试所有策略从原始内容中提取 CodeFile[]
 * 用于预览面板等需要直接获取结构化文件列表的场景
 */
export function parseContentToFiles(content: string): Array<{ path: string; content: string }> | null {
  if (!content?.trim()) return null
  return tryExtractJsonFiles(content) || tryExtractMarkdownFiles(content)
}
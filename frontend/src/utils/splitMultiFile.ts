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
  if (jsonFiles && jsonFiles.length >= 1) {
    // 找到所有文件条目的起始位置（含不完整的）
    const entryRe = /\{[^{}]*"path"\s*:\s*"([^"]+)"\s*,\s*"content"\s*:\s*"/g
    const entryStarts: number[] = []
    let em: RegExpExecArray | null
    while ((em = entryRe.exec(trimmed)) !== null) {
      entryStarts.push(em.index)
    }

    const parts: string[] = []

    // 首个文件前的文字说明（过滤纯 JSON 语法字符，避免 [ / [{ 等被当作有效内容）
    if (entryStarts.length > 0 && entryStarts[0] > 0) {
      const intro = trimmed.slice(0, entryStarts[0]).trim()
      if (intro && !/^[\s\[\]\{\},:"]+$/.test(intro)) parts.push(intro)
    }

    // 已完成文件 → 格式化为独立气泡内容
    parts.push(...jsonFiles.map(f => formatFileMessage(f.path, f.content)))

    // 尾随不完整内容（流式时下一个文件的开头部分）
    // 提取文件名和代码片段，格式化为规范 markdown 代码块，保证流式渲染效果
    if (jsonFiles.length < entryStarts.length) {
      const incompleteStart = entryStarts[jsonFiles.length]
      const rawEntry = trimmed.slice(incompleteStart)
      const formatted = formatPartialJsonEntry(rawEntry)
      if (formatted) parts.push(formatted)
    }

    if (parts.length >= 2) return parts
  }

  // ---- 策略2：Markdown ## 文件标题 + 代码块 ----
  const mdFiles = tryExtractMarkdownFiles(trimmed)
  if (mdFiles && mdFiles.length >= 1) {
    // 检查标题之前是否有文字说明
    const firstHeaderIdx = trimmed.search(/##\s+(?:📁\s*)?[^\n]+?\s*\n\s*```/)
    const intro = firstHeaderIdx > 0 ? trimmed.slice(0, firstHeaderIdx).trim() : ''

    // 找到最后一个已提取文件的代码块结束位置
    let lastEnd = 0
    const blockPattern = /##\s+(?:📁\s*)?[^\n]+?\s*\n\s*```[\s\S]*?```/g
    let bm: RegExpExecArray | null
    while ((bm = blockPattern.exec(trimmed)) !== null) {
      lastEnd = bm.index + bm[0].length
    }

    // 尾随内容（流式时可能是不完整的下一个文件）
    const trailing = trimmed.slice(lastEnd).trim()

    const parts: string[] = []
    if (intro) parts.push(intro)
    parts.push(...mdFiles.map(f => formatFileMessage(f.path, f.content)))
    if (trailing) parts.push(trailing)

    // 至少拆分为 2 个部分（或单文件后有尾随内容）才返回
    if (parts.length >= 2) return parts
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
  if (jsonStart < 0) return null

  // 流式场景：数组尚未闭合（}] 未到达），用正则提取已完成文件
  if (jsonEnd <= jsonStart) {
    const partial = jsonText.slice(jsonStart)
    return regexExtractFiles(partial)
  }

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
    let stringClosed = false

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
          stringClosed = true
          break
        }
        content += ch
        continue
      }
      if (inString) { content += ch }
    }

    // 仅当内容字符串正确闭合时才加入（流式时最后一个文件可能不完整）
    if (content && stringClosed) {
      // 注意：\\\\ 必须最先处理，否则 \\n 中的 \\ 会被 \\n 规则错误消费
      content = content
          .replace(/\\\\/g, '\\')
          .replace(/\\n/g, '\n')
          .replace(/\\t/g, '\t')
          .replace(/\\r/g, '\r')
          .replace(/\\"/g, '"')
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
 * 将流式中的不完整 JSON 文件条目格式化为规范 markdown 代码块
 * 输入如：{"path":"style.css","content":"body {\n  margin
 * 输出如：## 📁 style.css\n\n```css\nbody {\n  margin\n```
 */
export function formatPartialJsonEntry(rawEntry: string): string | null {
  // 提取文件名
  const pathMatch = rawEntry.match(/"path"\s*:\s*"([^"]+)"/)
  const path = pathMatch ? pathMatch[1] : 'file'

  // 提取 "content":" 之后的部分
  const contentMatch = rawEntry.match(/"content"\s*:\s*"/)
  if (!contentMatch) return null

  const codeStart = contentMatch.index! + contentMatch[0].length
  let code = rawEntry.slice(codeStart)

  // 去除尾部未闭合的 JSON 片段（末尾的反斜杠转义残余等）
  code = code.replace(/\\?$/, '')

  // JSON 转义还原（\\\\ 必须最先处理）
  code = code
      .replace(/\\\\/g, '\\')
      .replace(/\\n/g, '\n')
      .replace(/\\t/g, '\t')
      .replace(/\\r/g, '\r')
      .replace(/\\"/g, '"')

  if (!code.trim()) return null

  const lang = detectFileLang(path)
  return `## 📁 ${path}\n\n\`\`\`${lang}\n${code}\n\`\`\``
}

/**
 * 统一的文件解析器：依次尝试所有策略从原始内容中提取 CodeFile[]
 * 用于预览面板等需要直接获取结构化文件列表的场景
 */
export function parseContentToFiles(content: string): Array<{ path: string; content: string }> | null {
  if (!content?.trim()) return null
  return tryExtractJsonFiles(content) || tryExtractMarkdownFiles(content)
}
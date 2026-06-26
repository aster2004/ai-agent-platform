/**
 * 流式 Markdown 解析工具
 * 将大模型流式输出的 Markdown 文本解析为渲染段数组
 * 支持：代码块(```)、行内代码(`)、标题(#)、粗体(**)、斜体(*)、列表(-/数字.)、段落
 *
 * 多文件 JSON 数组自动转换 → 结构化 markdown 代码块
 */

import { detectFileLang, detectCodeLang } from './formatCode'

/**
 * 检测 AI 返回内容是否为多文件 JSON 数组格式（Vue/Multi-file 策略）
 * 若是，则转换为带语言标记的 markdown 代码块，让聊天框正常渲染
 * 若不是，原样返回
 *
 * 增强：支持从任意位置提取 JSON 数组（AI 常在前后加文字说明）、
 *       支持 markdown 文件标题 + 代码块模式
 */
export function normalizeAiContent(raw: string): string {
    if (!raw?.trim()) return raw
    const trimmed = raw.trim()

    // ---- 策略0：markdown 文件标题 + 代码块模式 ----
    // AI 常输出 "## index.html\n```html\n...```" 表示多文件
    if (!trimmed.startsWith('[') && !trimmed.startsWith('```')) {
        const markdownFiles = tryParseMarkdownFiles(trimmed)
        if (markdownFiles) return markdownFiles
    }

    // ---- 策略1：从任意位置提取多文件 JSON 数组 → 结构化 markdown ----
    // 先尝试从 ```json ... ``` 中提取
    let jsonText = trimmed
    const mdMatch = jsonText.match(/```(?:json)?\s*\n?([\s\S]*?)```/)
    if (mdMatch) jsonText = mdMatch[1].trim()

    // 再从任意位置定位 [{ ... }]
    const filesFromJson = tryExtractJsonFiles(jsonText)
    if (filesFromJson) return filesFromJson

    // ---- 策略2：纯 HTML / Vue 文本 → 包裹为 markdown 代码块 ----
    if (!trimmed.includes('```')) {
        if (isRawHtml(trimmed)) {
            const lang = trimmed.startsWith('<template') ? 'vue' : 'html'
            return '```' + lang + '\n' + trimmed + '\n```'
        }
    }

    // ---- 策略2b：内容中混杂了裸 HTML 代码（AI 先写文字说明再给代码） ----
    if (!trimmed.includes('```')) {
        const mixed = wrapMixedHtmlCode(trimmed)
        if (mixed) return mixed
    }

    // ---- 策略3：格式化已有代码块中的代码 ----
    let result = raw
    result = result.replace(/```(\w*)\n([\s\S]*?)```/g, (_full: string, lang: string, code: string) => {
        const l = lang?.trim() || detectCodeLang(code)
        return '```' + l + '\n' + code + '\n```'
    })

    return result
}

/**
 * 从文本任意位置提取 [{path, content}] JSON 数组
 * 解决 AI 在 JSON 前后添加解释文字导致检测失败的问题
 */
function tryExtractJsonFiles(text: string): string | null {
    const jsonStart = text.indexOf('[{')
    const jsonEnd = text.lastIndexOf('}]')
    if (jsonStart < 0 || jsonEnd <= jsonStart) return null

    const candidate = text.slice(jsonStart, jsonEnd + 2)
    try {
        const arr = JSON.parse(candidate)
        if (Array.isArray(arr) && arr.length > 0) {
            if (arr[0] && typeof arr[0].path === 'string' && typeof arr[0].content === 'string') {
                const parts: string[] = []
                for (const file of arr) {
                    const lang = detectFileLang(file.path)
                    parts.push(`## 📁 ${file.path}`)
                    parts.push('```' + lang)
                    parts.push(file.content)
                    parts.push('```')
                    parts.push('')
                }
                return parts.join('\n').trim()
            }
        }
    } catch { /* 不是有效 JSON，继续后续检测 */ }
    return null
}

/**
 * 识别 markdown 文件标题 + 代码块模式
 * AI 常用 "## 文件名\n```lang\ncode\n```" 表示多文件项目
 * 至少匹配到 2 个文件才视为有效的多文件输出
 */
function tryParseMarkdownFiles(text: string): string | null {
    // 匹配 ## 文件名（可选 📁 emoji）后紧跟代码块
    const pattern = /##\s+(?:📁\s*)?([^\n]+?)\s*\n\s*```(\w*)\s*\n([\s\S]*?)```/g
    const files: { path: string; content: string }[] = []
    let match: RegExpExecArray | null
    while ((match = pattern.exec(text)) !== null) {
        const rawPath = match[1].trim()
        const code = match[3]?.trim() || ''
        // 过滤掉非文件路径的标题（如 "需求分析"、"总结" 等不含扩展名的标题）
        if (!rawPath.includes('.') && !rawPath.includes('/')) continue
        if (!code) continue
        files.push({ path: rawPath, content: code })
    }
    if (files.length < 2) return null

    const parts: string[] = []
    for (const f of files) {
        const lang = detectFileLang(f.path)
        parts.push(`## 📁 ${f.path}`)
        parts.push('```' + lang)
        parts.push(f.content)
        parts.push('```')
        parts.push('')
    }
    return parts.join('\n').trim()
}

/** 检测是否为裸 HTML / Vue 文本（整段都是代码） */
function isRawHtml(text: string): boolean {
    const t = text.trim()
    return t.startsWith('<!DOCTYPE')
        || /^<html[\s>]/i.test(t)
        // 注意：不能用 \b，因为 AI 输出的标签名和属性之间可能缺少空格
        // 如 <divclass=""> — \b 会因 'c' 是字母而匹配失败
        || (t.startsWith('<') && /<\/[a-zA-Z][\s\S]*?>/.test(t) && /<(html|head|body|div|meta|link|style|script|title|template|h[1-6]|p|span|a|img|ul|ol|li|table|form|input|button)/i.test(t))
}

/**
 * 处理"文字说明 + 裸 HTML 代码"的混合内容
 * 例如：AI 先说"以下是代码"，然后直接贴 HTML
 * 找到代码起始位置，把后面部分包裹为代码块
 */
function wrapMixedHtmlCode(text: string): string | null {
    // 尝试在文本中找到 HTML/Vue 代码的起始位置
    const codeStartRegex = /(?:^|\n)\s*(<(!DOCTYPE\s+html|html[\s>]|template[\s>]|head[\s>]|body[\s>]|div[\s>]))/im
    // 也匹配缺少空格的标签名：<divclass, <html lang="en", 等
    const codeStartRegex2 = /(?:^|\n)\s*(<(?:html|head|body|div|template|section|nav|header|footer|main|article|aside)[a-zA-Z])/im

    const m1 = codeStartRegex.exec(text)
    const m2 = codeStartRegex2.exec(text)

    let codeStart = -1
    if (m1) codeStart = m1.index + (m1[0].length - m1[1].length) // 指向 < 的位置
    else if (m2) codeStart = m2.index + m2[0].indexOf('<')

    if (codeStart <= 0) return null // 代码在开头或没找到 → 走 isRawHtml 路径

    const before = text.slice(0, codeStart).trim()
    const code = text.slice(codeStart).trim()
    if (!code || code.length < 10) return null

    const lang = code.startsWith('<template') ? 'vue' : 'html'

    const parts: string[] = []
    if (before) parts.push(before)
    parts.push('')
    parts.push('```' + lang)
    parts.push(code)
    parts.push('```')
    return parts.join('\n')
}

export interface MarkdownSegment {
    type: 'text' | 'code'
    html?: string        // text 段：已转换的行内 HTML
    lang?: string        // code 段：语言标识
    code?: string        // code 段：原始代码内容
}

/**
 * 解析行内 Markdown → HTML
 * 处理：**粗体**、*斜体*、`行内代码`、[链接](url)、换行→<br>
 */
function parseInline(text: string): string {
    let html = text
        // 转义 HTML 特殊字符（代码块外）
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')

    // 行内代码 `code` — 必须在粗体/斜体之前处理，防止内部符号被误解析
    html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')

    // 粗体 **text**
    html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')

    // 斜体 *text*（不匹配列表项前面的 * ）
    html = html.replace(/(?<!\*)\*(?!\*)([^*]+?)(?<!\*)\*(?!\*)/g, '<em>$1</em>')

    // 链接 [text](url)
    html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>')

    return html
}

/**
 * 将单段纯文本（非代码块）渲染为 HTML
 * 处理标题、列表、段落
 */
function renderTextBlock(text: string): string {
    const lines = text.split('\n')
    const result: string[] = []

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i]

        // 空行 → 段落分隔
        if (!line.trim()) {
            result.push('<div class="md-spacer"></div>')
            continue
        }

        // 标题 ### / ## / #
        const headingMatch = line.match(/^(#{1,6})\s+(.+)$/)
        if (headingMatch) {
            const level = headingMatch[1].length
            const hText = parseInline(headingMatch[2])
            result.push(`<h${level} class="md-heading">${hText}</h${level}>`)
            continue
        }

        // 无序列表 - item 或 * item
        const ulMatch = line.match(/^[\-\*]\s+(.+)$/)
        if (ulMatch) {
            result.push(`<li class="md-li">${parseInline(ulMatch[1])}</li>`)
            continue
        }

        // 有序列表 1. item
        const olMatch = line.match(/^\d+\.\s+(.+)$/)
        if (olMatch) {
            result.push(`<li class="md-li md-li-ol">${parseInline(olMatch[1])}</li>`)
            continue
        }

        // 引用 >
        const quoteMatch = line.match(/^>\s*(.+)$/)
        if (quoteMatch) {
            result.push(`<blockquote class="md-quote">${parseInline(quoteMatch[1])}</blockquote>`)
            continue
        }

        // 分割线 ---
        if (/^\-{3,}$/.test(line.trim())) {
            result.push('<hr class="md-hr">')
            continue
        }

        // 普通段落行
        result.push(`<p class="md-p">${parseInline(line)}</p>`)
    }

    return result.join('')
}

/**
 * 将完整 Markdown 文本解析为渲染段数组
 * 交替返回 text 段和 code 段
 */
export function parseMarkdown(content: string): MarkdownSegment[] {
    const segments: MarkdownSegment[] = []

    // 按代码块分隔 ```lang\n...\n```
    const codeBlockRegex = /```(\w*)\n?([\s\S]*?)```/g

    let lastIndex = 0
    let match: RegExpExecArray | null

    while ((match = codeBlockRegex.exec(content)) !== null) {
        // 代码块之前的文本
        const beforeText = content.slice(lastIndex, match.index)
        if (beforeText.trim()) {
            segments.push({
                type: 'text',
                html: renderTextBlock(beforeText),
            })
        }

        // 代码块
        const lang = match[1]?.trim() || detectCodeLang(match[2] || '')
        const code = (match[2] || '').replace(/\n$/, '') // 去掉末尾多余换行
        segments.push({
            type: 'code',
            lang,
            code,
        })

        lastIndex = match.index + match[0].length
    }

    // 剩余文本
    const remaining = content.slice(lastIndex)
    if (remaining.trim()) {
        segments.push({
            type: 'text',
            html: renderTextBlock(remaining),
        })
    }

    // 如果没有任何内容，返回空文本段
    if (segments.length === 0 && content.trim()) {
        segments.push({
            type: 'text',
            html: `<p class="md-p">${parseInline(content)}</p>`,
        })
    }

    return segments
}
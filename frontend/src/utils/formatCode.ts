/**
 * 代码工具集
 * - 语言标识 → 友好显示名映射
 * - 根据文件路径/代码内容推测编程语言
 */

// ===================== 语言映射 =====================

/**
 * 将内部语言标识映射为友好的显示名称
 * 用于按钮、标签等处显示
 */
export function formatLangLabel(lang: string): string {
    const map: Record<string, string> = {
        html: 'HTML',
        vue: 'Vue',
        javascript: 'JavaScript',
        js: 'JavaScript',
        typescript: 'TypeScript',
        ts: 'TypeScript',
        css: 'CSS',
        scss: 'SCSS',
        less: 'Less',
        python: 'Python',
        py: 'Python',
        java: 'Java',
        json: 'JSON',
        xml: 'XML',
        markdown: 'Markdown',
        md: 'Markdown',
        sql: 'SQL',
        text: 'Text',
        multi: '多文件',
    }
    return map[lang.toLowerCase()] ?? lang
}

/**
 * 将后端的 generateType（'HTML' | 'VUE' | 'MULTI_FILE'）映射为友好显示名
 */
export function formatGenerateTypeLabel(type: string): string {
    const map: Record<string, string> = {
        HTML: 'HTML',
        VUE: 'Vue',
        MULTI_FILE: '多文件',
        WORKFLOW: '工作流',
        GENERAL: '通用',
    }
    return map[type] ?? type
}

// ===================== 语言检测 =====================

/** 根据文件路径推测代码语言 */
export function detectFileLang(path: string): string {
    const ext = (path.split('.').pop() || '').toLowerCase()
    const map: Record<string, string> = {
        html: 'html', htm: 'html',
        vue: 'vue',
        js: 'javascript', mjs: 'javascript',
        ts: 'typescript',
        css: 'css', scss: 'scss', less: 'less',
        json: 'json',
        py: 'python', java: 'java',
        xml: 'xml', md: 'markdown',
    }
    return map[ext] || 'text'
}

/** 根据代码内容推测语言 */
export function detectCodeLang(code: string): string {
    const trimmed = code.trimStart()
    if (trimmed.startsWith('<template>') || trimmed.includes('export default')) return 'vue'
    if (trimmed.includes('<html') || trimmed.includes('<!DOCTYPE')) return 'html'
    if (trimmed.includes('package ') || trimmed.includes('import ')) return 'typescript'
    if (trimmed.startsWith('function ') || trimmed.startsWith('const ') || trimmed.includes('=>')) return 'javascript'
    if (trimmed.includes('{') && trimmed.includes('}') && trimmed.includes(':')) return 'css'
    return 'text'
}
/* eslint-disable vue/no-setup-props-in-components, no-http-url, unused-imports/no-unused-vars */
/**
 * 多文件预览合并工具
 *
 * 将 AI 返回的任意格式内容合并为单个浏览器可运行的 HTML 字符串，
 * 供 iframe.srcdoc 渲染。所有文件（HTML/CSS/JS/Vue）最终输出为一个
 * 自包含的 HTML 文件。
 */

import { parseContentToFiles } from './splitMultiFile'
import type { CodeFile } from '@/types/codegen'

// ===================== 类型 =====================

interface SfcBlocks {
  template: string
  script: string
  scriptSetup: string
  style: string
  styleScoped: boolean
  styleLang: string
}

interface CompiledComponent {
  name: string
  fileName: string
  jsCode: string
  deps: string[]
}

// ===================== 公共入口 =====================

/**
 * 将 AI 内容合并为单个可预览 HTML
 * @param content 单段文本 或者 本轮批量消息文本数组（多代码片段场景）
 * @returns HTML 字符串，或 null 表示无法解析
 */
export function mergeToPreviewHtml(content: string | string[]): string {
  try {
    const rawText = Array.isArray(content)
      ? content.filter(item => item?.trim()).join('\n\n')
      : (content || '')

    if (!rawText.trim()) {
      return diagPage('空内容', 'previewContent 为空字符串，请先触发 AI 生成代码', rawText)
    }

    // 优先解析多文件结构
    const files = parseContentToFiles(rawText)
    if (files && files.length > 0) {
      const merged = mergeFilesToHtml(files)
      if (merged) return merged
      return diagPage('多文件合并失败',
        `成功解析 ${files.length} 个文件，但合并为 HTML 时失败。文件列表：\n${files.map(f => f.path).join('\n')}`,
        rawText)
    }

    // 回退：从任意文本中提取
    const extracted = extractAnyToHtml(rawText)
    if (extracted) return extracted

    // 完全无法解析 → 显示诊断页
    return diagPage('无法识别内容格式',
      `内容不符合已知格式（JSON多文件 / Markdown多文件 / HTML / Vue SFC / CSS / JS）`,
      rawText)
  } catch (err: unknown) {
    const error = err as Error
    return `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>编译异常</title><style>body{padding:24px;font-family:system-ui}h2{color:#f53f3f}pre{background:#f7f8fa;padding:16px;border-radius:8px;color:#f53f3f;white-space:pre-wrap;font-size:13px}</style></head><body><h2>预览编译失败</h2><pre>${error.message}\n${error.stack ?? ''}</pre></body></html>`
  }
}

/** 生成诊断 HTML 页面，显示无法解析的原因和原始内容 */
function diagPage(title: string, reason: string, raw: string): string {
  const escaped = raw.slice(0, 3000)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return `<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><title>${title}</title>
<style>body{font-family:system-ui;padding:20px;color:#333}h2{color:#e67e22}pre{background:#f5f5f5;padding:12px;border-radius:6px;white-space:pre-wrap;font-size:12px;max-height:400px;overflow:auto}.info{color:#888;font-size:13px}</style></head>
<body><h2>${title}</h2><p>${reason}</p><p class="info">原始内容（前3000字符）：</p><pre>${escaped}</pre></body></html>`
}

/**
 * 判断文本是否是完整HTML文档（包含<!DOCTYPE html> 或 <html>根标签）
 */
function isFullHtml(text: string): boolean {
  return /^\s*<!DOCTYPE\s+html/i.test(text) || /^\s*<html\b/i.test(text)
}

// ===================== 回退：任意文本 → HTML =====================

function extractAnyToHtml(content: string): string | null {
  const trimmed = content.trim()

  // 提取所有代码块和文字段落
  const segments = extractAllSegments(trimmed)
  if (segments.length === 0) {
    // 无代码块 → 纯文本判断
    return guessWrap(trimmed)
  }

  // 如果有 HTML 代码块 → 以第一个 HTML 为基础
  const htmlSeg = segments.find(s => s.lang === 'html' || s.lang === 'htm')
  if (htmlSeg && isFullHtml(htmlSeg.code)) {
    // 完整 HTML → 直接返回
    return htmlSeg.code
  }
  if (htmlSeg) {
    // HTML 片段 → 包裹
    return wrapHtmlBody(htmlSeg.code)
  }

  // Vue SFC → 编译
  const vueSeg = segments.find(s => s.lang === 'vue')
  if (vueSeg) return wrapSingleVueSfc(vueSeg.code)

  // 第一个代码块（任意语言）
  const first = segments[0]
  if (first.lang === 'css') return wrapCssOnly(first.code)
  if (first.lang === 'javascript' || first.lang === 'js' || first.lang === 'typescript' || first.lang === 'ts') {
    return wrapJsOnly(first.code)
  }

  // 代码中包含 HTML 标签 → 当 HTML 包裹
  if (first.code.includes('<')) return wrapHtmlBody(first.code)

  // 兜底：包裹代码块
  return wrapCodeAsHtml(first.code, first.lang)
}

/** 提取文本中的全部代码块和它们的语言 */
function extractAllSegments(content: string): Array<{ lang: string; code: string }> {
  const segments: Array<{ lang: string; code: string }> = []
  const re = /```(\w*)\s*\n?([\s\S]*?)```/g
  let m: RegExpExecArray | null
  while ((m = re.exec(content)) !== null) {
    const lang = (m[1] || 'text').toLowerCase()
    const code = m[2].trim()
    if (code) segments.push({ lang, code })
  }
  return segments
}

/** 猜测文本类型并包裹 */
function guessWrap(text: string): string | null {
  const t = text.trim()
  if (!t) return null

  // 完整 HTML
  if (isFullHtml(t)) return t

  // HTML 片段
  if (/^<(\w+)[^>]*>/.test(t) && /<\/\w+>$/.test(t)) return wrapHtmlBody(t)

  // Vue SFC
  if (/<template[\s>]/i.test(t) && /<script/i.test(t)) return wrapSingleVueSfc(t)

  // CSS
  if (t.includes('{') && t.includes('}') && t.includes(':') && !t.includes('<')) {
    return wrapCssOnly(t)
  }

  // JS
  if (/\b(function|const|let|var|import|export)\b/.test(t) && !t.includes('<')) {
    return wrapJsOnly(t)
  }

  // 任何包含 < 的文本 → 当 HTML 处理
  if (t.includes('<')) return wrapHtmlBody(t)

  // 纯文本 → 包裹在 <pre> 中
  return '<!DOCTYPE html>\n<html lang="zh-CN">\n<head>\n  <meta charset="UTF-8">\n  <meta name="viewport" content="width=device-width, initial-scale=1.0">\n  <title>Preview</title>\n</head>\n<body>\n  <pre style="padding:20px;font-family:monospace;white-space:pre-wrap;">' +
      t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') +
      '\n  </pre>\n</body>\n</html>'
}

// ===================== 多文件合并调度 =====================

function mergeFilesToHtml(files: CodeFile[]): string | null {
  if (files.length === 0) return null

  const hasVue = files.some(f => f.path.endsWith('.vue'))
  const baseHtml = findMainHtml(files)

  // 有真正的 index.html → 以它为基础，内联所有资源
  if (baseHtml && isFullHtml(baseHtml.content)) {
    return mergeWithHtmlBase(baseHtml, files, hasVue)
  }

  // 有 Vue 文件 → 编译为完整 Vue 沙箱
  if (hasVue) {
    return buildVueSandbox(files)
  }

  // 其他所有情况 → 统一包裹为最小 HTML
  return buildMinimalWrapper(files)
}

// ===================== 策略1：HTML 基础 + 内联所有资源 =====================

function mergeWithHtmlBase(baseHtml: CodeFile, allFiles: CodeFile[], needsVue: boolean): string {
  const fileMap = buildFileMap(allFiles)
  const assembled = new Set<string>()

  let html = baseHtml.content
  assembled.add(baseHtml.path)

  // 1. 内联 CSS: <link rel="stylesheet" href="xxx"> → <style>
  html = html.replace(
      /<link\b[^>]*\brel\s*=\s*["']stylesheet["'][^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*\/?\s*>/gi,
      (full, href: string) => {
        if (isExternalUrl(href)) return full
        const file = resolveFile(href, fileMap)
        if (!file) return full
        assembled.add(file.path)
        return '<style>/* ' + href + ' */\n' + file.content + '\n</style>'
      },
  )

  // 2. 内联 JS: <script src="xxx">
  const SCRIPT_SRC_RE = new RegExp(
      '<script\\b[^>]*\\bsrc\\s*=\\s*["\']([^"\']+)["\'][^>]*>\\s*</script>', 'gi',
  )
  const SCRIPT_TAG_RE = new RegExp('<script\\b([^>]*)>', 'i')

  html = html.replace(SCRIPT_SRC_RE, (full, src: string) => {
    if (isExternalUrl(src)) return full
    const file = resolveFile(src, fileMap)
    if (!file) return full
    const tagOpen = full.match(SCRIPT_TAG_RE)?.[1] || ''
    const cleanTag = tagOpen.replace(/\bsrc\s*=\s*["'][^"']+["']/i, '')
    const inlined = resolveJsRecursive(file, fileMap, assembled)
    return '<script ' + cleanTag + '>\n' + inlined + '\n</script>'
  })

  // 3. 注入 Vue CDN（如果需要）
  if (needsVue && !html.includes('vue.global')) {
    // eslint-disable-next-line no-http-url
    const vueCdn = '<script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>'
    html = html.replace('</head>', '\n  ' + vueCdn + '\n</head>')
  }

  // 4. 追加未被引用的剩余文件
  const extraParts: string[] = []
  for (const f of allFiles) {
    if (assembled.has(f.path)) continue
    if (f.path.endsWith('.css')) {
      extraParts.push('<style>/* ' + f.path + ' */\n' + f.content + '\n</style>')
      assembled.add(f.path)
    } else if (f.path.endsWith('.js') || f.path.endsWith('.mjs') || f.path.endsWith('.ts')) {
      extraParts.push('<script>/* ' + f.path + ' */\n' + f.content + '\n</script>')
      assembled.add(f.path)
    } else if (f.path.endsWith('.vue')) {
      // 编译 Vue SFC 为组件定义 + 注入 mount
      const compJs = convertVueSfcToJs(f.content, f.path)
      extraParts.push('<script>/* ' + f.path + ' */\n' + compJs + '\n</script>')
      assembled.add(f.path)
    }
  }

  // 5. 为额外加入的 Vue 组件添加 mount 代码
  if (needsVue) {
    const allVueFiles = allFiles.filter(f => f.path.endsWith('.vue'))
    if (allVueFiles.length > 0) {
      // 优先用 App.vue 作为根组件
      const rootVue = allVueFiles.find(f => f.path.endsWith('App.vue') || f.path === 'App.vue') || allVueFiles[0]
      const rootName = componentVarName(rootVue.path)

      // 检查 HTML 中是否已有 mount 代码
      if (!html.includes('.mount(') && !html.includes('createApp(')) {
        const mountCode = '\n<script>\n' +
            '/* Vue mount */\n' +
            'const { createApp } = Vue\n' +
            'const __app__ = createApp(' + rootName + ')\n' +
            '__app__.mount(\'#app\')\n' +
            '</script>'
        html = html.replace('</body>', mountCode + '\n</body>')
      }
    }
  }

  if (extraParts.length > 0) {
    html = html.replace('</body>',
        extraParts.join('\n') + '\n</body>')
  }

  return html
}

// ===================== 策略2：Vue 项目沙箱（无 HTML 入口） =====================

function buildVueSandbox(files: CodeFile[]): string | null {
  const fileMap = buildFileMap(files)

  // 找到入口 .vue 文件
  const appVue = files.find(f => f.path.endsWith('App.vue') || f.path === 'App.vue')
  const entry = appVue || files.find(f => f.path.endsWith('.vue'))
  if (!entry) return null

  // 编译所有 .vue / .js 文件
  const compiled = new Map<string, CompiledComponent>()
  const visited = new Set<string>()

  function compileRecursive(filePath: string): CompiledComponent | null {
    const resolved = resolveFile(filePath, fileMap)
    if (!resolved) return null
    const key = resolved.path
    if (compiled.has(key)) return compiled.get(key)!
    if (visited.has(key)) return compiled.get(key) || null
    visited.add(key)

    let comp: CompiledComponent | null = null
    if (key.endsWith('.vue')) {
      comp = compileSfcToComponent(resolved, fileMap)
    } else if (key.endsWith('.js') || key.endsWith('.ts') || key.endsWith('.mjs')) {
      comp = compileJsModule(resolved, fileMap)
    }
    if (comp) {
      for (const dep of comp.deps) compileRecursive(dep)
      compiled.set(key, comp)
    }
    return comp
  }

  for (const f of files) {
    if (f.path.endsWith('.vue') || f.path.endsWith('.js') || f.path.endsWith('.ts') || f.path.endsWith('.mjs')) {
      compileRecursive(f.path)
    }
  }

  const ordered = topologicalSort(compiled)
  if (ordered.length === 0) return null

  const rootComp = ordered[ordered.length - 1]
  const allCss = collectAllCss(files)
  const vueApis = collectUsedVueApis(ordered)

  // 组件定义
  const componentDefs = ordered.map(c => c.jsCode).join('\n\n')

  // 入口 HTML
  const htmlEntry = findMainHtml(files)
  let html: string
  let isAutoEntry = false
  if (htmlEntry) {
    html = htmlEntry.content
  } else {
    html = generateIndexHtml()
    isAutoEntry = true
  }

  // 引导代码
  const bootCode = `
/* ====== Vue App ====== */
const { ${vueApis.join(', ')} } = Vue

${componentDefs}

const __app__ = Vue.createApp(${rootComp.name})
__app__.mount('#app')
`

  return injectResources(html, allCss, bootCode, isAutoEntry)
}

// ===================== 策略3：最小 HTML 包裹（CSS/JS/多类型混合） =====================

function buildMinimalWrapper(files: CodeFile[]): string {
  const cssParts: string[] = []
  const jsParts: string[] = []
  const htmlParts: string[] = []
  const vueFiles: CodeFile[] = []

  for (const f of files) {
    if (f.path.endsWith('.css') || f.path.endsWith('.scss') || f.path.endsWith('.less')) {
      cssParts.push('/* ' + f.path + ' */\n' + f.content)
    } else if (f.path.endsWith('.js') || f.path.endsWith('.mjs') || f.path.endsWith('.ts')) {
      jsParts.push('/* ' + f.path + ' */\n' + f.content)
    } else if (f.path.endsWith('.html') || f.path.endsWith('.htm')) {
      htmlParts.push(f.content)
    } else if (f.path.endsWith('.vue')) {
      vueFiles.push(f)
    } else {
      // 未知文件类型自动判断
      if (f.content.includes('{') && f.content.includes('}') && f.content.includes(':')) {
        cssParts.push('/* ' + f.path + ' */\n' + f.content)
      } else if (f.content.includes('<')) {
        htmlParts.push(f.content)
      } else {
        jsParts.push('/* ' + f.path + ' */\n' + f.content)
      }
    }
  }

  // 如果存在Vue文件，走Vue沙箱编译
  if (vueFiles.length > 0) {
    const allVueFiles = [...files.filter(f => !f.path.endsWith('.vue')), ...vueFiles]
    return buildVueSandbox(allVueFiles) || fallbackWrapper(cssParts, jsParts, htmlParts, true)
  }

  return fallbackWrapper(cssParts, jsParts, htmlParts, false)
}

function fallbackWrapper(cssParts: string[], jsParts: string[], htmlParts: string[], hasVue: boolean): string {
  let headExtra = ''
  let bodyContent = htmlParts.join('\n')
  let bodyExtra = ''

  if (cssParts.length > 0) {
    headExtra += '\n  <style>\n' + cssParts.join('\n\n') + '\n  </style>'
  }

  if (hasVue && !headExtra.includes('vue.global')) {
    // eslint-disable-next-line no-http-url
    headExtra += '\n  <script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>'
  }

  if (jsParts.length > 0) {
    bodyExtra += '\n  <script>\n' + jsParts.join('\n\n') + '\n  </script>'
  }

  // Vue项目自动挂载容器
  if (!bodyContent && hasVue) {
    bodyContent = '<div id="app"></div>'
  }

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Preview</title>${headExtra}
</head>
<body>
  ${bodyContent}
${bodyExtra}
</body>
</html>`
}

// ===================== 单文件包裹工具函数 =====================

function wrapHtmlBody(code: string): string {
  if (isFullHtml(code)) return code
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Preview</title>
</head>
<body>
${code}
</body>
</html>`
}

function wrapCssOnly(css: string): string {
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Preview</title>
  <style>
${css}
  </style>
</head>
<body>
  <div id="app"></div>
</body>
</html>`
}

function wrapJsOnly(js: string): string {
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Preview</title>
</head>
<body>
  <script>
${js}
  </script>
</body>
</html>`
}

function wrapCodeAsHtml(code: string, lang: string): string {
  let body = ''
  if (lang === 'css') {
    body = `<style>\n${code}\n</style>`
  } else if (['javascript', 'js', 'typescript', 'ts'].includes(lang)) {
    body = `<script>\n${code}\n</script>`
  } else {
    const safeCode = code
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
    body = `<pre style="padding:20px;font-family:monospace;white-space:pre-wrap;">${safeCode}</pre>`
  }
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Preview</title>
</head>
<body>
  ${body}
</body>
</html>`
}

function wrapSingleVueSfc(vueCode: string): string {
  const files: CodeFile[] = [{ path: 'App.vue', content: vueCode }]
  return buildVueSandbox(files) || wrapHtmlBody(vueCode)
}

// ===================== Vue SFC 编译器核心 =====================

function parseSfcBlocks(code: string): SfcBlocks {
  const result: SfcBlocks = {
    template: '',
    script: '',
    scriptSetup: '',
    style: '',
    styleScoped: false,
    styleLang: 'css'
  }

  const tMatch = code.match(/<template[^>]*>([\s\S]*?)<\/template>/i)
  if (tMatch) result.template = tMatch[1].trim()

  const ssMatch = code.match(/<script\s+setup[^>]*>([\s\S]*?)<\/script>/i)
  if (ssMatch) result.scriptSetup = ssMatch[1].trim()

  if (!result.scriptSetup) {
    const sMatch = code.match(/<script[^>]*>([\s\S]*?)<\/script>/i)
    if (sMatch) result.script = sMatch[1].trim()
  }

  const styleMatch = code.match(/<style([^>]*)>([\s\S]*?)<\/style>/i)
  if (styleMatch) {
    result.style = styleMatch[2].trim()
    const attrs = styleMatch[1]
    result.styleScoped = /scoped/i.test(attrs)
    const langMatch = attrs.match(/lang\s*=\s*["'](\w+)["']/i)
    if (langMatch) result.styleLang = langMatch[1].toLowerCase()
  }

  return result
}

function extractDeps(script: string): string[] {
  const deps: string[] = []
  // 匹配从相对路径导入组件
  const re = /import\s+(?:(?:\{[^}]*\}|\*\s+as\s+\w+|\w+)\s*,?\s*)?(?:\{[^}]*\})?\s*from\s*['"](\.[^'"]+)['"]\s*;?/g
  let m: RegExpExecArray | null
  while ((m = re.exec(script)) !== null) deps.push(m[1])
  // 匹配样式导入
  const styleRe = /import\s+['"](\.[^'"]+\.css)['"]\s*;?/g
  while ((m = styleRe.exec(script)) !== null) deps.push(m[1])
  return deps
}

function detectUsedVueApis(script: string): string[] {
  const apis = [
    'ref', 'reactive', 'computed', 'watch', 'watchEffect',
    'onMounted', 'onUnmounted', 'onBeforeMount', 'onBeforeUnmount',
    'onUpdated', 'onBeforeUpdate', 'onActivated', 'onDeactivated',
    'provide', 'inject', 'nextTick',
    'toRef', 'toRefs', 'readonly', 'shallowRef', 'shallowReactive',
    'triggerRef', 'customRef', 'markRaw', 'toRaw',
    'isRef', 'isReactive', 'isReadonly', 'isProxy',
    'unref', 'effectScope', 'getCurrentScope', 'onScopeDispose',
  ]
  const used = new Set<string>()
  // 清除注释、字符串避免误匹配
  const stripped = script
      .replace(/\/\/.*/g, ' ')
      .replace(/\/\*[\s\S]*?\*\//g, ' ')
      .replace(/`[\s\S]*?`/g, ' ')
      .replace(/'[^']*'/g, ' ')
      .replace(/"[^"]*"/g, ' ')
  for (const api of apis) {
    if (new RegExp('\\b' + api + '\\b').test(stripped)) used.add(api)
  }
  return Array.from(used)
}

function compileSfcToComponent(file: CodeFile, fileMap: Map<string, CodeFile>): CompiledComponent | null {
  const blocks = parseSfcBlocks(file.content)
  const name = componentVarName(file.path)
  const rawDeps = extractDeps(blocks.script || blocks.scriptSetup || '')
  const deps = rawDeps.map(d => {
    const resolved = resolveFile(d, fileMap)
    return resolved ? resolved.path : d
  })

  const template = blocks.template || '<div></div>'
  const hasSetup = !!blocks.scriptSetup
  const hasOptions = !!blocks.script

  // 收集模板内用到的子组件
  const subCompMap: Record<string, string> = {}
  for (const d of deps) {
    if (d.endsWith('.vue')) {
      const compTag = d.replace(/^.*[/\\]/, '').replace(/\.vue$/, '')
      subCompMap[compTag] = componentVarName(d)
    }
  }

  let optionsCode = ''
  if (hasSetup) {
    optionsCode = compileScriptSetup(blocks.scriptSetup, name, template, subCompMap, fileMap)
  } else if (hasOptions) {
    optionsCode = compileScriptOptions(blocks.script, name, template, subCompMap)
  } else {
    // 无脚本只有模板
    let compDef = `const ${name} = { template: ${JSON.stringify(template)}`
    if (Object.keys(subCompMap).length > 0) {
      const entries = Object.entries(subCompMap).map(([k, v]) => `    '${k}': ${v}`)
      compDef += `,\n  components: {\n${entries.join(',\n')}\n  }`
    }
    compDef += '\n}'
    optionsCode = compDef
  }

  // 强制注入template
  if (!optionsCode.includes('template:')) {
    optionsCode = optionsCode.replace(
        /(Vue\.defineComponent\s*\(\s*\{)/,
        `$1\n    template: ${JSON.stringify(template)},`,
    )
    if (!optionsCode.includes('template:')) {
      optionsCode = optionsCode.replace(
          /(const\s+\w+\s*=\s*\{)/,
          `$1\n    template: ${JSON.stringify(template)},`,
      )
    }
  }

  // 注入components配置
  if (Object.keys(subCompMap).length > 0 && !optionsCode.includes('components:')) {
    const entries = Object.entries(subCompMap).map(([k, v]) => `    '${k}': ${v}`)
    optionsCode = optionsCode.replace(
        /}(\s*\)?\s*)$/,
        `,\n  components: {\n${entries.join(',\n')}\n  }\n}$1`,
    )
  }

  // 处理scoped样式注入
  let styleInjectCode = ''
  if (blocks.style) {
    styleInjectCode = generateScopedStyle(name, blocks.style, !!blocks.styleScoped)
  }

  return {
    name,
    fileName: file.path,
    jsCode: optionsCode + '\n' + styleInjectCode,
    deps
  }
}

function compileScriptSetup(
    script: string,
    compName: string,
    template: string,
    subCompMap: Record<string, string>,
    fileMap: Map<string, CodeFile>
): string {
  let code = script
  const usedApis = detectUsedVueApis(code)
  const vueDestructLine = usedApis.length > 0 ? `const { ${usedApis.join(', ')} } = Vue` : ''

  // 移除vue内置导入
  code = code.replace(/import\s+\{([^}]+)\}\s*from\s*['"]vue['"]\s*;?/g, '')
  code = code.replace(/import\s+(\w+)\s+from\s*['"]vue['"]\s*;?/g, '')

  // 替换vue组件导入为全局变量引用
  code = code.replace(
      /import\s+(\w+)\s+from\s*['"](\.[^'"]+\.vue)['"]\s*;?/g,
      (_full, importedName: string, importPath: string) => {
        const resolved = resolveFile(importPath, fileMap)
        return resolved ? `const ${importedName} = ${componentVarName(resolved.path)}` : _full
      },
  )

  // 替换js模块导入
  code = code.replace(
      /import\s+(\w+)\s+from\s*['"](\.[^'"]+\.(?:js|ts|mjs))['"]\s*;?/g,
      (_full, importedName: string, importPath: string) => {
        const resolved = resolveFile(importPath, fileMap)
        return resolved ? `const ${importedName} = ${componentVarName(resolved.path)}` : _full
      },
  )

  // 移除样式导入
  code = code.replace(/import\s+['"](\.[^'"]+\.css)['"]\s*;?/g, '')
  // 移除所有其他相对导入
  code = code.replace(/import\s+.*?from\s+['"]\.[^'"]+['"]\s*;?/g, '')
  code = code.replace(/import\s+['"]\.[^'"]+['"]\s*;?/g, '')

  // 去除setup语法糖专属编译器宏
  code = code.replace(/const\s+\w+\s*=\s*defineProps\s*(?:<[^>]*>)?\s*\(\s*(?:\{[^}]*\})?\s*\)\s*;?/g, '')
  code = code.replace(/const\s+\w+\s*=\s*defineEmits\s*(?:<[^>]*>)?\s*\(\s*\[[^\]]*\]\s*\)\s*;?/g, '')
  code = code.replace(/defineExpose\s*\(\s*\{([^}]*)\}\s*\)\s*;?/g, '// expose: $1')
  code = code.replace(/\n{3,}/g, '\n\n').trim()

  const compEntries = Object.entries(subCompMap)
  const compStr = compEntries.length > 0
      ? `,\n  components: {\n${compEntries.map(([k, v]) => `    '${k}': ${v}`).join(',\n')}\n  }`
      : ''

  return `const ${compName} = Vue.defineComponent({
  template: ${JSON.stringify(template)},${compStr}
  setup(props, { emit }) {
    ${vueDestructLine}
    ${code}
    return {}
  }
})`
}

function compileScriptOptions(
    script: string,
    compName: string,
    template: string,
    subCompMap: Record<string, string>
): string {
  let code = script
  // 清理所有import语句
  code = code.replace(/import\s+.*?from\s+['"].*?['"]\s*;?/g, '')
  code = code.replace(/import\s+['"][^'"]+['"]\s*;?/g, '')
  // 把默认导出改为变量声明
  code = code.replace(/export\s+default\s*(\{[\s\S]*?\})/, (_full, obj: string) => `const ${compName} = ${obj}`)

  // 注入template
  if (!code.includes('template:')) {
    code = code.replace(/(const\s+\w+\s*=\s*\{)/, `$1\n    template: ${JSON.stringify(template)},`)
  }

  // 注入components
  if (Object.keys(subCompMap).length > 0 && !code.includes('components:')) {
    const entries = Object.entries(subCompMap).map(([k, v]) => `    '${k}': ${v}`)
    code = code.replace(/}(\s*\)?\s*)$/, `,\n  components: {\n${entries.join(',\n')}\n  }\n}$1`)
  }

  return `/* ${compName} (options API) */\n${code}`
}

function compileJsModule(file: CodeFile, fileMap: Map<string, CodeFile>): CompiledComponent | null {
  const name = componentVarName(file.path)
  const rawDeps = extractDeps(file.content)
  const deps = rawDeps.map(d => {
    const resolved = resolveFile(d, fileMap)
    return resolved ? resolved.path : d
  })
  let code = file.content

  // 清理vue导入
  code = code.replace(/import\s+\{([^}]+)\}\s*from\s*['"]vue['"]\s*;?/g, '')
  // 替换vue组件导入
  code = code.replace(
      /import\s+(\w+)\s+from\s*['"](\.[^'"]+\.vue)['"]\s*;?/g,
      (_full, importedName: string, importPath: string) => {
        const resolved = resolveFile(importPath, fileMap)
        return resolved ? `const ${importedName} = ${componentVarName(resolved.path)}` : _full
      },
  )
  // 替换JS模块导入
  code = code.replace(
      /import\s+(\w+)\s+from\s*['"](\.[^'"]+\.(?:js|ts|mjs))['"]\s*;?/g,
      (_full, importedName: string, importPath: string) => {
        const resolved = resolveFile(importPath, fileMap)
        return resolved ? `const ${importedName} = ${componentVarName(resolved.path)}` : _full
      },
  )
  // 清理样式、其他相对导入
  code = code.replace(/import\s+['"](\.[^'"]+\.css)['"]\s*;?/g, '')
  code = code.replace(/import\s+.*?from\s+['"]\.[^'"]+['"]\s*;?/g, '')
  code = code.replace(/import\s+['"]\.[^'"]+['"]\s*;?/g, '')
  code = code.replace(/\n{3,}/g, '\n\n').trim()

  return {
    name,
    fileName: file.path,
    jsCode: `/* ${file.path} */\n${code}`,
    deps
  }
}

function generateScopedStyle(compName: string, css: string, scoped: boolean): string {
  if (!css) return ''
  const scopeId = compName.replace(/^__reg_/, 'data-v-')
  if (scoped) {
    // 仅常规选择器添加scope属性，@规则、伪类不处理
    css = css.replace(
        /([^\r\n,{}]+)(?=\s*\{)/g,
        (selector: string) => {
          const s = selector.trim()
          if (s.startsWith('@') || s.startsWith(':root') || s.startsWith('from') || s.startsWith('to') || s.startsWith('&')) return selector
          return s + `[${scopeId}]`
        }
    )
  }
  // 动态创建style标签注入样式
  return `;(function(){var s=document.createElement("style");s.textContent=${JSON.stringify(css)};document.head.appendChild(s)})();`
}

function convertVueSfcToJs(code: string, filename: string): string {
  const templateMatch = code.match(/<template>([\s\S]*?)<\/template>/i)
  const scriptSetupMatch = code.match(/<script\s+setup[^>]*>([\s\S]*?)<\/script>/i)
  const scriptMatch = !scriptSetupMatch ? code.match(/<script[^>]*>([\s\S]*?)<\/script>/i) : null
  const styleMatch = code.match(/<style[^>]*>([\s\S]*?)<\/style>/i)

  const componentName = filename.split('/').pop()!.replace(/\.vue$/, '').replace(/[^a-zA-Z0-9]/g, '_')
  const template = templateMatch ? templateMatch[1].trim() : '<div></div>'
  const style = styleMatch ? styleMatch[1].trim() : ''

  let styleInjection = ''
  if (style) {
    styleInjection = ';(function(){var s=document.createElement("style");s.textContent=' + JSON.stringify(style) + ';document.head.appendChild(s)})();'
  }

  if (scriptSetupMatch) {
    let script = scriptSetupMatch[1].trim()
    script = script.replace(/import\s+.*?from\s+['"].*?['"]\s*;?/g, '')
    script = script
        .replace(/const\s+\w+\s*=\s*defineProps\s*(?:<[^>]*>)?\s*\(\s*(?:\{[^}]*\})?\s*\)\s*;?/g, '')
        .replace(/const\s+\w+\s*=\s*defineEmits\s*<[^>]*>\s*\(\s*\)\s*;?/g, '')
        .trim()
    const usedApis = detectUsedVueApis(script)
    const vueDest = usedApis.length > 0 ? 'const { ' + usedApis.join(', ') + ' } = Vue; ' : ''
    return styleInjection + `
const ${componentName} = {
  template: ${JSON.stringify(template)},
  setup() {
    ${vueDest}
    ${script}
    return {}
  }
}`
  }

  if (scriptMatch) {
    let script = scriptMatch[1].trim()
    script = script.replace(/import\s+.*?from\s+['"].*?['"]\s*;?/g, '')
    script = script
        .replace(/const\s+\w+\s*=\s*defineProps\s*<[^>]*>\s*\(\s*\)\s*;?/g, '')
        .replace(/const\s+\w+\s*=\s*defineEmits\s*<[^>]*>\s*\(\s*\)\s*\)\s*;?/g, '')
        .trim()
    const usedApis = detectUsedVueApis(script)
    const vueDest = usedApis.length > 0 ? 'const { ' + usedApis.join(', ') + ' } = Vue; ' : ''
    return styleInjection + `
const ${componentName} = {
  template: ${JSON.stringify(template)},
  setup() {
    ${vueDest}
    ${script}
    return {}
  }
}`
  }

  return `const ${componentName} = { template: ${JSON.stringify(template)} }`
}

// ===================== 项目组装工具函数 =====================

function topologicalSort(compiled: Map<string, CompiledComponent>): CompiledComponent[] {
  const result: CompiledComponent[] = []
  const visited = new Set<string>()
  const tempMark = new Set<string>()

  function visit(path: string, comp: CompiledComponent) {
    if (visited.has(path)) return
    if (tempMark.has(path)) return
    tempMark.add(path)
    for (const depPath of comp.deps) {
      const depComp = compiled.get(depPath)
      if (depComp) visit(depPath, depComp)
    }
    tempMark.delete(path)
    visited.add(path)
    result.push(comp)
  }

  for (const [path, comp] of compiled) {
    visit(path, comp)
  }
  return result
}

function collectAllCss(files: CodeFile[]): string {
  const cssParts: string[] = []
  const seen = new Set<string>()
  for (const f of files) {
    if ((f.path.endsWith('.css') || f.path.endsWith('.scss') || f.path.endsWith('.less')) && !seen.has(f.path)) {
      seen.add(f.path)
      cssParts.push(`/* ${f.path} */\n${f.content}`)
    }
  }
  return cssParts.join('\n\n')
}

function collectUsedVueApis(comps: CompiledComponent[]): string[] {
  const allApis = [
    'createApp', 'defineComponent',
    'ref', 'reactive', 'computed', 'watch', 'watchEffect',
    'onMounted', 'onUnmounted', 'onBeforeMount', 'onBeforeUnmount',
    'onUpdated', 'onBeforeUpdate', 'onActivated', 'onDeactivated',
    'provide', 'inject', 'nextTick',
    'toRef', 'toRefs', 'readonly', 'shallowRef', 'shallowReactive',
    'h', 'resolveComponent',
  ]
  const used = new Set<string>()
  for (const comp of comps) {
    for (const api of allApis) {
      if (comp.jsCode.includes(api)) used.add(api)
    }
  }
  return Array.from(used)
}

function generateIndexHtml(): string {
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Preview</title>
</head>
<body>
  <div id="app"></div>
</body>
</html>`
}

function injectResources(html: string, css: string, bootJs: string, isAutoEntry: boolean): string {
  let result = html

  // 注入Vue全局CDN
  if (!result.includes('vue.global')) {
    // eslint-disable-next-line no-http-url
    const cdn = `<script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>`
    result = result.replace('</head>', `\n  ${cdn}\n</head>`)
  }

  // 注入全局样式
  if (css) {
    result = result.replace('</head>', `\n<style>\n${css}\n</style>\n</head>`)
  }

  // 注入启动脚本
  const bootTag = `\n<script>\n${bootJs}\n</script>`
  result = result.replace('</body>', bootTag + '\n</body>')

  // 自动模板补充挂载节点
  if (isAutoEntry && !result.includes('id="app"')) {
    result = result.replace('<body>', '<body>\n  <div id="app"></div>')
  }

  return result
}

// ===================== 文件路径工具函数 =====================

function buildFileMap(files: CodeFile[]): Map<string, CodeFile> {
  const map = new Map<string, CodeFile>()
  for (const f of files) {
    map.set(f.path, f)
    const fileName = f.path.split('/').pop()!
    if (!map.has(fileName)) map.set(fileName, f)
    // 兼容常见目录前缀
    const prefixList = ['src/', './', 'components/', 'assets/', 'public/']
    for (const prefix of prefixList) {
      if (f.path.startsWith(prefix)) {
        const shortKey = f.path.slice(prefix.length)
        if (!map.has(shortKey)) map.set(shortKey, f)
      }
    }
  }
  return map
}

function findMainHtml(files: CodeFile[]): CodeFile | null {
  return files.find(f => f.path === 'index.html' || f.path.endsWith('index.html'))
      || files.find(f => f.path.endsWith('.html'))
      || null
}

function resolveFile(refPath: string, fileMap: Map<string, CodeFile>, fromFile?: string): CodeFile | null {
  // 直接匹配
  if (fileMap.has(refPath)) return fileMap.get(refPath)!
  // 去除./前缀再匹配
  const cleanPath = refPath.replace(/^\.\//, '')
  if (fileMap.has(cleanPath)) return fileMap.get(cleanPath)!
  // 尝试补全后缀
  const extList = ['.vue', '.js', '.ts', '.css']
  for (const ext of extList) {
    if (fileMap.has(cleanPath + ext)) return fileMap.get(cleanPath + ext)!
  }
  // 根据当前文件所在目录解析相对路径
  if (fromFile) {
    const baseDir = fromFile.split('/').slice(0, -1).join('/')
    if (baseDir) {
      const relativeFullPath = baseDir + '/' + cleanPath
      if (fileMap.has(relativeFullPath)) return fileMap.get(relativeFullPath)!
      for (const ext of extList) {
        if (fileMap.has(relativeFullPath + ext)) return fileMap.get(relativeFullPath + ext)!
      }
    }
  }
  // 模糊文件名匹配
  for (const [path, file] of fileMap) {
    if (path.endsWith('/' + cleanPath) || path === cleanPath) return file
  }
  return null
}

function resolveJsRecursive(file: CodeFile, fileMap: Map<string, CodeFile>, assembled: Set<string>): string {
  if (assembled.has(file.path)) return `/* ${file.path} (已内联) */`
  assembled.add(file.path)

  let content = file.content
  if (file.path.endsWith('.vue')) return convertVueSfcToJs(content, file.path)

  // 递归替换import依赖
  content = content.replace(
      /import\s+(?:(?:\{[^}]*\}|\*\s+as\s+\w+|\w+)\s*,?\s*)?(?:\{[^}]*\})?\s*from\s*['"](\.[^'"]+)['"]\s*;?/g,
      (full, importPath: string) => {
        const resolved = resolveFile(importPath, fileMap, file.path)
            || resolveFile(importPath + '.js', fileMap, file.path)
            || resolveFile(importPath + '.vue', fileMap, file.path)
            || resolveFile(importPath + '/index.js', fileMap, file.path)
        if (!resolved) return full
        const innerCode = resolveJsRecursive(resolved, fileMap, assembled)
        return `/* import ${importPath} */\n${innerCode}`
      },
  )

  // 动态import懒加载处理
  content = content.replace(
      /import\s*\(\s*['"](\.[^'"]+)['"]\s*\)/g,
      (full, importPath: string) => {
        const resolved = resolveFile(importPath, fileMap, file.path)
        if (!resolved) return full
        const innerCode = resolveJsRecursive(resolved, fileMap, assembled)
        return `(function(){/* ${importPath} */\n${innerCode}\n})()`
      },
  )

  return content
}

function isExternalUrl(url: string): boolean {
  return /^(https?:)?\/\//.test(url) || url.startsWith('data:') || url.startsWith('#')
}

function componentVarName(filePath: string): string {
  return '__reg_' + filePath
      .replace(/^.*[\\\/]/, '')
      .replace(/\.\w+$/, '')
}
/**
 * 简易语法高亮 —— 豆包风格的多语言代码着色
 * 成员7 — 为代码块渲染带颜色的 token
 */

/** 语言关键词集合 */
const KEYWORDS: Record<string, string[]> = {
    js: ['const','let','var','function','return','if','else','for','while','do','switch','case','break','continue',
        'new','this','class','extends','import','export','default','from','async','await','try','catch','throw','finally',
        'typeof','instanceof','in','of','true','false','null','undefined','void','delete','yield','static','get','set'],
    ts: ['const','let','var','function','return','if','else','for','while','do','switch','case','break','continue',
        'new','this','class','extends','implements','import','export','default','from','async','await','try','catch','throw','finally',
        'typeof','instanceof','in','of','true','false','null','undefined','void','delete','yield','static','get','set',
        'interface','type','enum','namespace','readonly','abstract','private','public','protected','as','any','string','number','boolean'],
    py: ['def','return','if','elif','else','for','while','import','from','class','try','except','finally','with','as',
        'True','False','None','and','or','not','in','is','lambda','yield','raise','pass','break','continue','global','nonlocal','async','await'],
    java: ['public','private','protected','static','final','class','interface','extends','implements','new','return',
        'if','else','for','while','do','switch','case','break','continue','try','catch','throw','throws','finally',
        'void','int','long','double','float','boolean','char','String','null','true','false','this','super','import','package',
        'abstract','synchronized','volatile','transient','native','enum','instanceof','default'],
    css: ['none','auto','inherit','initial','unset','block','inline','flex','grid','absolute','relative','fixed','sticky',
        'hidden','visible','center','space-between','space-around','normal','bold','italic','underline','uppercase','lowercase','capitalize',
        'transparent','cover','contain','repeat','no-repeat','pointer','solid','dashed','dotted','border-box','content-box'],
    html: ['DOCTYPE','html','head','body','div','span','p','a','img','ul','ol','li','table','tr','td','th','thead','tbody',
        'form','input','button','select','option','textarea','label','h1','h2','h3','h4','h5','h6','header','footer','nav','section',
        'article','aside','main','script','style','link','meta','title','br','hr','strong','em','code','pre','iframe','canvas','svg'],
    vue: ['template','script','style','setup','ref','reactive','computed','watch','onMounted','defineProps','defineEmits',
        'v-if','v-else','v-for','v-model','v-bind','v-on','v-show','v-html','@click','@change','@input',':key',':class',':style'],
    sql: ['SELECT','FROM','WHERE','INSERT','INTO','UPDATE','DELETE','CREATE','TABLE','ALTER','DROP','INDEX','JOIN','LEFT','RIGHT',
        'INNER','OUTER','ON','AND','OR','NOT','NULL','IS','IN','LIKE','BETWEEN','ORDER','BY','GROUP','HAVING','LIMIT','OFFSET',
        'AS','DISTINCT','COUNT','SUM','AVG','MAX','MIN','PRIMARY','KEY','FOREIGN','REFERENCES','VALUES','SET','DEFAULT','UNIQUE'],
}

/** 把 lang 映射到关键词集合 */
function resolveLang(lang: string): string {
    const m: Record<string, string> = {
        javascript: 'js', js: 'js', typescript: 'ts', ts: 'ts',
        python: 'py', py: 'py', java: 'java',
        css: 'css', scss: 'css', less: 'css',
        html: 'html', vue: 'vue', svelte: 'vue',
        sql: 'sql', json: 'json',
    }
    return m[lang.toLowerCase()] ?? lang.toLowerCase()
}

/** 主入口：对代码文本进行高亮 */
export function highlightCode(code: string, lang = 'text'): string {
    const l = resolveLang(lang)
    const keywords = KEYWORDS[l] ?? KEYWORDS.js

    // 先转义 HTML
    let html = code
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')

    // 按 token 类别正则替换
    // 顺序很重要：先匹配长 token（字符串/注释），再匹配短 token（关键词）

    // 1. 多行注释 /* ... */ 和单行注释 //
    html = html.replace(/(\/\*[\s\S]*?\*\/)/g, '<span class="tk-comment">$1</span>')
    html = html.replace(/(\/\/.*$)/gm, '<span class="tk-comment">$1</span>')

    // 2. 字符串（双引号和单引号）
    html = html.replace(/(&quot;[^&]*?&quot;)/g, '<span class="tk-string">$1</span>')
    html = html.replace(/(&#39;[^&]*?&#39;)/g, '<span class="tk-string">$1</span>')
    // 模板字符串
    html = html.replace(/(`[^`]*`)/g, '<span class="tk-string">$1</span>')

    // 3. HTML 标签
    html = html.replace(/(&lt;\/?\w+[^&]*?\/?&gt;)/g, '<span class="tk-tag">$1</span>')

    // 4. CSS 属性
    if (l === 'css') {
        html = html.replace(/^(\s*)([\w-]+)(\s*:)/gm,
            '$1<span class="tk-prop">$2</span>$3')
    }

    // 5. 数字
    html = html.replace(/\b(\d+\.?\d*)\b/g, '<span class="tk-num">$1</span>')

    // 6. 函数调用 method( —— 必须在关键词之前，否则关键词会覆盖 if/for/while 等
    html = html.replace(/\b(\w+)(?=\()/g, '<span class="tk-fn">$1</span>')

    // 7. 关键词（词边界匹配）—— 在函数调用之后，覆盖 if(、for(、while( 等
    if (keywords.length) {
        const kwPattern = new RegExp(`\\b(${keywords.join('|')})\\b`, 'g')
        html = html.replace(kwPattern, '<span class="tk-keyword">$1</span>')
    }

    // 8. 布尔/null
    html = html.replace(/\b(true|false|null|undefined|None|True|False)\b/g, '<span class="tk-bool">$1</span>')

    return html
}
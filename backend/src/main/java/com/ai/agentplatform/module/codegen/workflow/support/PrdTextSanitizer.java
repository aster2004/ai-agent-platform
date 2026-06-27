package com.ai.agentplatform.module.codegen.workflow.support;

import org.springframework.util.StringUtils;

/**
 * 净化需求分析 / PRD 文本：去除模型误输出的代码、HTML 页面，仅保留可读文字。
 */
public final class PrdTextSanitizer {

    private PrdTextSanitizer() {
    }

    /** 需求摘要：应为 1～2 句纯文字，若像代码则丢弃 */
    public static String sanitizeSummary(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = stripArtifacts(text.trim());
        if (!StringUtils.hasText(cleaned) || looksLikeCode(cleaned)) {
            return "";
        }
        return truncate(cleaned, 500);
    }

    /** PRD 正文：保留 Markdown 结构，移除代码块与整页 HTML */
    public static String sanitizePrd(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = stripArtifacts(text.trim());
        cleaned = cleaned.replaceAll("(?m)^\\s*#{1,6}\\s*技术建议\\s*$[\\s\\S]*?(?=^\\s*#{1,6}\\s|$)", "").trim();
        return truncate(cleaned, 12000);
    }

    public static boolean looksLikeCode(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String t = text.trim();
        if (t.contains("```")) {
            return true;
        }
        if (t.startsWith("<!DOCTYPE") || t.startsWith("<html") || t.startsWith("<HTML")) {
            return true;
        }
        if (t.startsWith("<") && (t.contains("</script>") || t.contains("</style>") || t.contains("<body"))) {
            return true;
        }
        if (t.contains("<template") && t.contains("<script")) {
            return true;
        }
        int angle = t.indexOf('<');
        if (angle >= 0 && angle < 80 && t.contains(">") && t.contains("</")) {
            return true;
        }
        return false;
    }

    private static String stripArtifacts(String text) {
        String s = text;
        s = s.replaceAll("```[\\w-]*\\s*\\n?[\\s\\S]*?```", "").trim();
        s = s.replaceAll("(?is)<!DOCTYPE\\s+html[\\s\\S]*?</html>", "").trim();
        s = s.replaceAll("(?is)<html[\\s\\S]*?</html>", "").trim();
        s = s.replaceAll("(?is)<head[\\s\\S]*?</head>", "").trim();
        s = s.replaceAll("(?is)<style[\\s\\S]*?</style>", "").trim();
        s = s.replaceAll("(?is)<script[\\s\\S]*?</script>", "").trim();
        s = s.replaceAll("(?m)^\\s*import\\s+.+$", "").trim();
        s = s.replaceAll("(?m)^\\s*export\\s+.+$", "").trim();
        s = s.replaceAll("\\n{3,}", "\n\n").trim();
        return s;
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "…";
    }
}

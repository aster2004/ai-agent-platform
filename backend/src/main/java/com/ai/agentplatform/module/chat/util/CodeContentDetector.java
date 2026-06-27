package com.ai.agentplatform.module.chat.util;

/**
 * 判断 ai 消息是否为代码/PRD 类内容，用于 save 时跳过写入 Redis。
 */
public final class CodeContentDetector {

    private static final int CODE_BLOCK_MIN_LENGTH = 500;

    private CodeContentDetector() {
    }

    /**
     * @return true 表示不应写入 Redis（完整内容仍进 MySQL）
     */
    public static boolean isCodeOrArtifactContent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String trimmed = content.trim();
        String lower = trimmed.toLowerCase();

        if (trimmed.startsWith("@WORKFLOW_PENDING@")) {
            return true;
        }
        if (isPrdOrAnalysisSummary(trimmed)) {
            return true;
        }
        if (lower.contains("<!doctype") || lower.startsWith("<html") || looksLikeHtmlDocument(trimmed)) {
            return true;
        }
        if (looksLikeJsonFileArray(trimmed)) {
            return true;
        }
        if (trimmed.contains("## 📁") && trimmed.contains("```")) {
            return true;
        }
        if (countCodeBlocks(trimmed) >= 1 && trimmed.length() > CODE_BLOCK_MIN_LENGTH) {
            return true;
        }
        return false;
    }

    private static boolean looksLikeHtmlDocument(String content) {
        return content.length() > 200
                && content.contains("<")
                && (content.contains("</html>") || content.contains("<head") || content.contains("<body"));
    }

    private static boolean looksLikeJsonFileArray(String content) {
        int start = content.indexOf("[{");
        int end = content.lastIndexOf("}]");
        if (start < 0 || end <= start) {
            return false;
        }
        String candidate = content.substring(start, end + 2);
        return candidate.contains("\"path\"") && candidate.contains("\"content\"");
    }

    private static int countCodeBlocks(String content) {
        int count = 0;
        int idx = 0;
        while ((idx = content.indexOf("```", idx)) >= 0) {
            count++;
            idx += 3;
        }
        return count / 2;
    }

    /** Workflow 前端 save 的 PRD 展示文本，应由后端 [分析] 写入 Redis。 */
    public static boolean isPrdOrAnalysisSummary(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String trimmed = content.trim();
        return trimmed.startsWith("[分析]")
                || trimmed.contains("## 📋 需求分析")
                || trimmed.contains("需求分析完成");
    }
}

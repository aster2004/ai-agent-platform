package com.ai.agentplatform.module.chat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeContentDetectorTest {

    @Test
    void detectsHtml() {
        assertTrue(CodeContentDetector.isCodeOrArtifactContent("<!DOCTYPE html><html><body>hi</body></html>"));
    }

    @Test
    void detectsJsonFiles() {
        String json = "[{\"path\":\"index.html\",\"content\":\"<html></html>\"}]";
        assertTrue(CodeContentDetector.isCodeOrArtifactContent(json));
    }

    @Test
    void detectsMarkdownFile() {
        String md = "## 📁 index.html\n\n```html\n<html></html>\n```";
        assertTrue(CodeContentDetector.isCodeOrArtifactContent(md));
    }

    @Test
    void allowsShortPlainAiText() {
        assertFalse(CodeContentDetector.isCodeOrArtifactContent("好的，我会把标题改成红色。"));
    }

    @Test
    void detectsPrdSummary() {
        assertTrue(CodeContentDetector.isCodeOrArtifactContent("## 📋 需求分析完成\n\n用户需要日历"));
    }

    @Test
    void detectsAnalysisMemory() {
        assertTrue(CodeContentDetector.isPrdOrAnalysisSummary("[分析] 摘要：待办 App"));
    }
}

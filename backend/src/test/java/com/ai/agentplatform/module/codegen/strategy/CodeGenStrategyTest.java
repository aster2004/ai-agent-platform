package com.ai.agentplatform.module.codegen.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 代码生成策略单元测试
 */
class CodeGenStrategyTest {

    private HtmlSingleStrategy htmlSingleStrategy;
    private HtmlMultiStrategy htmlMultiStrategy;
    private VueStrategy vueStrategy;
    private CodeGenStrategyFactory strategyFactory;

    @BeforeEach
    void setUp() {
        htmlSingleStrategy = new HtmlSingleStrategy();
        htmlMultiStrategy = new HtmlMultiStrategy();
        vueStrategy = new VueStrategy();
        strategyFactory = new CodeGenStrategyFactory();
        // 手动注入策略列表（模拟 Spring 依赖注入）
        strategyFactory.strategyList = List.of(htmlSingleStrategy, htmlMultiStrategy, vueStrategy);
    }

    // ==================== Strategy Support ====================

    @Test
    void htmlSingleStrategyShouldSupportHtmlType() {
        assertTrue(htmlSingleStrategy.support("HTML"));
        assertFalse(htmlSingleStrategy.support("MULTI_FILE"));
        assertFalse(htmlSingleStrategy.support("VUE"));
    }

    @Test
    void htmlMultiStrategyShouldSupportMultiFileType() {
        assertTrue(htmlMultiStrategy.support("MULTI_FILE"));
        assertFalse(htmlMultiStrategy.support("HTML"));
    }

    // ==================== HtmlSingleStrategy ====================

    @Test
    void htmlSingleShouldAppendFormatConstraints() {
        String basePrompt = "只输出纯净代码";
        String result = htmlSingleStrategy.buildSpecialPrompt(basePrompt, null);
        assertTrue(result.contains("只输出纯净代码"));
        assertTrue(result.contains("DOCTYPE html"));
        assertTrue(result.contains("markdown"));
        assertTrue(result.contains("JavaScript"));
    }

    @Test
    void htmlSingleShouldReturnRawText() {
        String html = "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>";
        assertEquals(html, htmlSingleStrategy.parseResult(html));
    }

    @Test
    void htmlSingleShouldReturnHtmlWithMarkdownWrapper() {
        // AI 有时会返回带 markdown 标记的内容，单文件策略保留原样（不做清洗）
        String withMarkdown = "```html\n<!DOCTYPE html><html></html>\n```";
        assertEquals(withMarkdown, htmlSingleStrategy.parseResult(withMarkdown));
    }

    // ==================== HtmlMultiStrategy ====================

    @Test
    void htmlMultiShouldAppendJsonFormatConstraint() {
        String basePrompt = "只输出纯净代码";
        String result = htmlMultiStrategy.buildSpecialPrompt(basePrompt, null);
        assertTrue(result.contains("JSON数组"));
        assertTrue(result.contains("\"path\""));
        assertTrue(result.contains("\"content\""));
    }

    @Test
    void htmlMultiShouldParseValidJsonArray() {
        String json = "[{\"path\":\"index.html\",\"content\":\"<html></html>\"}]";
        assertEquals(json, htmlMultiStrategy.parseResult(json));
    }

    @Test
    void htmlMultiShouldStripMarkdownJsonWrapper() {
        String withWrapper = "```json\n[{\"path\":\"index.html\",\"content\":\"<html></html>\"}]\n```";
        String result = htmlMultiStrategy.parseResult(withWrapper);
        assertTrue(result.startsWith("["));
        assertTrue(result.contains("index.html"));
        assertFalse(result.contains("```"));
    }

    @Test
    void htmlMultiShouldReturnOriginalOnInvalidJson() {
        String invalid = "这不是合法的JSON格式";
        assertEquals(invalid, htmlMultiStrategy.parseResult(invalid));
    }

    // ==================== StrategyFactory ====================

    @Test
    void factoryShouldReturnHtmlSingleForHtmlType() {
        CodeGenStrategy strategy = strategyFactory.getStrategy("HTML");
        assertInstanceOf(HtmlSingleStrategy.class, strategy);
    }

    @Test
    void factoryShouldReturnHtmlMultiForMultiFileType() {
        CodeGenStrategy strategy = strategyFactory.getStrategy("MULTI_FILE");
        assertInstanceOf(HtmlMultiStrategy.class, strategy);
    }

    @Test
    void factoryShouldFallbackToDefaultForUnknownType() {
        CodeGenStrategy strategy = strategyFactory.getStrategy("UNKNOWN");
        assertNotNull(strategy);
        assertInstanceOf(HtmlSingleStrategy.class, strategy);
    }

    @Test
    void factoryShouldFallbackForNullType() {
        CodeGenStrategy strategy = strategyFactory.getStrategy(null);
        assertNotNull(strategy);
    }

    // ==================== VueStrategy ====================

    @Test
    void vueStrategyShouldSupportVueType() {
        assertTrue(vueStrategy.support("VUE"));
        assertFalse(vueStrategy.support("HTML"));
        assertFalse(vueStrategy.support("MULTI_FILE"));
    }

    @Test
    void vueStrategyShouldAppendVueProjectConstraints() {
        String basePrompt = "只输出纯净代码";
        String result = vueStrategy.buildSpecialPrompt(basePrompt, null);
        assertNotNull(result);
        assertTrue(result.length() > basePrompt.length(), "Vue 策略应追加约束文本");
        assertTrue(result.contains("src/App.vue"), "应包含 Vue 工程入口文件路径");
        // 约束中应体现多文件 JSON 格式要求
        assertTrue(result.contains("JSON"), "应包含 JSON 格式约束");
    }

    @Test
    void vueStrategyShouldParseValidJsonArray() {
        String json = """
                [{"path":"index.html","content":"<html></html>"},\
                {"path":"src/App.vue","content":"<template></template>"}]""";
        assertEquals(json, vueStrategy.parseResult(json));
    }

    @Test
    void vueStrategyShouldStripMarkdownJsonWrapper() {
        String withWrapper = """
                ```json
                [{"path":"src/App.vue","content":"<template><div>Hello</div></template>"}]
                ```""";
        String result = vueStrategy.parseResult(withWrapper);
        assertTrue(result.startsWith("["));
        assertTrue(result.contains("App.vue"));
        assertFalse(result.contains("```"));
    }

    @Test
    void vueStrategyShouldReturnOriginalOnInvalidJson() {
        String invalid = "这是非法的 Vue 输出";
        assertEquals(invalid, vueStrategy.parseResult(invalid));
    }

    @Test
    void factoryShouldReturnVueStrategyForVueType() {
        CodeGenStrategy strategy = strategyFactory.getStrategy("VUE");
        assertInstanceOf(VueStrategy.class, strategy);
    }
}

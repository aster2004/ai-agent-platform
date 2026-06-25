package com.ai.agentplatform.module.codegen.strategy;

import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Vue 工程代码生成策略
 * 输出多文件 JSON 数组，含 App.vue / main.js / package.json 等标准 Vue 3 工程结构
 *
 * <p>与 HtmlMultiStrategy 共享多文件格式，差异化在 Prompt 约束：</p>
 * <ul>
 *   <li>必须使用 Vue 3 Composition API + <script setup></li>
 *   <li>CSS 使用 <style scoped> 内联</li>
 *   <li>依赖通过 CDN 引入或不依赖外部 UI 库</li>
 *   <li>目录结构: src/App.vue, src/main.js, index.html, package.json</li>
 * </ul>
 */
@Slf4j
@Component
public class VueStrategy implements CodeGenStrategy {

    /** Vue 3 多文件工程专属约束 */
    private static final String VUE_CONSTRAINT =
            "\n必须严格使用 Vue 3 Composition API（<script setup>）生成完整工程，规则："
                    + "1) 输出纯 JSON 数组，格式：[{\"path\":\"文件相对路径\",\"content\":\"文件完整代码\"}]；"
                    + "2) 必须包含以下文件：index.html、src/main.js、src/App.vue，可选补充其他组件文件；"
                    + "3) 所有 CSS 使用 <style scoped> 内联于 .vue 单文件组件中；"
                    + "4) 不依赖外部 UI 组件库（如 Element Plus / Ant Design），仅使用原生 HTML+CSS；"
                    + "5) 通过 CDN 引入 Vue 3（unpkg 或 jsdelivr），不依赖构建工具；"
                    + "6) 禁止任何 markdown 标记、代码块包裹、解释性文字；"
                    + "7) index.html 必须包含 CDN 引入 Vue 3 的 <script> 标签和挂载点 <div id=\"app\">。";

    @Override
    public boolean support(String generateType) {
        return CodeGenConstant.GENERATE_TYPE_VUE.equals(generateType);
    }

    @Override
    public String buildSpecialPrompt(String basePrompt, CodeGenRequest request) {
        return basePrompt + VUE_CONSTRAINT;
    }

    /**
     * 解析 AI 返回文本：清洗 markdown 代码块 → 校验 JSON 数组 → 返回合法 JSON 字符串
     * 解析失败原样返回，保证入库不中断
     */
    @Override
    public String parseResult(String aiRawText) {
        String cleanText = cleanMarkdownTag(aiRawText);
        try {
            JSONArray array = JSON.parseArray(cleanText);
            log.info("VueStrategy 解析成功，文件数={}", array.size());
            return cleanText;
        } catch (Exception e) {
            log.error("VueStrategy 解析失败，AI 返回格式非法，原始文本：{}", aiRawText, e);
            return aiRawText;
        }
    }

    /**
     * 移除 AI 自带的 ```json / ``` 标记
     */
    private String cleanMarkdownTag(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        if (result.startsWith("```json")) {
            result = result.replaceFirst("```json", "");
        } else if (result.startsWith("```")) {
            result = result.replaceFirst("```", "");
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.lastIndexOf("```"));
        }
        return result.trim();
    }
}

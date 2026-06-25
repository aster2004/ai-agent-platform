package com.ai.agentplatform.module.codegen.workflow.node;

import com.ai.agentplatform.module.codegen.workflow.state.CodeFile;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowState;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
import com.ai.agentplatform.module.codegen.workflow.tool.FileToolService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ChatModel.class)
public class GenerateNode implements NodeAction<WorkflowState> {

    private final ChatModel chatModel;
    private final FileToolService fileToolService;

    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("[Workflow] 节点: GENERATE 开始, strategy={}", state.strategy());
        if (!state.error().isBlank()) {
            return Map.of(
                    WorkflowState.CODE_FILES_KEY, List.of(),
                    WorkflowState.CURRENT_STEP_KEY, WorkflowStep.VALIDATE.getCode()
            );
        }
        fileToolService.reset();

        String requirement = buildRequirement(state);
        List<CodeFile> codeFiles = switch (state.strategy()) {
            case "VUE", "WORKFLOW" -> generateVueProject(requirement, state.summary());
            case "MULTI_FILE" -> generateMultiFileHtml(requirement);
            case "FULL_STACK" -> generateFullStackProject(requirement, state.summary());
            default -> generateSingleHtml(requirement);
        };

        log.info("[Workflow] 生成文件数: {}", codeFiles.size());
        return Map.of(
                WorkflowState.CODE_FILES_KEY, new ArrayList<>(codeFiles),
                WorkflowState.CURRENT_STEP_KEY, WorkflowStep.VALIDATE.getCode()
        );
    }

    private String buildRequirement(WorkflowState state) {
        if (!state.prd().isBlank()) {
            return state.prd();
        }
        return state.prompt();
    }

    private List<CodeFile> generateVueProject(String prompt, String summary) {
        VueGeneratorAgent agent = AiServices.builder(VueGeneratorAgent.class)
                .chatModel(chatModel)
                .tools(fileToolService)
                .build();

        agent.generate("""
                请根据需求生成一个最小可运行的 Vue 3 + Vite 工程。
                必须使用 createFile 工具创建至少以下文件：
                - package.json
                - index.html
                - vite.config.js
                - src/main.js
                - src/App.vue
                需求摘要：%s
                原始需求：%s
                """.formatted(summary, prompt));

        List<CodeFile> files = new ArrayList<>(fileToolService.snapshotFiles());
        if (files.isEmpty()) {
            files.addAll(defaultVueScaffold(prompt));
        }
        return files;
    }

    private List<CodeFile> generateMultiFileHtml(String prompt) {
        MultiFileAgent agent = AiServices.builder(MultiFileAgent.class)
                .chatModel(chatModel)
                .tools(fileToolService)
                .build();

        agent.generate("使用 createFile 生成多文件 HTML 项目（至少 index.html 和 styles.css）：\n" + prompt);
        List<CodeFile> files = new ArrayList<>(fileToolService.snapshotFiles());
        if (files.isEmpty()) {
            files.add(new CodeFile("index.html", chatModel.chat("生成完整 HTML 页面，需求：\n" + prompt)));
        }
        return files;
    }

    private List<CodeFile> generateSingleHtml(String prompt) {
        String html = chatModel.chat("""
                你是前端工程师，只输出完整 HTML 代码，不要 markdown 代码块。
                要求：
                1. 页面必须包含完整可交互 UI（表单、按钮、输入框等），不要只有背景色
                2. CSS 和 JS 全部内联在同一 HTML 文件中
                3. 可直接在浏览器打开运行，不依赖 Vue/Vite/构建工具
                需求：
                %s
                """.formatted(prompt));
        return List.of(new CodeFile("index.html", stripCodeFence(html)));
    }

    private List<CodeFile> generateFullStackProject(String prompt, String summary) {
        fileToolService.reset();
        FullStackAgent agent = AiServices.builder(FullStackAgent.class)
                .chatModel(chatModel)
                .tools(fileToolService)
                .build();

        agent.generate("""
                请生成前后端分离项目，必须使用 createFile 工具创建文件：
                前端（可预览）：
                - index.html（完整可交互页面，内联 CSS/JS，可直接浏览器打开）
                后端（Spring Boot）：
                - backend/src/main/java/com/example/Application.java
                - backend/src/main/java/com/example/controller/AppController.java
                - backend/src/main/java/com/example/service/AppService.java
                - backend/src/main/resources/application.yml
                - backend/pom.xml
                需求摘要：%s
                原始需求：%s
                """.formatted(summary, prompt));

        List<CodeFile> files = new ArrayList<>(fileToolService.snapshotFiles());
        if (files.stream().noneMatch(f -> "index.html".equals(f.getPath()))) {
            files.addAll(0, generateSingleHtml(prompt));
        }
        return files;
    }

    private List<CodeFile> defaultVueScaffold(String prompt) {
        return List.of(
                new CodeFile("package.json", """
                        {
                          "name": "ai-generated-vue-app",
                          "private": true,
                          "version": "0.0.1",
                          "scripts": { "dev": "vite", "build": "vite build" },
                          "dependencies": { "vue": "^3.4.0" },
                          "devDependencies": { "vite": "^5.0.0", "@vitejs/plugin-vue": "^5.0.0" }
                        }
                        """),
                new CodeFile("index.html", """
                        <!DOCTYPE html>
                        <html lang="zh-CN">
                        <head><meta charset="UTF-8"/><title>AI Vue App</title></head>
                        <body><div id="app"></div><script type="module" src="/src/main.js"></script></body>
                        </html>
                        """),
                new CodeFile("vite.config.js", """
                        import { defineConfig } from 'vite'
                        import vue from '@vitejs/plugin-vue'
                        export default defineConfig({ plugins: [vue()] })
                        """),
                new CodeFile("src/main.js", """
                        import { createApp } from 'vue'
                        import App from './App.vue'
                        createApp(App).mount('#app')
                        """),
                new CodeFile("src/App.vue", """
                        <template>
                          <main style="padding:24px;font-family:sans-serif">
                            <h1>AI 生成的 Vue 应用</h1>
                            <p>%s</p>
                          </main>
                        </template>
                        <script setup></script>
                        """.formatted(escapeForJava(prompt)))
        );
    }

    private static String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static String escapeForJava(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    interface VueGeneratorAgent {
        @SystemMessage("你是 Vue 工程脚手架生成器，必须通过 createFile/writeFile 工具创建文件，不要直接输出代码。")
        String generate(@UserMessage String prompt);
    }

    interface MultiFileAgent {
        @SystemMessage("你是多文件 HTML 生成器，必须通过 createFile 工具创建文件。")
        String generate(@UserMessage String prompt);
    }

    interface FullStackAgent {
        @SystemMessage("你是全栈工程师，必须通过 createFile 工具分别创建前端 index.html 与 Spring Boot 后端文件。")
        String generate(@UserMessage String prompt);
    }
}

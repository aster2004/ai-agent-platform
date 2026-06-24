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

        List<CodeFile> codeFiles = switch (state.strategy()) {
            case "VUE", "WORKFLOW" -> generateVueProject(state.prompt(), state.summary());
            case "MULTI_FILE" -> generateMultiFileHtml(state.prompt());
            default -> generateSingleHtml(state.prompt());
        };

        log.info("[Workflow] 生成文件数: {}", codeFiles.size());
        return Map.of(
                WorkflowState.CODE_FILES_KEY, new ArrayList<>(codeFiles),
                WorkflowState.CURRENT_STEP_KEY, WorkflowStep.VALIDATE.getCode()
        );
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
                你是前端工程师，只输出完整 HTML 代码，不要 markdown 代码块：
                %s
                """.formatted(prompt));
        return List.of(new CodeFile("index.html", stripCodeFence(html)));
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
}

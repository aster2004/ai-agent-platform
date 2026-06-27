package com.ai.agentplatform.module.codegen.workflow.node;

import com.ai.agentplatform.module.codegen.workflow.support.PrdTextSanitizer;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowState;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
import org.springframework.util.StringUtils;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ChatModel.class)
public class PrdNode implements NodeAction<WorkflowState> {

    private final ChatModel chatModel;

    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("[Workflow] 节点: PRD 开始");
        String prompt = state.prompt();
        String summary = state.summary();

        String prdPrompt = """
                你是产品经理，请根据用户需求输出一份 Markdown 格式的产品需求文档（PRD）。
                要求：
                1. 使用中文
                2. 包含以下章节（按顺序）：
                   ## 1. 应用概述
                   - 应用名称
                   - 应用简介
                   ## 2. 页面结构与功能说明
                   - 页面布局描述
                   - 各功能模块说明
                   ## 3. 交互与状态
                   - 用户操作流程
                   - 关键状态变化
                   ## 4. 实现要点（仅文字描述，不要写代码或技术栈列表）
                   - 关键交互与状态说明
                3. 严禁输出任何代码、HTML、CSS、JavaScript、Vue 片段或 markdown 代码块
                4. 不要包裹 markdown 代码围栏

                需求摘要：%s
                原始需求：%s
                """.formatted(summary, prompt);

        String prd = PrdTextSanitizer.sanitizePrd(chatModel.chat(prdPrompt).trim());
        if (!StringUtils.hasText(prd)) {
            prd = buildFallbackPrd(summary, prompt);
        }
        log.info("[Workflow] PRD 生成完成, 长度={}", prd.length());

        return Map.of(
                WorkflowState.PRD_KEY, prd,
                WorkflowState.CURRENT_STEP_KEY, WorkflowStep.PRD_READY.getCode()
        );
    }

    private static String buildFallbackPrd(String summary, String prompt) {
        String s = StringUtils.hasText(summary) ? summary : prompt;
        return """
                ## 1. 应用概述
                - 应用名称：AI 生成应用
                - 应用简介：%s

                ## 2. 页面结构与功能说明
                - 根据用户描述实现主要页面布局与核心功能模块

                ## 3. 交互与状态
                - 描述用户从打开页面到完成主要操作的流程

                ## 4. 实现要点
                - 页面需具备完整可交互 UI，避免空白占位
                """.formatted(s == null ? "" : s.trim());
    }
}

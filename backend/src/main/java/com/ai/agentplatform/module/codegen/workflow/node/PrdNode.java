package com.ai.agentplatform.module.codegen.workflow.node;

import com.ai.agentplatform.module.codegen.workflow.state.WorkflowState;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
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
                   ## 4. 技术建议
                   - 推荐技术栈（如 Vue 3）
                   - 关键实现要点
                3. 不要输出代码，只输出需求文档正文
                4. 不要包裹 markdown 代码块

                需求摘要：%s
                原始需求：%s
                """.formatted(summary, prompt);

        String prd = chatModel.chat(prdPrompt).trim();
        prd = stripCodeFence(prd);
        log.info("[Workflow] PRD 生成完成, 长度={}", prd.length());

        return Map.of(
                WorkflowState.PRD_KEY, prd,
                WorkflowState.CURRENT_STEP_KEY, WorkflowStep.PRD_READY.getCode()
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
}

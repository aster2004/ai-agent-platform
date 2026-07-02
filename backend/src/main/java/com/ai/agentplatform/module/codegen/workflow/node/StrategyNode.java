package com.ai.agentplatform.module.codegen.workflow.node;

import com.ai.agentplatform.module.codegen.workflow.state.WorkflowState;
import com.ai.agentplatform.module.codegen.workflow.state.WorkflowStep;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(ChatModel.class)
public class StrategyNode implements NodeAction<WorkflowState> {

    private static final Pattern REJECT_VUE_PATTERN = Pattern.compile(
            "(不要|不用|不使用|别用|禁止|无需|排除|不要写|别写).{0,8}(vue|vite|react|构建工具|npm|webpack)"
                    + "|(vue|vite|react).{0,8}(不要|不用|不使用|别用|禁止|无需|排除)",
            Pattern.CASE_INSENSITIVE);

    private static final String SYSTEM_STRATEGY_PROMPT = """
            根据用户需求，只输出一个策略类型，不要解释。
            可选值：HTML、VUE、MULTI_FILE、FULL_STACK、WORKFLOW
            规则：
            - 用户明确禁止 Vue/Vite/构建工具 → HTML 或 MULTI_FILE，不得选 VUE/WORKFLOW
            - 简单单页（登录页、计算器、Todo、简历、表单、展示页）且用户未明确要求 Vue 工程 → HTML
            - 用户明确要求 Vue/Vite/组件化工程 → VUE
            - 多页面静态站点 → MULTI_FILE
            - 用户要求后端、API、数据库、Spring Boot → FULL_STACK
            - 复杂多步骤流程且未禁止 Vue → WORKFLOW
            """;

    private final ChatModel chatModel;

    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("[Workflow] 节点: STRATEGY 开始");
        if (!state.error().isBlank()) {
            return Map.of(WorkflowState.CURRENT_STEP_KEY, WorkflowStep.VALIDATE.getCode());
        }
        String context = state.prd().isBlank() ? state.summary() : state.prd();
        String strategy = resolveStrategy(state.prompt(), context);
        log.info("[Workflow] 选定策略: {}", strategy);

        return Map.of(
                WorkflowState.STRATEGY_KEY, strategy,
                WorkflowState.CURRENT_STEP_KEY, WorkflowStep.GENERATE.getCode()
        );
    }

    private String resolveStrategy(String prompt, String summary) {
        String promptLower = prompt.toLowerCase(Locale.ROOT);
        String combined = (prompt + " " + summary).toLowerCase(Locale.ROOT);

        // 用户明确禁止 Vue/构建工具 → 优先 HTML（避免「不使用vue」被误判为 VUE）
        if (userRejectsVue(promptLower, combined)) {
            if (promptLower.contains("多文件") || promptLower.contains("multi") || promptLower.contains("多个页面")) {
                return "MULTI_FILE";
            }
            return "HTML";
        }

        // 用户原始需求优先：明确要求 Vue 工程才走 VUE
        if (userWantsVueProject(promptLower)) {
            return "VUE";
        }

        // 用户要求后端 → 全栈（前端 index.html + 后端代码，预览最稳定）
        if (userWantsBackend(promptLower, combined)) {
            return "FULL_STACK";
        }

        // 简单前端单页：登录、注册、计算器、待办清单、简历等 → HTML（iframe 可直接预览）
        if (isSimpleFrontendPage(promptLower)) {
            return "HTML";
        }

        if (promptLower.contains("多文件") || promptLower.contains("multi") || promptLower.contains("多个页面")) {
            return "MULTI_FILE";
        }
        if (combined.contains("工作流") || combined.contains("workflow") || combined.contains("流程")) {
            return "WORKFLOW";
        }

        String aiStrategy = chatModel.chat(SYSTEM_STRATEGY_PROMPT + "\n\n用户原始需求：\n" + prompt + "\n\n需求摘要/PRD：\n" + summary);
        String normalized = aiStrategy.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("FULL_STACK")) {
            return "FULL_STACK";
        }
        if (normalized.contains("VUE") && userWantsVueProject(promptLower)) {
            return "VUE";
        }
        if (normalized.contains("MULTI_FILE")) {
            return "MULTI_FILE";
        }
        if (normalized.contains("WORKFLOW") && !userRejectsVue(promptLower, combined)) {
            return "WORKFLOW";
        }
        return "HTML";
    }

    /** 用户明确拒绝 Vue/Vite/构建工具（含「不使用vue」等否定句） */
    private boolean userRejectsVue(String promptLower, String combined) {
        String text = promptLower + " " + (combined == null ? "" : combined);
        if (text.contains("不使用vue") || text.contains("不用vue") || text.contains("不要vue")
                || text.contains("禁止vue") || text.contains("无需vue") || text.contains("别用vue")) {
            return true;
        }
        return REJECT_VUE_PATTERN.matcher(text).find();
    }

    /** 用户明确要求 Vue/Vite 工程（正向意图，避免子串误判） */
    private boolean userWantsVueProject(String promptLower) {
        if (userRejectsVue(promptLower, "")) {
            return false;
        }
        if (promptLower.contains("vue3") || promptLower.contains("vue 3") || promptLower.contains("vite")) {
            return true;
        }
        if (promptLower.contains("vue项目") || promptLower.contains("vue 项目")
                || promptLower.contains("vue工程") || promptLower.contains("vue 工程")) {
            return true;
        }
        if (promptLower.matches(".*(使用|采用|基于|用|需要|写一个|搭建).*(vue|vite).*")) {
            return true;
        }
        return promptLower.contains("组件化") || promptLower.contains("前端工程") || promptLower.contains("前端项目");
    }

    private boolean userWantsBackend(String promptLower, String combined) {
        return promptLower.contains("后端") || promptLower.contains("backend")
                || promptLower.contains("spring") || promptLower.contains("api")
                || promptLower.contains("数据库") || promptLower.contains("服务端")
                || combined.contains("spring boot");
    }

    /** 典型可单 HTML 预览的简单前端页面 */
    private boolean isSimpleFrontendPage(String promptLower) {
        return promptLower.contains("登录") || promptLower.contains("login")
                || promptLower.contains("注册") || promptLower.contains("register")
                || promptLower.contains("计算器") || promptLower.contains("calculator")
                || promptLower.contains("todo") || promptLower.contains("待办")
                || promptLower.contains("简历") || promptLower.contains("resume")
                || promptLower.contains("个人主页") || promptLower.contains("portfolio")
                || promptLower.contains("landing") || promptLower.contains("官网")
                || promptLower.contains("简单") || promptLower.contains("单页")
                || promptLower.contains("静态页") || promptLower.contains("展示页")
                || promptLower.contains("网页") || promptLower.contains("页面")
                || (promptLower.contains("前端") && (promptLower.contains("页面") || promptLower.contains("代码")));
    }
}

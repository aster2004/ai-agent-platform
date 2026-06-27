package com.ai.agentplatform.module.codegen.service.tool;

import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import com.ai.agentplatform.module.codegen.service.helper.AppSyncHelper;
import com.ai.agentplatform.module.codegen.service.helper.ChatMemoryContext;
import com.ai.agentplatform.module.codegen.strategy.CodeGenStrategy;
import com.ai.agentplatform.module.codegen.strategy.CodeGenStrategyFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    @Resource
    private AppSyncHelper appSyncHelper;

    @Resource
    private CodeGenStrategyFactory strategyFactory;

    /**
     * @deprecated 请使用 {@link #buildPrompt(CodeGenRequest, ChatMemoryContext)}
     */
    @Deprecated
    public String buildPrompt(CodeGenRequest request, List<String> chatHistory) {
        return buildPrompt(request, new ChatMemoryContext(null, chatHistory));
    }

    /**
     * 构建完整 Prompt：系统规则 + Layer2 会话概要 + Layer1 近期对话 + 本次需求。
     */
    public String buildPrompt(CodeGenRequest request, ChatMemoryContext memoryContext) {
        ChatMemoryContext context = memoryContext != null ? memoryContext : ChatMemoryContext.empty();

        Long appId = request.getAppId();
        String generateType = request.getGenerateType();

        AppSyncHelper.AppConfigDTO appConfig = appSyncHelper.getAppConfig(appId);
        String system = appConfig.getPromptTemplate();

        CodeGenStrategy strategy = strategyFactory.getStrategy(generateType);
        system = strategy.buildSpecialPrompt(system, request);

        String safeUserPrompt = request.getPrompt();
        if (safeUserPrompt.length() > 5000) {
            safeUserPrompt = safeUserPrompt.substring(0, 5000) + "...";
        }

        String summarySection = buildSessionSummarySection(context.sessionSummary());
        String dialogueSection = buildRecentDialogueSection(context.recentDialogue());
        String baselineSection = buildIterationBaselineSection(context.iterationBaseline());

        return "【系统规则】" + system + "\n"
                + summarySection
                + dialogueSection
                + baselineSection
                + "【本次需求】" + safeUserPrompt;
    }

    private String buildIterationBaselineSection(String iterationBaseline) {
        if (iterationBaseline == null || iterationBaseline.isBlank()) {
            return "";
        }
        return """
                【当前代码基线】
                以下为用户当前版本的页面/代码。请严格在此基础上按【本次需求】做最小修改；
                保留未提及的布局、结构与样式，不要整页重写。
                
                """ + iterationBaseline.trim() + "\n";
    }

    private String buildSessionSummarySection(String sessionSummary) {
        if (sessionSummary == null || sessionSummary.isBlank()) {
            return "";
        }
        return "【会话概要】\n" + sessionSummary.trim() + "\n";
    }

    private String buildRecentDialogueSection(List<String> recentDialogue) {
        if (recentDialogue == null || recentDialogue.isEmpty()) {
            return "【近期对话】\n（无）\n";
        }
        StringBuilder sb = new StringBuilder("【近期对话】\n");
        for (String line : recentDialogue) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}

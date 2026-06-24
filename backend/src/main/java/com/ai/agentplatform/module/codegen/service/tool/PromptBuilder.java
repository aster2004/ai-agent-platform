package com.ai.agentplatform.module.codegen.service.tool;

import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import com.ai.agentplatform.module.codegen.service.helper.AppSyncHelper;
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
     * 构建完整 Prompt（系统规则 + 类型约束 + 策略专属格式 + 历史上下文 + 用户需求）
     *
     * @param request      前端请求参数（含 prompt、generateType、appId 等）
     * @param chatHistory  多轮对话历史文本列表
     * @return 拼接后的最终 Prompt
     */
    public String buildPrompt(CodeGenRequest request, List<String> chatHistory) {
        Long appId = request.getAppId();
        String generateType = request.getGenerateType();

        // 1. 应用自定义系统提示（当前为 Mock，D5 改为真实查询）
        AppSyncHelper.AppConfigDTO appConfig = appSyncHelper.getAppConfig(appId);
        String system = appConfig.getPromptTemplate();

        // 2. 由策略补充类型专属格式约束（替代原来的 switch，策略自行决定追加内容）
        CodeGenStrategy strategy = strategyFactory.getStrategy(generateType);
        system = strategy.buildSpecialPrompt(system, request);

        // 3. 超长文本截断
        String safeUserPrompt = request.getPrompt();
        if (safeUserPrompt.length() > 5000) {
            safeUserPrompt = safeUserPrompt.substring(0, 5000) + "...";
        }

        // 4. 拼接历史对话
        StringBuilder historyText = new StringBuilder();
        if (chatHistory != null && !chatHistory.isEmpty()) {
            historyText.append("历史对话：");
            chatHistory.forEach(item -> historyText.append(item).append("；"));
        }

        // 5. 最终完整 Prompt
        return String.format("【系统规则】%s\n【历史上下文】%s\n【用户需求】%s",
                system, historyText, safeUserPrompt);
    }
}
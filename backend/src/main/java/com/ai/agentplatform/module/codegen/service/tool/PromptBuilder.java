package com.ai.agentplatform.module.codegen.service.tool;

import com.ai.agentplatform.module.codegen.service.helper.AppSyncHelper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class PromptBuilder {

    @Resource
    private AppSyncHelper appSyncHelper;

    /**
     * 拼接完整大模型Prompt
     */
    public String buildPrompt(String userPrompt, String generateType, Long appId, List<String> chatHistory) {
        // 1. 获取应用系统提示词（Mock）
        AppSyncHelper.AppConfigDTO appConfig = appSyncHelper.getAppConfig(appId);
        String systemPrompt = appConfig.getPromptTemplate();
        // 修复：之前写的system变量不存在，统一用systemPrompt
        String system = systemPrompt;

        // 2. 根据生成类型追加专属约束
        switch (generateType) {
            case "HTML":
                system += " 纯HTML+内联CSS，不引入外部JS框架";
                break;
            case "VUE":
                system += " Vue3组合式API，Element Plus规范代码";
                break;
            case "MULTI_FILE":
                system += " 分文件输出，标注每个文件路径与内容";
                break;
            case "WORKFLOW":
                system += " 输出LangGraph工作流节点代码";
                break;
            default:
                system += " 标准业务代码，结构清晰";
        }

        // 3. 超长文本截断（上限5000字符）
        String safeUserPrompt = userPrompt.length() > 5000 ? userPrompt.substring(0,5000)+"..." : userPrompt;

        // 4. 拼接历史对话
        StringBuilder historyText = new StringBuilder();
        if (!chatHistory.isEmpty()) {
            historyText.append("历史对话：");
            chatHistory.forEach(item -> historyText.append(item).append("；"));
        }

        // 5. 最终完整prompt
        return String.format("【系统规则】%s\n【历史上下文】%s\n【用户需求】%s", system, historyText, safeUserPrompt);
    }
}
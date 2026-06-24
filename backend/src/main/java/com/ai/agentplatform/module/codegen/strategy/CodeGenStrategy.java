package com.ai.agentplatform.module.codegen.strategy;

import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import java.util.List;

/**
 * 代码生成策略顶层接口
 * HTML单文件 / MULTI_FILE多文件 分别实现
 */
public interface CodeGenStrategy {
    /**
     * 判断当前策略是否匹配生成类型
     */
    boolean support(String generateType);

    /**
     * 构建差异化最终Prompt
     */
    String buildSpecialPrompt(String basePrompt, CodeGenRequest request);

    /**
     * 解析大模型返回结果，统一返回完整文本（多文件转为JSON字符串）
     */
    String parseResult(String aiRawText);
}
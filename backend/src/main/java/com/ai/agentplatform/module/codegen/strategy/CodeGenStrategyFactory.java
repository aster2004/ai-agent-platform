package com.ai.agentplatform.module.codegen.strategy;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CodeGenStrategyFactory {
    @Resource
    List<CodeGenStrategy> strategyList;

    /**
     * 根据生成类型匹配对应策略实现
     */
    public CodeGenStrategy getStrategy(String generateType) {
        for (CodeGenStrategy strategy : strategyList) {
            if (strategy.support(generateType)) {
                return strategy;
            }
        }
        // 默认兜底单文件策略
        return strategyList.stream()
                .filter(s -> s instanceof HtmlSingleStrategy)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("无匹配生成策略"));
    }
}
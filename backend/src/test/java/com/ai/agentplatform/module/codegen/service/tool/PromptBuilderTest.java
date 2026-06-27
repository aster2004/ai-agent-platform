package com.ai.agentplatform.module.codegen.service.tool;

import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import com.ai.agentplatform.module.codegen.service.helper.AppSyncHelper;
import com.ai.agentplatform.module.codegen.service.helper.ChatMemoryContext;
import com.ai.agentplatform.module.codegen.strategy.CodeGenStrategy;
import com.ai.agentplatform.module.codegen.strategy.CodeGenStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptBuilderTest {

    @Mock
    private AppSyncHelper appSyncHelper;

    @Mock
    private CodeGenStrategyFactory strategyFactory;

    @Mock
    private CodeGenStrategy strategy;

    @InjectMocks
    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        AppSyncHelper.AppConfigDTO config = new AppSyncHelper.AppConfigDTO();
        config.setPromptTemplate("你是代码助手");
        when(appSyncHelper.getAppConfig(any())).thenReturn(config);
        when(strategyFactory.getStrategy(any())).thenReturn(strategy);
        when(strategy.buildSpecialPrompt(any(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void buildPromptIncludesSummaryAndRecentDialogue() {
        CodeGenRequest request = new CodeGenRequest();
        request.setPrompt("标题改红色");
        request.setAppId(1L);
        request.setGenerateType("HTML");

        ChatMemoryContext context = new ChatMemoryContext(
                "用户在做日历页。",
                List.of("user: 做日历", "ai: [生成] HTML 单页")
        );

        String prompt = promptBuilder.buildPrompt(request, context);

        assertTrue(prompt.contains("【会话概要】"));
        assertTrue(prompt.contains("用户在做日历页。"));
        assertTrue(prompt.contains("【近期对话】"));
        assertTrue(prompt.contains("user: 做日历"));
        assertTrue(prompt.contains("【本次需求】标题改红色"));
    }

    @Test
    void buildPromptOmitsSummarySectionWhenEmpty() {
        CodeGenRequest request = new CodeGenRequest();
        request.setPrompt("做一个待办");
        request.setAppId(1L);
        request.setGenerateType("HTML");

        String prompt = promptBuilder.buildPrompt(request, ChatMemoryContext.empty());

        assertFalse(prompt.contains("【会话概要】"));
        assertTrue(prompt.contains("【近期对话】"));
        assertTrue(prompt.contains("（无）"));
        assertTrue(prompt.contains("【本次需求】做一个待办"));
    }

    @Test
    void buildPromptIncludesIterationBaseline() {
        CodeGenRequest request = new CodeGenRequest();
        request.setPrompt("只把背景换成深蓝色");
        request.setAppId(1L);
        request.setGenerateType("HTML");

        ChatMemoryContext context = new ChatMemoryContext(
                null,
                List.of("user: 做日历"),
                "<html><body class=\"calendar\">旧版</body></html>"
        );

        String prompt = promptBuilder.buildPrompt(request, context);

        assertTrue(prompt.contains("【当前代码基线】"));
        assertTrue(prompt.contains("最小修改"));
        assertTrue(prompt.contains("旧版"));
        assertTrue(prompt.contains("【本次需求】只把背景换成深蓝色"));
    }
}

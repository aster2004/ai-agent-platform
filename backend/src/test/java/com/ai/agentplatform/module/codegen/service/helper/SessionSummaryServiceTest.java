package com.ai.agentplatform.module.codegen.service.helper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSummaryServiceTest {

    private final SessionSummaryService service = new SessionSummaryService(null, null, null);

    @Test
    void buildFallbackSummaryMergesOldAndRecent() {
        String result = service.buildFallbackSummary(
                "用户在做日历页。",
                List.of("user: 改背景色", "ai: [生成] 已更新样式"));

        assertTrue(result.contains("日历"));
        assertTrue(result.contains("改背景色"));
    }
}

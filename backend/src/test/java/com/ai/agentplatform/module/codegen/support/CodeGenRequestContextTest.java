package com.ai.agentplatform.module.codegen.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeGenRequestContextTest {

    @AfterEach
    void tearDown() {
        CodeGenRequestContext.clear();
    }

    @Test
    void runWithAuthorization_makesTokenAvailableInNestedRunnable() {
        assertNull(CodeGenRequestContext.resolveAuthorization());

        CodeGenRequestContext.runWithAuthorization("Bearer user-token-42", () -> {
            assertEquals("Bearer user-token-42", CodeGenRequestContext.resolveAuthorization());

            CodeGenRequestContext.runWithAuthorization("Bearer nested", () ->
                    assertEquals("Bearer nested", CodeGenRequestContext.resolveAuthorization()));
        });

        assertNull(CodeGenRequestContext.resolveAuthorization());
    }
}

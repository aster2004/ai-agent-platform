package com.ai.agentplatform.module.codegen.service.helper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeFingerprintExtractorTest {

    @Test
    void extractsJsonFiles() {
        String json = """
                [{"path":"index.html","content":"<html><title>Todo</title></html>"},
                 {"path":"app.js","content":"console.log(1)"}]
                """;
        CodeFingerprintExtractor.Fingerprint fp = CodeFingerprintExtractor.extract(json);
        assertEquals(2, fp.fileNames().size());
        assertTrue(fp.mainSnippet().contains("Todo"));
    }

    @Test
    void describesGenerateType() {
        assertEquals("HTML 单页", CodeFingerprintExtractor.describeGenerateType("HTML"));
        assertEquals("Vue 多文件项目", CodeFingerprintExtractor.describeGenerateType("VUE"));
    }
}

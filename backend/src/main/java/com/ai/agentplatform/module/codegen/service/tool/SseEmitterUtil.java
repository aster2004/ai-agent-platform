package com.ai.agentplatform.module.codegen.service.tool;

import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;

@Slf4j
@Component
public class SseEmitterUtil {
    // 连接超时3分钟
    private static final long TIME_OUT = CodeGenConstant.SSE_TIMEOUT_MS;

    /**
     * 创建SSE发射器，绑定超时/断连/异常回调
     */
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(TIME_OUT);
        // 客户端正常关闭
        emitter.onCompletion(() -> log.info("SSE连接正常关闭"));
        // 连接超时
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时");
            emitter.complete();
        });
        // 连接异常
        emitter.onError(e -> {
            log.error("SSE连接异常", e);
            emitter.completeWithError(e);
        });
        return emitter;
    }

    /**
     * 推送单段代码片段
     */
    public void sendChunk(SseEmitter emitter, String content) throws IOException {
        emitter.send(SseEmitter.event()
                .name(CodeGenConstant.SSE_EVENT_CODE_CHUNK)
                .data(content)
                .id(String.valueOf(System.currentTimeMillis())));
    }

    /**
     * 推送生成完成标识，附带策略清洗后的最终文本（前端用此替换流式拼接的原始 token 流）
     */
    public void sendFinish(SseEmitter emitter, String finalText) throws IOException {
        emitter.send(SseEmitter.event()
                .name(CodeGenConstant.SSE_EVENT_FINISH)
                .data(finalText != null ? finalText : ""));
        emitter.complete();
    }

    /**
     * 推送文件创建通知（仅 GENERAL 模式，AI 通过 createFile 工具创建文件时触发）
     */
    public void sendFileCreated(SseEmitter emitter, String filePath) throws IOException {
        emitter.send(SseEmitter.event()
                .name(CodeGenConstant.SSE_EVENT_FILE_CREATED)
                .data(filePath));
    }

    /**
     * 推送错误消息
     */
    public void sendError(SseEmitter emitter, String errMsg) throws IOException {
        emitter.send(SseEmitter.event().name(CodeGenConstant.SSE_EVENT_ERROR).data("生成失败："+errMsg));
        emitter.completeWithError(new RuntimeException(errMsg));
    }
}
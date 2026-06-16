package com.ai.agentplatform.module.codegen.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ChatModel.class)
public class CodeGenService {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的全栈代码生成助手。
            用户会描述他们的需求，你需要分析需求并生成高质量的代码。
            请使用 Markdown 格式输出，包含代码块和必要的说明。
            """;

    public String generate(String prompt) {
        return chatModel.chat(SYSTEM_PROMPT + "\n\n用户需求：\n" + prompt);
    }

    public SseEmitter generateStream(String prompt) {
        SseEmitter emitter = new SseEmitter(300_000L);
        String fullPrompt = SYSTEM_PROMPT + "\n\n用户需求：\n" + prompt;

        streamingChatModel.chat(fullPrompt, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                try {
                    emitter.send(SseEmitter.event().data(partialResponse));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                emitter.complete();
            }

            @Override
            public void onError(Throwable error) {
                log.error("流式生成失败", error);
                emitter.completeWithError(error);
            }
        });

        return emitter;
    }
}

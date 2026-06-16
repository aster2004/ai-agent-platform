package com.ai.agentplatform.module.codegen.controller;

import com.ai.agentplatform.common.result.Result;
import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import com.ai.agentplatform.module.codegen.service.CodeGenService;
import dev.langchain4j.model.chat.ChatModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "代码生成")
@RestController
@RequestMapping("/api/codegen")
@RequiredArgsConstructor
@ConditionalOnBean(ChatModel.class)
public class CodeGenController {

    private final CodeGenService codeGenService;

    @Operation(summary = "同步代码生成")
    @PostMapping
    public Result<String> generate(@Valid @RequestBody CodeGenRequest request) {
        return Result.success(codeGenService.generate(request.getPrompt()));
    }

    @Operation(summary = "流式代码生成")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateStream(@Valid @RequestBody CodeGenRequest request) {
        return codeGenService.generateStream(request.getPrompt());
    }
}

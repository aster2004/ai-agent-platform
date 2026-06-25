package com.ai.agentplatform.module.chat.controller;

import com.ai.agentplatform.common.result.Result;
import com.ai.agentplatform.module.chat.dto.ChatSaveRequest;
import com.ai.agentplatform.module.chat.dto.ChatSessionCreateRequest;
import com.ai.agentplatform.module.chat.service.ChatService;
import com.ai.agentplatform.module.chat.vo.ChatHistoryVO;
import com.ai.agentplatform.module.chat.vo.ChatMemoryMessageVO;
import com.ai.agentplatform.module.chat.vo.ChatSaveVO;
import com.ai.agentplatform.module.chat.vo.ChatSessionVO;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Tag(name = "对话历史")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final Long DEFAULT_USER_ID = 1L;

    private final ChatService chatService;

    @Operation(summary = "保存消息")
    @PostMapping("/save")
    public Result<ChatSaveVO> save(@Valid @RequestBody ChatSaveRequest request) {
        return Result.success(chatService.saveMessage(request, DEFAULT_USER_ID));
    }

    @Operation(summary = "查询会话历史")
    @GetMapping("/history")
    public Result<ChatHistoryVO> history(@RequestParam Long sessionId,
                                         @RequestParam(required = false) Long cursor,
                                         @RequestParam(defaultValue = "20") int size) {
        return Result.success(chatService.getHistory(sessionId, DEFAULT_USER_ID, cursor, size));
    }

    @Operation(summary = "会话列表")
    @GetMapping("/sessions")
    public Result<Page<ChatSessionVO>> sessions(@RequestParam(required = false) Long appId,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(chatService.listSessions(DEFAULT_USER_ID, appId, page, size));
    }

    @Operation(summary = "新建空会话")
    @PostMapping("/session")
    public Result<ChatSessionVO> createSession(@RequestBody(required = false) ChatSessionCreateRequest request) {
        if (request == null) {
            request = new ChatSessionCreateRequest();
        }
        return Result.success(chatService.createSession(request, DEFAULT_USER_ID));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/session/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(id, DEFAULT_USER_ID);
        return Result.success();
    }

    @Operation(summary = "读取 Redis 对话记忆", description = "供 Step1 自测及成员4 codegen 联调前验证；读 Redis 非 MySQL")
    @GetMapping("/memory/{sessionId}")
    public Result<List<ChatMemoryMessageVO>> memory(@PathVariable Long sessionId) {
        return Result.success(chatService.getMemory(sessionId, DEFAULT_USER_ID));
    }
}

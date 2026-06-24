package com.ai.agentplatform.module.codegen.controller;

import com.ai.agentplatform.common.result.Result;
import com.ai.agentplatform.module.codegen.constant.CodeGenConstant;
import com.ai.agentplatform.module.codegen.dto.CodeGenAsyncRequest;
import com.ai.agentplatform.module.codegen.dto.CodeGenBatchRequest;
import com.ai.agentplatform.module.codegen.dto.CodeGenRequest;
import com.ai.agentplatform.module.codegen.service.CodeGenService;
import com.ai.agentplatform.module.codegen.vo.CodeGenPageVO;
import com.ai.agentplatform.module.codegen.vo.CodeGenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/codegen")
@Tag(name = "AI代码生成模块", description = "同步/流式代码生成、历史记录查询接口")
public class CodeGenController {

    @Resource
    private CodeGenService codeGenService;

    /**
     * 1. 同步一次性生成完整代码
     * JWT 鉴权由 CodeGenSecurityConfig + 成员1 JwtAuthFilter 统一处理
     */
    @PostMapping("/generate")
    @Operation(summary = "同步代码生成", description = "一次性调用大模型返回完整代码，自动入库生成记录")
    public Result<CodeGenVO> generate(@Valid @RequestBody CodeGenRequest request) {
        CodeGenVO vo = codeGenService.generateSync(request);
        return Result.success(vo);
    }

    /**
     * 2. SSE长连接流式分段生成代码
     * JWT 鉴权由 CodeGenSecurityConfig + 成员1 JwtAuthFilter 统一处理
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "SSE流式代码生成", description = "长连接实时分片推送代码片段，全部输出完成自动入库")
    public SseEmitter generateStream(@Valid @RequestBody CodeGenRequest request) {
        return codeGenService.generateStream(request);
    }

    /**
     * 3. 分页查询当前用户代码生成历史记录
     * JWT 鉴权由 CodeGenSecurityConfig + 成员1 JwtAuthFilter 统一处理
     */
    @GetMapping("/record/list")
    @Operation(summary = "分页查询生成记录", description = "分页查询当前登录用户所有代码生成记录")
    public Result<CodeGenPageVO> recordList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        // 补充参数校验
        if (pageNum < CodeGenConstant.PAGE_MIN_NUM) {
            return Result.fail("页码不能小于1");
        }
        if (pageSize < 1 || pageSize > CodeGenConstant.PAGE_MAX_SIZE) {
            return Result.fail("每页条数需在1-100之间");
        }
        CodeGenPageVO pageVO = codeGenService.pageRecord(pageNum, pageSize);
        return Result.success(pageVO);
    }

    // ========== 预留迭代接口（D13系统优化阶段实现，当前返回 501） ==========

    @PostMapping("/batch")
    @Operation(summary = "【预留】批量代码生成", description = "D13迭代开发，当前返回 501 未实现")
    public Result<String> batchGenerate(@Valid @RequestBody CodeGenBatchRequest request) {
        return Result.fail(501, "批量代码生成功能将在 D13 迭代中实现");
    }

    @PostMapping("/async")
    @Operation(summary = "【预留】后台异步代码生成", description = "D13迭代开发，当前返回 501 未实现")
    public Result<String> asyncGenerate(@Valid @RequestBody CodeGenAsyncRequest request) {
        return Result.fail(501, "后台异步生成功能将在 D13 迭代中实现");
    }
}
package com.ai.agentplatform.module.app.controller;

import com.ai.agentplatform.common.result.Result;
import com.ai.agentplatform.module.app.dto.AppCreateRequest;
import com.ai.agentplatform.module.app.service.AppService;
import com.ai.agentplatform.module.app.vo.AppVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Tag(name = "应用管理")
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppController {

    private static final Long DEFAULT_USER_ID = 1L;

    private final AppService appService;

    @Operation(summary = "创建应用")
    @PostMapping
    public Result<AppVO> create(@Valid @RequestBody AppCreateRequest request) {
        return Result.success(appService.create(request, DEFAULT_USER_ID));
    }

    @Operation(summary = "应用列表")
    @GetMapping("/list")
    public Result<Page<AppVO>> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return Result.success(appService.listByUser(DEFAULT_USER_ID, page, size));
    }

    @Operation(summary = "应用详情")
    @GetMapping("/{id}")
    public Result<AppVO> getById(@PathVariable Long id) {
        return Result.success(appService.getById(id));
    }

    @Operation(summary = "删除应用")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appService.delete(id);
        return Result.success();
    }
}

package com.ai.agentplatform.module.app.controller;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.common.result.Result;
import com.ai.agentplatform.common.util.SecurityUtils;
import com.ai.agentplatform.module.app.dto.AppCodeUpdateRequest;
import com.ai.agentplatform.module.app.dto.AppCreateRequest;
import com.ai.agentplatform.module.app.dto.AppFeaturedUpdateRequest;
import com.ai.agentplatform.module.app.dto.AppUpdateRequest;
import com.ai.agentplatform.module.app.service.AppCoverService;
import com.ai.agentplatform.module.app.service.AppService;
import com.ai.agentplatform.module.app.vo.AppVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "应用管理")
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;
    private final AppCoverService appCoverService;

    @Operation(summary = "创建应用")
    @PostMapping
    public Result<AppVO> create(@Valid @RequestBody AppCreateRequest request) {
        return Result.success(appService.create(request, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "我的应用列表")
    @GetMapping("/list")
    public Result<Page<AppVO>> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return Result.success(appService.listByUser(SecurityUtils.getCurrentUserId(), page, size));
    }

    @Operation(summary = "管理员：全站应用列表")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/list")
    public Result<Page<AppVO>> adminList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer isFeatured) {
        return Result.success(appService.listAllForAdmin(page, size, isFeatured));
    }

    @Operation(summary = "精选应用列表")
    @GetMapping("/featured")
    public Result<List<AppVO>> listFeatured() {
        return Result.success(appService.listFeatured());
    }

    @Operation(summary = "应用详情")
    @GetMapping("/{id}")
    public Result<AppVO> getById(@PathVariable Long id) {
        return Result.success(appService.getById(id));
    }

    @Operation(summary = "编辑应用")
    @PutMapping("/{id}")
    public Result<AppVO> update(@PathVariable Long id,
                                @Valid @RequestBody AppUpdateRequest request) {
        return Result.success(appService.update(id, request, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "管理员：设精选 / 取消精选")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/featured")
    public Result<AppVO> setFeatured(
            @PathVariable Long id,
            @RequestBody AppFeaturedUpdateRequest request) {
        boolean featured;
        try {
            featured = request.resolveFeatured();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
        return Result.success(appService.setFeatured(id, featured));
    }

    @Operation(summary = "上传应用封面")
    @PostMapping("/upload/cover")
    public Result<String> uploadCover(@RequestParam("file") MultipartFile file) {
        return Result.success(appCoverService.saveCover(file));
    }

    @Operation(summary = "更新应用代码")
    @PutMapping("/{id}/code")
    public Result<AppVO> updateCode(@PathVariable Long id,
                                    @Valid @RequestBody AppCodeUpdateRequest request) {
        return Result.success(appService.updateCode(id, request));
    }

    @Operation(summary = "下架应用")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appService.delete(id, SecurityUtils.getCurrentUserId());
        return Result.success();
    }
}

package com.ai.agentplatform.module.app.deploy.controller;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.common.result.Result;
import com.ai.agentplatform.module.app.deploy.dto.DeployRequest;
import com.ai.agentplatform.module.app.deploy.service.AppDeployService;
import com.ai.agentplatform.module.app.deploy.service.AppDownloadService;
import com.ai.agentplatform.module.app.deploy.service.AppPreviewService;
import com.ai.agentplatform.module.app.deploy.service.CoverScreenshotService;
import com.ai.agentplatform.module.app.deploy.vo.DeployModeVO;
import com.ai.agentplatform.module.app.deploy.vo.DeployResultVO;
import com.ai.agentplatform.module.app.deploy.vo.PreviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "应用部署分享", description = "成员3：预览、部署、下载、封面截图")
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppDeployController {

    private final AppPreviewService appPreviewService;
    private final AppDeployService appDeployService;
    private final AppDownloadService appDownloadService;
    private final ObjectProvider<CoverScreenshotService> coverScreenshotServiceProvider;

    @Operation(summary = "获取应用预览地址", description = "将 app_code 写入本地 preview 目录并返回 iframe 可用 URL")
    @GetMapping("/{id}/preview")
    public Result<PreviewVO> preview(@PathVariable Long id) throws IOException {
        return Result.success(appPreviewService.buildPreview(id));
    }

    @Operation(summary = "下载应用源码", description = "将 app_code 打包为 zip 下载")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
        Resource resource = appDownloadService.buildZipResource(id);
        String filename = URLEncoder.encode(resource.getFilename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "获取支持的部署方式")
    @GetMapping("/deploy/modes")
    public Result<List<DeployModeVO>> deployModes() {
        return Result.success(appDeployService.listDeployModes());
    }

    @Operation(summary = "一键部署", description = "支持 local / nginx / docker 三种方式")
    @PostMapping("/{id}/deploy")
    public Result<DeployResultVO> deploy(@PathVariable Long id,
                                         @RequestBody(required = false) @Valid DeployRequest request)
            throws IOException, InterruptedException {
        String mode = request != null && request.getMode() != null ? request.getMode() : "local";
        return Result.success(appDeployService.deploy(id, mode));
    }

    @Operation(summary = "获取部署地址")
    @GetMapping("/{id}/deploy-url")
    public Result<DeployResultVO> deployUrl(@PathVariable Long id) {
        return Result.success(appDeployService.getDeployInfo(id));
    }

    @Operation(summary = "生成封面截图", description = "P2 功能，需 app.deploy.screenshot-enabled=true")
    @PostMapping("/{id}/cover")
    public Result<?> captureCover(@PathVariable Long id) throws IOException, InterruptedException {
        CoverScreenshotService coverService = coverScreenshotServiceProvider.getIfAvailable();
        if (coverService == null) {
            throw new BusinessException("封面截图未启用，请在配置中设置 app.deploy.screenshot-enabled=true");
        }
        return Result.success(coverService.captureCover(id));
    }
}

package com.ai.agentplatform.module.app.deploy.service;

import com.ai.agentplatform.module.app.deploy.dto.AppCodeFile;
import com.ai.agentplatform.module.app.deploy.repository.AppDeployAccessor;
import com.ai.agentplatform.module.app.deploy.support.ShareUrlResolver;
import com.ai.agentplatform.module.app.deploy.vo.PreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppPreviewService {

    private static final String PREVIEW_SUB_DIR = "preview";

    private final AppDeployAccessor appDeployAccessor;
    private final AppCodeFileService appCodeFileService;
    private final ShareUrlResolver shareUrlResolver;

    public PreviewVO buildPreview(Long appId) throws IOException {
        var info = appDeployAccessor.findAppDeployInfo(appId);
        if (info == null) {
            throw new com.ai.agentplatform.common.exception.BusinessException("应用不存在");
        }
        List<AppCodeFile> files = appCodeFileService.resolveCodeFiles(appId);
        appCodeFileService.writeFiles(appId, PREVIEW_SUB_DIR, files);
        String entryPath = resolveEntryPath(files);
        String previewPath = "/preview/" + appId + "/" + entryPath;
        String coverImg = (String) info.get("coverImg");
        String publicCover = StringUtils.hasText(coverImg)
                ? shareUrlResolver.buildShareUrl(shareUrlResolver.normalizeToPath(
                        coverImg.startsWith("/") ? coverImg : "/" + coverImg))
                : "";
        return new PreviewVO(
                appId,
                (String) info.get("appName"),
                shareUrlResolver.buildShareUrl(previewPath),
                publicCover
        );
    }

    private String resolveEntryPath(List<AppCodeFile> files) {
        for (AppCodeFile file : files) {
            if ("preview.html".equals(file.getPath())) {
                return file.getPath();
            }
        }
        for (AppCodeFile file : files) {
            String path = file.getPath();
            if ("index.html".equals(path)) {
                return path;
            }
        }
        for (AppCodeFile file : files) {
            String path = file.getPath();
            if (path != null && path.endsWith(".html")) {
                return path;
            }
        }
        return "index.html";
    }
}

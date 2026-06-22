package com.ai.agentplatform.module.app.deploy.service.strategy;

import com.ai.agentplatform.module.app.deploy.dto.AppCodeFile;
import com.ai.agentplatform.module.app.deploy.enums.DeployMode;
import com.ai.agentplatform.module.app.deploy.service.AppCodeFileService;
import com.ai.agentplatform.module.app.deploy.support.ShareUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LocalDeployStrategy implements DeployStrategy {

    private static final String SITES_SUB_DIR = "sites";

    private final AppCodeFileService appCodeFileService;
    private final ShareUrlResolver shareUrlResolver;

    @Override
    public DeployMode mode() {
        return DeployMode.LOCAL;
    }

    @Override
    public DeployOutcome deploy(Long appId, List<AppCodeFile> files, String entryPath) throws IOException {
        appCodeFileService.writeFiles(appId, SITES_SUB_DIR, files);
        String deployPath = "/sites/" + appId + "/" + entryPath;
        String shareUrl = shareUrlResolver.buildShareUrl(deployPath);
        return new DeployOutcome(deployPath, shareUrl, "本地部署成功（Spring Boot /sites）");
    }
}

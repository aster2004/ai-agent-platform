package com.ai.agentplatform.module.app.deploy.service;

import com.ai.agentplatform.module.app.deploy.dto.AppCodeFile;
import com.ai.agentplatform.module.app.deploy.repository.AppDeployAccessor;
import com.ai.agentplatform.module.app.deploy.vo.DeployResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppDeployService {

    private static final String SITES_SUB_DIR = "sites";

    private final AppDeployAccessor appDeployAccessor;
    private final AppCodeFileService appCodeFileService;

    @Transactional
    public DeployResultVO deploy(Long appId) throws IOException {
        appDeployAccessor.requireAppExists(appId);
        List<AppCodeFile> files = appCodeFileService.resolveCodeFiles(appId);
        appCodeFileService.writeFiles(appId, SITES_SUB_DIR, files);

        String entryPath = resolveEntryPath(files);
        String deployPath = "/sites/" + appId + "/" + entryPath;
        String deployUrl = appCodeFileService.buildPublicUrl(deployPath);
        appDeployAccessor.updateDeployUrl(appId, deployUrl);

        return new DeployResultVO(appId, deployUrl, "部署成功");
    }

    public String getDeployUrl(Long appId) {
        var info = appDeployAccessor.findAppDeployInfo(appId);
        if (info == null) {
            return "";
        }
        return (String) info.get("deployUrl");
    }

    private String resolveEntryPath(List<AppCodeFile> files) {
        for (AppCodeFile file : files) {
            if ("index.html".equals(file.getPath())) {
                return file.getPath();
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

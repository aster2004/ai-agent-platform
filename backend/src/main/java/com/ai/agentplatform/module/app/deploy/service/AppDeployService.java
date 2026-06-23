package com.ai.agentplatform.module.app.deploy.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.app.deploy.dto.AppCodeFile;
import com.ai.agentplatform.module.app.deploy.dto.DeployRequest;
import com.ai.agentplatform.module.app.deploy.enums.DeployMode;
import com.ai.agentplatform.module.app.deploy.repository.AppDeployAccessor;
import com.ai.agentplatform.module.app.deploy.service.strategy.DeployOutcome;
import com.ai.agentplatform.module.app.deploy.service.strategy.DeployStrategy;
import com.ai.agentplatform.module.app.deploy.support.DeployUrlCodec;
import com.ai.agentplatform.module.app.deploy.support.ShareUrlResolver;
import com.ai.agentplatform.module.app.deploy.vo.DeployModeVO;
import com.ai.agentplatform.module.app.deploy.vo.DeployResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppDeployService {

    private final AppDeployAccessor appDeployAccessor;
    private final AppCodeFileService appCodeFileService;
    private final ShareUrlResolver shareUrlResolver;
    private final List<DeployStrategy> deployStrategies;

    public List<DeployModeVO> listDeployModes() {
        return Arrays.stream(DeployMode.values())
                .map(mode -> new DeployModeVO(mode.getCode(), mode.getLabel(), mode.getDescription()))
                .toList();
    }

    @Transactional
    public DeployResultVO deploy(Long appId, String modeCode) throws IOException, InterruptedException {
        appDeployAccessor.requireAppExists(appId);
        DeployMode mode = DeployMode.fromCode(modeCode);
        DeployStrategy strategy = resolveStrategy(mode);

        List<AppCodeFile> files = appCodeFileService.resolveCodeFiles(appId);
        String entryPath = resolveEntryPath(files);
        DeployOutcome outcome = strategy.deploy(appId, files, entryPath);

        String stored = DeployUrlCodec.encode(mode, outcome.getStoredReference());
        appDeployAccessor.updateDeployUrl(appId, stored);

        return new DeployResultVO(
                appId,
                outcome.getShareUrl(),
                outcome.getMessage(),
                mode.getCode(),
                mode.getLabel()
        );
    }

    public DeployResultVO getDeployInfo(Long appId) {
        var info = appDeployAccessor.findAppDeployInfo(appId);
        if (info == null) {
            throw new BusinessException("应用不存在");
        }
        String stored = (String) info.get("deployUrl");
        if (!StringUtils.hasText(stored)) {
            return new DeployResultVO(appId, "", "尚未部署", "", "");
        }
        DeployUrlCodec.DeployReference reference = DeployUrlCodec.parse(stored);
        return new DeployResultVO(
                appId,
                shareUrlResolver.buildShareUrl(reference),
                "已部署",
                reference.mode().getCode(),
                reference.mode().getLabel()
        );
    }

    public String getDeployUrl(Long appId) {
        DeployResultVO info = getDeployInfo(appId);
        return info.getDeployUrl();
    }

    private DeployStrategy resolveStrategy(DeployMode mode) {
        Map<DeployMode, DeployStrategy> strategyMap = deployStrategies.stream()
                .collect(Collectors.toMap(DeployStrategy::mode, Function.identity()));
        DeployStrategy strategy = strategyMap.get(mode);
        if (strategy == null) {
            throw new BusinessException("不支持的部署方式: " + mode.getCode());
        }
        return strategy;
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

package com.ai.agentplatform.module.app.deploy.repository;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.app.deploy.enums.DeployMode;
import com.ai.agentplatform.module.app.deploy.mapper.AppDeployMapper;
import com.ai.agentplatform.module.app.deploy.mapper.AppDeployRecord;
import com.ai.agentplatform.module.app.deploy.support.DeployUrlCodec;
import com.ai.agentplatform.module.app.deploy.support.ShareUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AppDeployAccessor {

    private final AppDeployMapper appDeployMapper;
    private final ShareUrlResolver shareUrlResolver;

    public Map<String, Object> findAppDeployInfo(Long appId) {
        AppDeployRecord record = appDeployMapper.selectDeployInfo(appId);
        if (record == null) {
            return null;
        }
        return Map.of(
                "id", record.getId(),
                "appName", record.getAppName(),
                "appCode", Optional.ofNullable(record.getAppCode()).orElse(""),
                "deployUrl", Optional.ofNullable(record.getDeployUrl()).orElse(""),
                "coverImg", Optional.ofNullable(record.getCoverImg()).orElse("")
        );
    }

    public void requireAppExists(Long appId) {
        if (findAppDeployInfo(appId) == null) {
            throw new BusinessException("应用不存在");
        }
    }

    public String getAppCode(Long appId) {
        Map<String, Object> info = findAppDeployInfo(appId);
        if (info == null) {
            throw new BusinessException("应用不存在");
        }
        return (String) info.get("appCode");
    }

    public String getAppName(Long appId) {
        Map<String, Object> info = findAppDeployInfo(appId);
        if (info == null) {
            throw new BusinessException("应用不存在");
        }
        return (String) info.get("appName");
    }

    public void saveDeployUrl(Long appId, DeployMode mode, String shareUrl) {
        AppDeployRecord record = appDeployMapper.selectDeployInfo(appId);
        String existing = record != null ? Optional.ofNullable(record.getDeployUrl()).orElse("") : "";
        String merged = DeployUrlCodec.merge(
                existing,
                mode,
                shareUrl,
                DeployUrlCodec::parseEntry,
                shareUrlResolver::resolveFullUrlFromReference
        );
        if (merged.equals(existing.trim())) {
            return;
        }
        appDeployMapper.updateDeployUrl(appId, merged);
    }

    public void updateCoverImg(Long appId, String coverImg) {
        appDeployMapper.updateCoverImg(appId, coverImg);
    }
}

package com.ai.agentplatform.module.app.deploy.repository;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.app.deploy.support.AppDeployConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AppDeployAccessor {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> findAppDeployInfo(Long appId) {
        return jdbcTemplate.query(
                "SELECT id, app_name, app_code, deploy_url, cover_img FROM " + AppDeployConstants.APP_TABLE + " WHERE id = ?",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return Map.of(
                            "id", rs.getLong("id"),
                            "appName", rs.getString("app_name"),
                            "appCode", Optional.ofNullable(rs.getString("app_code")).orElse(""),
                            "deployUrl", Optional.ofNullable(rs.getString("deploy_url")).orElse(""),
                            "coverImg", Optional.ofNullable(rs.getString("cover_img")).orElse("")
                    );
                },
                appId
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

    public void updateDeployUrl(Long appId, String deployUrl) {
        jdbcTemplate.update("UPDATE " + AppDeployConstants.APP_TABLE + " SET deploy_url = ? WHERE id = ?", deployUrl, appId);
    }

    public void updateCoverImg(Long appId, String coverImg) {
        jdbcTemplate.update("UPDATE " + AppDeployConstants.APP_TABLE + " SET cover_img = ? WHERE id = ?", coverImg, appId);
    }
}

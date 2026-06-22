package com.ai.agentplatform.module.app.deploy.support;

import com.ai.agentplatform.module.app.deploy.enums.DeployMode;
import org.springframework.util.StringUtils;

/**
 * 数据库存储格式：{mode}:{value}
 * local → /sites/1/index.html
 * nginx → /apps/1/index.html
 * docker → 9001（宿主机端口）
 */
public final class DeployUrlCodec {

    private DeployUrlCodec() {
    }

    public record DeployReference(DeployMode mode, String value) {
    }

    public static String encode(DeployMode mode, String value) {
        return mode.getCode() + ":" + value;
    }

    public static DeployReference parse(String stored) {
        if (!StringUtils.hasText(stored)) {
            return new DeployReference(DeployMode.LOCAL, "");
        }
        String trimmed = stored.trim();
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            String modeCode = trimmed.substring(0, colon);
            String value = trimmed.substring(colon + 1);
            try {
                return new DeployReference(DeployMode.fromCode(modeCode), value);
            } catch (IllegalArgumentException ignored) {
                // 兼容旧数据：纯路径
            }
        }
        return new DeployReference(DeployMode.LOCAL, normalizeLegacyPath(trimmed));
    }

    private static String normalizeLegacyPath(String stored) {
        if (stored.startsWith("http://") || stored.startsWith("https://")) {
            try {
                return java.net.URI.create(stored).getPath();
            } catch (IllegalArgumentException e) {
                return stored;
            }
        }
        return stored;
    }
}

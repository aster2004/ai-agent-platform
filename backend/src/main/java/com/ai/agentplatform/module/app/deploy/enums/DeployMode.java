package com.ai.agentplatform.module.app.deploy.enums;

import lombok.Getter;

@Getter
public enum DeployMode {

    LOCAL("local", "本地静态目录", "写入 deploy/sites，由 Spring Boot 提供 /sites 访问"),
    NGINX("nginx", "Nginx 部署", "生成 Nginx 配置，通过 Nginx 反向代理静态站点"),
    DOCKER("docker", "Docker / 云端", "构建 Nginx 镜像并运行容器，适合容器化与云端部署");

    private final String code;
    private final String label;
    private final String description;

    DeployMode(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    public static DeployMode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return LOCAL;
        }
        for (DeployMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code.trim())) {
                return mode;
            }
        }
        throw new IllegalArgumentException("不支持的部署方式: " + code);
    }
}

package com.ai.agentplatform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class AppUploadProperties {

    /** 本地存储根目录（相对项目运行目录） */
    private String path = "uploads";

    /** 对外访问 URL 前缀 */
    private String urlPrefix = "/uploads";
}

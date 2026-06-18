package com.ai.agentplatform.module.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppUpdateRequest {

    @NotBlank(message = "应用名称不能为空")
    private String appName;

    private String description;

    /** 封面图 URL，传空字符串表示清除 */
    private String coverImg;
}

package com.ai.agentplatform.module.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppCreateRequest {

    @NotBlank(message = "应用名称不能为空")
    private String appName;

    private String description;
}

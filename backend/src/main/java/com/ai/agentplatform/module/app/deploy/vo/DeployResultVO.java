package com.ai.agentplatform.module.app.deploy.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeployResultVO {

    private Long appId;
    private String deployUrl;
    private String message;
    private String deployMode;
    private String deployModeLabel;
}

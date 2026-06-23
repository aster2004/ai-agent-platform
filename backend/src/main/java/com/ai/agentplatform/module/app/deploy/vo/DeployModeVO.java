package com.ai.agentplatform.module.app.deploy.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeployModeVO {

    private String code;
    private String label;
    private String description;
}

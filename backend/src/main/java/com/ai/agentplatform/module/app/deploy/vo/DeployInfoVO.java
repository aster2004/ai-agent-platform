package com.ai.agentplatform.module.app.deploy.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeployInfoVO {

    private String deployUrl;
    private String deployMode;
    private String deployModeLabel;
}

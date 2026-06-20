package com.ai.agentplatform.module.app.deploy.dto;

import lombok.Data;

@Data
public class DeployRequest {

    /** local | nginx | docker，默认 local */
    private String mode = "local";
}

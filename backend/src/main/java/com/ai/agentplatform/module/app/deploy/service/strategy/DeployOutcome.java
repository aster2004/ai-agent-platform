package com.ai.agentplatform.module.app.deploy.service.strategy;

import lombok.Data;

@Data
public class DeployOutcome {

    /** 写入数据库的引用值（路径或端口） */
    private String storedReference;

    private String shareUrl;

    private String message;

    public DeployOutcome(String storedReference, String shareUrl, String message) {
        this.storedReference = storedReference;
        this.shareUrl = shareUrl;
        this.message = message;
    }
}

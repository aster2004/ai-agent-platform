package com.ai.agentplatform.module.app.deploy.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PreviewVO {

    private Long appId;
    private String appName;
    private String previewUrl;
    /** 已有封面图 URL，可为空 */
    private String coverImg;
}

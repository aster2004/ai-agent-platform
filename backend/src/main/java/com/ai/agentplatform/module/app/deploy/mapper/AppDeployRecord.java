package com.ai.agentplatform.module.app.deploy.mapper;

import lombok.Data;

/** 成员3 部署模块查询 app 表所需字段（不依赖成员2 的 JPA 实体） */
@Data
public class AppDeployRecord {

    private Long id;

    private String appName;

    private String appCode;

    private String deployUrl;

    private String coverImg;
}

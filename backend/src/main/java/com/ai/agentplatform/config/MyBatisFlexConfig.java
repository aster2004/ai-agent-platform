package com.ai.agentplatform.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 仅扫描成员3部署模块的 Mapper，不影响成员1/2 的 JPA */
@Configuration
@MapperScan("com.ai.agentplatform.module.app.deploy.mapper")
public class MyBatisFlexConfig {
}

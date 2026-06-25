package com.ai.agentplatform.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** 扫描 MyBatis Mapper（部署、代码生成等模块），不影响 JPA 模块 */
@Configuration
@MapperScan({
        "com.ai.agentplatform.module.app.deploy.mapper",
        "com.ai.agentplatform.module.codegen.mapper"
})
public class MyBatisFlexConfig {
}

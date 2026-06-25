package com.ai.agentplatform.module.app.deploy.support;

import com.ai.agentplatform.module.app.deploy.mapper.AppDeployMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 成员 3 部署模块专用：确保 app 表存在部署相关字段，并插入联调 Mock 数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploySchemaMigration {

    private final AppDeployMapper appDeployMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDeployColumns() {
        addColumnIfMissing("app", "app_code", "LONGTEXT");
        addColumnIfMissing("app", "deploy_url", "VARCHAR(255)");
        addColumnIfMissing("app", "cover_img", "VARCHAR(255)");
        addColumnIfMissing("app", "is_featured", "TINYINT NOT NULL DEFAULT 0");
        seedMockAppIfMissing();
    }

    /** 并行开发期默认应用，与 sql/init.sql 保持一致 */
    private void seedMockAppIfMissing() {
        try {
            Integer count = appDeployMapper.countMockApp();
            if (count != null && count > 0) {
                return;
            }
            appDeployMapper.insertMockApp();
            log.info("已插入 Mock 测试应用 (id=1)，供部署预览联调");
        } catch (Exception e) {
            log.error("插入 Mock 测试应用失败，请在 MySQL 中执行 sql/init.sql 末尾的 INSERT 语句", e);
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer count = appDeployMapper.countColumn(table, column);
        if (count != null && count == 0) {
            appDeployMapper.addColumn(table, column, definition);
            log.info("已为 {} 添加字段 {}", table, column);
        }
    }
}

package com.ai.agentplatform.module.app.deploy.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 成员 3 部署模块专用：确保 app 表存在部署相关字段，并插入联调 Mock 数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeploySchemaMigration {

    private final JdbcTemplate jdbcTemplate;

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
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM `app` WHERE id = 1",
                    Integer.class
            );
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.update("""
                    INSERT INTO `app` (id, user_id, app_name, description, app_code, is_featured, status)
                    VALUES (1, 1, 'Mock 测试应用', '并行开发期默认应用，供预览/对话/生成联调',
                            '<html><body><h1>Hello Mock App</h1></body></html>', 0, 'normal')
                    """);
            log.info("已插入 Mock 测试应用 (id=1)，供部署预览联调");
        } catch (Exception e) {
            log.error("插入 Mock 测试应用失败，请在 MySQL 中执行 sql/init.sql 末尾的 INSERT 语句", e);
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """,
                Integer.class,
                table,
                column
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
            log.info("已为 {} 添加字段 {}", table, column);
        }
    }
}

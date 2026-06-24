-- =============================================================================
-- AI Agent Platform · 团队联调建表脚本（5 表）
-- 使用：mysql -u root -p < sql/schema-team.sql
-- 注意：若库中已有旧表（sys_user / app_info / user 重复），请先备份再执行
-- 建表顺序：user → app → chat_session → chat_message → code_generate（外键依赖）
-- =============================================================================

CREATE DATABASE IF NOT EXISTS ai_agent_platform
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE ai_agent_platform;

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. user 用户表（成员 1）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录用户名',
    `password`    VARCHAR(255) NOT NULL COMMENT '加密后的密码(BCrypt)',
    `nickname`    VARCHAR(50)  NULL COMMENT '用户昵称',
    `avatar`      VARCHAR(255) NULL COMMENT '头像地址',
    `phone`       VARCHAR(20)  NULL COMMENT '手机号码',
    `email`       VARCHAR(100) NULL COMMENT '电子邮箱',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'user' COMMENT '角色：user普通用户/admin管理员',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'normal' COMMENT '状态：normal正常/disabled禁用',
    `points`      INT          NOT NULL DEFAULT 0 COMMENT '积分/成长值',
    `level`       VARCHAR(20)  NOT NULL DEFAULT 'v0' COMMENT '用户等级（如v0/v1/v2）',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- -----------------------------------------------------------------------------
-- 2. app 应用表（成员 2、3）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `app` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`     BIGINT       NOT NULL COMMENT '创建者ID，逻辑关联 user.id',
    `app_name`    VARCHAR(100) NOT NULL COMMENT '应用名称',
    `description` TEXT         NULL COMMENT '应用描述',
    `cover_img`   VARCHAR(255) NULL COMMENT '封面图URL',
    `app_code`    LONGTEXT     NULL COMMENT '当前生效的生成代码',
    `is_featured` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否精选：0-否，1-是',
    `status`      VARCHAR(20)  NOT NULL DEFAULT 'normal' COMMENT '状态：normal-正常，offline-下架',
    `deploy_url`  VARCHAR(255) NULL COMMENT '部署后可访问地址',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_is_featured` (`is_featured`),
    KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='应用表';

-- -----------------------------------------------------------------------------
-- 3. chat_session 对话会话表（成员 6）
-- Redis Key：chat:memory:{sessionId}
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话 ID',
    `user_id`              BIGINT       NOT NULL COMMENT '所属用户，逻辑关联 user.id',
    `app_id`               BIGINT       NULL COMMENT '关联应用，逻辑关联 app.id；NULL 表示独立会话',
    `session_title`        VARCHAR(100) NULL COMMENT '会话标题，默认取首条 user 消息前 20 字',
    `last_message_preview` VARCHAR(200) NULL COMMENT '最后一条消息摘要，列表展示免 JOIN',
    `message_count`        INT          NOT NULL DEFAULT 0 COMMENT '消息条数',
    `last_message_time`    DATETIME     NULL COMMENT '最后一条消息时间，会话列表排序',
    `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_user_app_time` (`user_id`, `app_id`, `last_message_time` DESC),
    KEY `idx_session_user_time` (`user_id`, `last_message_time` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

-- -----------------------------------------------------------------------------
-- 4. chat_message 对话消息明细表（成员 6、7）
-- 游标分页：ORDER BY id DESC，cursor 为上一页最后一条消息的 id
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id`           BIGINT                              NOT NULL AUTO_INCREMENT COMMENT '消息唯一ID',
    `session_id`   BIGINT                              NOT NULL COMMENT '所属会话ID',
    `app_id`       BIGINT                              NULL COMMENT '归属应用ID，空代表临时对话',
    `message_type` ENUM('user', 'ai')                  NOT NULL COMMENT 'user 用户消息 / ai AI回复',
    `content`      LONGTEXT                            NOT NULL COMMENT '对话、代码内容',
    `create_time`  DATETIME                            NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `update_time`  DATETIME                            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    `is_deleted`   TINYINT                             NOT NULL DEFAULT 0 COMMENT '0正常 1逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_message_session_id` (`session_id`, `id` DESC),
    KEY `idx_app_session` (`app_id`, `session_id`),
    CONSTRAINT `fk_msg_session` FOREIGN KEY (`session_id`) REFERENCES `chat_session` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_msg_app` FOREIGN KEY (`app_id`) REFERENCES `app` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息明细表';

-- -----------------------------------------------------------------------------
-- 5. code_generate 代码生成记录表（成员 4、5）
-- generate_status：0生成中 / 1成功 / 2失败
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `code_generate` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '生成记录ID',
    `user_id`         BIGINT       NOT NULL COMMENT '所属用户，逻辑关联 user.id',
    `app_id`          BIGINT       NOT NULL COMMENT '所属应用，逻辑关联 app.id',
    `session_id`      BIGINT       NULL COMMENT '关联 chat_session.id；空=独立生成，有值=多轮对话上下文',
    `prompt`          TEXT         NOT NULL COMMENT '用户输入的需求描述',
    `generate_type`   VARCHAR(50)  NOT NULL COMMENT 'HTML / VUE / MULTI_FILE / WORKFLOW',
    `generate_status` TINYINT      NOT NULL DEFAULT 0 COMMENT '0生成中 1成功 2失败',
    `error_msg`       VARCHAR(500) NULL COMMENT '失败原因',
    `model_name`      VARCHAR(64)  NULL COMMENT '大模型名称',
    `cost_tokens`     INT          NULL COMMENT '消耗 Token 数',
    `code_content`    LONGTEXT     NULL COMMENT '完整生成代码',
    `duration`        INT          NULL COMMENT '生成耗时(ms)',
    `workflow_step`   VARCHAR(50)  NULL COMMENT '工作流节点；非工作流可为空',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_app_id` (`app_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_generate_status` (`generate_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成记录表';

-- -----------------------------------------------------------------------------
-- 6. user_points_log 积分流水表（成员 1）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_points_log` (
                                                 `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '积分记录ID',
                                                 `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
                                                 `points`      INT          NOT NULL COMMENT '变动积分',
                                                 `type`        VARCHAR(50)  NOT NULL COMMENT '积分类型：REGISTER/SET_NICKNAME/BIND_PHONE/BIND_EMAIL/UPLOAD_AVATAR/CHECKIN_DAILY/CHECKIN_7DAYS/CHECKIN_30DAYS',
    `description` VARCHAR(200) NULL COMMENT '描述',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表';

-- -----------------------------------------------------------------------------
-- 7. user_checkin 签到记录表（成员 1）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_checkin` (
                                              `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '签到记录ID',
                                              `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
                                              `checkin_date` DATE        NOT NULL COMMENT '签到日期',
                                              `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                              PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `checkin_date`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到记录表';

-- -----------------------------------------------------------------------------
-- Mock 测试数据（userId=1, appId=1）
-- 密码：123456（BCrypt）
-- -----------------------------------------------------------------------------
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `role`, `status`)
VALUES (
    1,
    'dev_user',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
    '开发测试用户',
    'admin',
    'normal'
) ON DUPLICATE KEY UPDATE `username` = `username`;

INSERT INTO `app` (`id`, `user_id`, `app_name`, `description`, `app_code`, `status`)
VALUES (
    1,
    1,
    'Mock 测试应用',
    '并行开发期默认应用，供预览/对话/生成联调',
    '<html><body><h1>Hello Mock App</h1></body></html>',
    'normal'
) ON DUPLICATE KEY UPDATE `app_name` = `app_name`;


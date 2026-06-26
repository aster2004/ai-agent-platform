-- 阶段 2/3：chat_session 长期记忆字段（已有库按需执行，列已存在则跳过对应语句）
USE ai_agent_platform;

ALTER TABLE `chat_session`
    ADD COLUMN `memory_summary` TEXT NULL COMMENT '跨轮会话概要（Layer 2 长期记忆）'
        AFTER `last_message_preview`;

ALTER TABLE `chat_session`
    ADD COLUMN `summary_updated_at` DATETIME NULL COMMENT 'memory_summary 最后更新时间'
        AFTER `memory_summary`;

ALTER TABLE `chat_session`
    ADD COLUMN `summary_message_count` INT NOT NULL DEFAULT 0 COMMENT '上次更新 summary 时的 message_count'
        AFTER `summary_updated_at`;

package com.ai.agentplatform.module.codegen.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 代码生成记录表实体
 * 完全匹配数据库设计截图 code_generate 全部字段
 */
@Data
@NoArgsConstructor
public class CodeGenerate {
    /**
     * 主键ID，自增 bigint
     */
    private Long id;

    /**
     * 所属用户ID，外键关联user.id bigint
     */
    private Long userId;

    /**
     * 所属应用ID，外键关联app.id bigint
     */
    private Long appId;

    /**
     * 会话ID，逻辑外键关联chat_session.id，可空 bigint
     * 为空代表独立单次生成；有值读取多轮对话上下文
     */
    private Long sessionId;

    /**
     * 用户输入需求描述 text 非空
     */
    private String prompt;

    /**
     * 生成类型 varchar(50) 非空
     * 枚举值：HTML / VUE / MULTI_FILE / WORKFLOW
     */
    private String generateType;

    /**
     * 生成状态 tinyint 非空，默认0
     * 0=生成中，1=生成成功，2=生成失败
     */
    private Integer generateStatus;

    /**
     * 错误信息 varchar(500) 可空，生成失败时存储报错原因
     */
    private String errorMsg;

    /**
     * 使用大模型名称 varchar(64) 可空
     * openai / deepseek / bailian
     */
    private String modelName;

    /**
     * 本次AI调用消耗Token数量 int 可空
     */
    private Integer costTokens;

    /**
     * 大模型返回完整代码 longtext 可空
     */
    private String codeContent;

    /**
     * 生成时间 datetime 非空
     */
    private LocalDateTime createTime;

    /**
     * 更新时间 datetime 非空
     */
    private LocalDateTime updateTime;

    /**
     * 生成总耗时 int 可空，单位毫秒
     */
    private Integer duration;

    /**
     * 工作流节点 VARCHAR(50) 非空
     * 对接成员5 LangGraph工作流模块
     */
    private String workflowStep;

    /**
     * 模型创造性系数（预留扩展）
     */
    //private BigDecimal temperature;

    /**
     * 最大输出Token限制（预留扩展）
     */
    //private Integer maxTokens;
}
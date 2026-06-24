package com.ai.agentplatform.module.codegen.workflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "code_generate")
public class CodeGenerate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "generate_type", nullable = false, length = 50)
    private String generateType;

    @Column(name = "generate_status", nullable = false)
    private Integer generateStatus = 0;

    @Column(name = "error_msg", length = 500)
    private String errorMsg;

    @Column(name = "model_name", length = 64)
    private String modelName;

    @Column(name = "code_content", columnDefinition = "LONGTEXT")
    private String codeContent;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "workflow_step", length = 50)
    private String workflowStep;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}

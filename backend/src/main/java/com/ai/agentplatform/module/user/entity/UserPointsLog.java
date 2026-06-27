package com.ai.agentplatform.module.user.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_points_log")
public class UserPointsLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer points;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(length = 200)
    private String description;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @Column
    private LocalDate recordDate;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "related_type", length = 50)
    private String relatedType;
}
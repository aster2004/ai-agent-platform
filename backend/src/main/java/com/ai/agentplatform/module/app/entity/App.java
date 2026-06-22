package com.ai.agentplatform.module.app.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "app")
@SQLDelete(sql = "UPDATE app SET status = 'offline' WHERE id = ?")
@SQLRestriction("status <> 'offline'")
public class App {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String appName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String coverImg;

    @Column(columnDefinition = "LONGTEXT")
    private String appCode;

    @Column(nullable = false)
    private Integer isFeatured = 0;

    @Column(nullable = false, length = 20)
    private String status = "normal";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}

package com.ai.agentplatform.module.user.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(length = 50)
    private String nickname;

    @Column(length = 255)
    private String avatar;

    @Column(nullable = false, length = 20)
    private String role = "user";

    @Column(nullable = false, length = 20)
    private String status = "normal";

    @Column(nullable = false)
    private Integer points = 0;

    @Column(nullable = false, length = 20)
    private String level = "v0";

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    private LocalDateTime updateTime;
}

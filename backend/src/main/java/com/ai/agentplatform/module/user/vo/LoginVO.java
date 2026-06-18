package com.ai.agentplatform.module.user.vo;

import lombok.Data;

@Data
public class LoginVO {

    private Long userId;
    private String username;
    private String nickname;
    private String role;
    private Integer points;
    private String level;
    private String token;
}

package com.ai.agentplatform.module.user.dto;

import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class UserProfileRequest {

    @Size(max = 50, message = "昵称长度不能超过50位")
    private String nickname;

    @Size(max = 20, message = "手机号长度不能超过20位")
    private String phone;

    @Size(max = 100, message = "邮箱长度不能超过100位")
    private String email;
}
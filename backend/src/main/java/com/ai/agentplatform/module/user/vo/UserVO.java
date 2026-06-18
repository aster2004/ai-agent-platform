package com.ai.agentplatform.module.user.vo;

import com.ai.agentplatform.module.user.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    private String role;
    private String status;
    private Integer points;
    private String level;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setPoints(user.getPoints());
        vo.setLevel(user.getLevel());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}

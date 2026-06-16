package com.ai.agentplatform.module.app.vo;

import com.ai.agentplatform.module.app.entity.App;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppVO {

    private Long id;
    private String appName;
    private String description;
    private Long userId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AppVO from(App app) {
        AppVO vo = new AppVO();
        vo.setId(app.getId());
        vo.setAppName(app.getAppName());
        vo.setDescription(app.getDescription());
        vo.setUserId(app.getUserId());
        vo.setStatus(app.getStatus());
        vo.setCreateTime(app.getCreateTime());
        vo.setUpdateTime(app.getUpdateTime());
        return vo;
    }
}

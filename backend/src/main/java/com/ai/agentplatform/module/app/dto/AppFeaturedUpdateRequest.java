package com.ai.agentplatform.module.app.dto;

import lombok.Data;

@Data
public class AppFeaturedUpdateRequest {

    /** 是否设为精选，与 isFeatured 二选一 */
    private Boolean featured;

    /** 是否精选：0 否 / 1 是，与 featured 二选一 */
    private Integer isFeatured;

    public boolean resolveFeatured() {
        if (featured != null) {
            return featured;
        }
        if (isFeatured != null) {
            return isFeatured == 1;
        }
        throw new IllegalArgumentException("请指定 featured 或 isFeatured");
    }
}

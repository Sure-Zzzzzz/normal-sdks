package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import lombok.Data;

/**
 * 同步权限范围响应
 *
 * @author surezzzzzz
 */
@Data
public class SyncScopesResponse {
    /**
     * 所属用户ID
     */
    private String ownerUserId;

    /**
     * 更新数量
     */
    private Integer updatedCount;

    /**
     * 消息
     */
    private String message;
}

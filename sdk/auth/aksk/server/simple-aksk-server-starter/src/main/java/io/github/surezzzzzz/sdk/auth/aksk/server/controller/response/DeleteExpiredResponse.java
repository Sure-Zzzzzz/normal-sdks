package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import lombok.Data;

/**
 * 删除过期Token响应
 *
 * @author surezzzzzz
 */
@Data
public class DeleteExpiredResponse {
    /**
     * 删除数量
     */
    private Integer deletedCount;

    /**
     * 消息
     */
    private String message;
}

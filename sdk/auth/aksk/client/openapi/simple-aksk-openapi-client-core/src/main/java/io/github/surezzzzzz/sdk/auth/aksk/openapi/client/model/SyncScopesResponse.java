package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

/**
 * 批量同步用户 scopes 响应
 *
 * @author surezzzzzz
 */
public class SyncScopesResponse {

    /**
     * 归属用户标识
     */
    private String ownerUserId;

    /**
     * 更新计数
     */
    private Integer updatedCount;

    /**
     * 说明
     */
    private String message;


    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Integer getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(Integer updatedCount) {
        this.updatedCount = updatedCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

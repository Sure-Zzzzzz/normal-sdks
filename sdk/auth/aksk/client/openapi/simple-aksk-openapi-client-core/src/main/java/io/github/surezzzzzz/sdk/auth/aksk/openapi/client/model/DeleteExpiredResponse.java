package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

/**
 * 过期 Token 清理响应
 *
 * @author surezzzzzz
 */
public class DeleteExpiredResponse {

    /**
     * 清理计数
     */
    private Integer deletedCount;

    /**
     * 说明
     */
    private String message;


    public Integer getDeletedCount() {
        return deletedCount;
    }

    public void setDeletedCount(Integer deletedCount) {
        this.deletedCount = deletedCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

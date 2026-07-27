package io.github.surezzzzzz.sdk.kms.server.service;

/**
 * 管理写操作可安全持久化和重放的 HTTP 结果。
 *
 * @author surezzzzzz
 */
public final class KmsManagementIdempotencyResult {

    private final int status;
    private final String responseBody;
    private final String resourceRef;
    private final String location;
    private final boolean replayed;

    /**
     * 创建管理写操作结果。
     */
    public KmsManagementIdempotencyResult(int status, String responseBody, String resourceRef, String location,
                                          boolean replayed) {
        this.status = status;
        this.responseBody = responseBody;
        this.resourceRef = resourceRef;
        this.location = location;
        this.replayed = replayed;
    }

    /**
     * 获取 HTTP 状态。
     */
    public int getStatus() {
        return status;
    }

    /**
     * 获取无敏感响应正文；204 响应为 null。
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * 获取成功资源稳定标识。
     */
    public String getResourceRef() {
        return resourceRef;
    }

    /**
     * 获取创建资源的位置；没有时为 null。
     */
    public String getLocation() {
        return location;
    }

    /**
     * 判断是否来自已持久化的成功结果。
     */
    public boolean isReplayed() {
        return replayed;
    }
}

package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 站内信批次 wire 模型（1.3.7）
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamMessageBatchResponse {
    /**
     * 批次标识。
     */
    public String sendBatchId;
    /**
     * 标题。
     */
    public String title;
    /**
     * 内容。
     */
    public String content;
    /**
     * 发送者标识（外部主体 aksk:{clientId}）。
     */
    public String senderUsername;
    /**
     * 收件用户数。
     */
    public int targetUserCount;
    /**
     * 收件部门数。
     */
    public int targetDepartmentCount;
}

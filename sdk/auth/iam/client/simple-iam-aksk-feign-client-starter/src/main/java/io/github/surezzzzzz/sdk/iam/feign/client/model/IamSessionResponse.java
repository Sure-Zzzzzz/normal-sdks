package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 在线会话 wire 模型（1.3.7）
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamSessionResponse {
    /**
     * 会话标识。
     */
    public String sessionId;
    /**
     * 属主公开主体。
     */
    public String subjectId;
    /**
     * 用户名。
     */
    public String username;
    /**
     * 客户端 ID。
     */
    public String clientId;
    /**
     * 来源 IP。
     */
    public String remoteIp;
    /**
     * User-Agent。
     */
    public String userAgent;
    /**
     * 认证时间。
     */
    public String authTime;
    /**
     * 最近活跃。
     */
    public String lastActiveAt;
}

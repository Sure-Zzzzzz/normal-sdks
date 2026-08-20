package io.github.surezzzzzz.sdk.auth.resource.core.model;

import lombok.Getter;

/**
 * 已路由的Bearer认证凭据。
 *
 * @author surezzzzzz
 */
@Getter
public class BearerResourceCredential implements ResourceCredential {

    private final ResourceAuthenticationSourceId sourceId;
    private final String token;

    /**
     * 创建已路由Bearer凭据。
     *
     * @param sourceId 唯一认证来源
     * @param token    原始Bearer凭据
     */
    public BearerResourceCredential(ResourceAuthenticationSourceId sourceId, String token) {
        this.sourceId = sourceId;
        this.token = token;
    }

    /**
     * 禁止凭据文本进入日志。
     *
     * @return 固定脱敏文本
     */
    @Override
    public String toString() {
        return "BearerResourceCredential[REDACTED]";
    }
}

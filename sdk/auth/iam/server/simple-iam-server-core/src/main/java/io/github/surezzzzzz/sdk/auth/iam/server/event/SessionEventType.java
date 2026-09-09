package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

/**
 * IAM 会话生命周期事件类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum SessionEventType {

    /**
     * 会话建立（登录成功后）
     */
    CREATED("created", "会话建立"),
    /**
     * 会话撤销（登出 / 重登踢旧 / 用户生命周期批量吊销）
     */
    REVOKED("revoked", "会话撤销");

    private final String code;
    private final String description;

    SessionEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 事件类型 code
     * @return 枚举，不存在返回 null
     */
    public static SessionEventType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SessionEventType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }
}

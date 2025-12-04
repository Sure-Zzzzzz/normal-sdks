package io.github.surezzzzzz.sdk.limiter.redis.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/3/20 12:12
 */
@Getter
@AllArgsConstructor
public enum TokenResult {
    SUCCESS(1, "成功扣除令牌并记录"),
    EXISTS(2, "记录已存在"),
    INSUFFICIENT(0, "令牌不足"),
    UNKNOWN(-1, "未知错误");

    private final int code;
    private final String message;

    public static TokenResult fromCode(Long code) {
        if (code == null) return UNKNOWN;
        for (TokenResult result : values()) {
            if (result.code == code.intValue()) {
                return result;
            }
        }
        return UNKNOWN;
    }
}
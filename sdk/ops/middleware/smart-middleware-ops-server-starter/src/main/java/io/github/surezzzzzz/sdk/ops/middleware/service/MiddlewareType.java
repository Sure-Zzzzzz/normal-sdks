package io.github.surezzzzzz.sdk.ops.middleware.service;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 已支持的中间件类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum MiddlewareType {

    ELASTICSEARCH("elasticsearch"),
    REDIS("redis"),
    KAFKA("kafka"),
    MYSQL("mysql");

    @JsonValue
    private final String code;

    MiddlewareType(String code) {
        this.code = code;
    }
}

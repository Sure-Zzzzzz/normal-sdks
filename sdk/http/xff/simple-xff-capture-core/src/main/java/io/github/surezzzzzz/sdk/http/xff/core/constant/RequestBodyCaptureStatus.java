package io.github.surezzzzzz.sdk.http.xff.core.constant;

import lombok.Getter;

/**
 * 请求体采集状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum RequestBodyCaptureStatus {

    /**
     * 请求体维度未启用。
     */
    DISABLED("DISABLED"),
    /**
     * 方法与 URI 规则未命中。
     */
    RULE_NOT_MATCHED("RULE_NOT_MATCHED"),
    /**
     * 请求没有 Body。
     */
    NO_BODY("NO_BODY"),
    /**
     * Body 已完整采集。
     */
    CAPTURED("CAPTURED"),
    /**
     * Body 超过上限，仅保留前缀。
     */
    TRUNCATED("TRUNCATED"),
    /**
     * Content-Type 不在允许范围内。
     */
    CONTENT_TYPE_SKIPPED("CONTENT_TYPE_SKIPPED"),
    /**
     * Body 读取失败。
     */
    READ_FAILED("READ_FAILED");

    private final String code;

    RequestBodyCaptureStatus(String code) {
        this.code = code;
    }
}

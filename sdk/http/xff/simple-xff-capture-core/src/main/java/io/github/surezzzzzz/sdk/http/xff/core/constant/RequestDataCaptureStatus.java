package io.github.surezzzzzz.sdk.http.xff.core.constant;

import lombok.Getter;

/**
 * 请求参数维度采集状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum RequestDataCaptureStatus {

    /**
     * 该维度未启用。
     */
    DISABLED("DISABLED"),
    /**
     * 方法与 URI 规则未命中。
     */
    RULE_NOT_MATCHED("RULE_NOT_MATCHED"),
    /**
     * 请求中没有该来源的参数。
     */
    ABSENT("ABSENT"),
    /**
     * 已采集参数。
     */
    CAPTURED("CAPTURED"),
    /**
     * 参数源超过安全采集上限。
     */
    TRUNCATED("TRUNCATED"),
    /**
     * 参数读取失败。
     */
    READ_FAILED("READ_FAILED");

    private final String code;

    RequestDataCaptureStatus(String code) {
        this.code = code;
    }
}

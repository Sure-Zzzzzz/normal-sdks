package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * v2 服务目录摘要响应（最小 ID/名称，不暴露未授权服务）
 *
 * @author surezzzzzz
 */
@Getter
@AllArgsConstructor
public class SmartRedisLimiterServiceSummaryResponse {

    /**
     * 服务编码
     */
    private final String serviceCode;

    /**
     * 展示名称（目录未配置时回显服务编码）
     */
    private final String displayName;
}

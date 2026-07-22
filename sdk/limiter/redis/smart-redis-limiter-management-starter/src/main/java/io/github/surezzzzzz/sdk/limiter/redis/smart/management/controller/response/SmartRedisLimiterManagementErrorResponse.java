package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Management API 错误响应
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterManagementErrorResponse {

    private String message;
    private Instant timestamp;
}

package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 限流策略分页响应
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterPolicyPageResponse {

    private List<SmartRedisLimiterPolicyResponse> items;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
}

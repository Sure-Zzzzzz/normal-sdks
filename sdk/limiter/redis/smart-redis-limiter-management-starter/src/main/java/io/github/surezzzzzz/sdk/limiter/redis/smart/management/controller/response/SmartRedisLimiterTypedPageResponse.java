package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * v2 类型化规则分页响应（count 由后端完整过滤得出）
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterTypedPageResponse {

    private List<SmartRedisLimiterTypedRuleResponse> items;
    private Long total;
    private Integer page;
    private Integer size;
}

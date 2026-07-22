package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request;

import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 整体更新限流窗口请求
 *
 * @author surezzzzzz
 */
@Data
public class SmartRedisLimiterPolicyUpdateRequest {

    private Long expectedRowVersion;
    private List<SmartRedisLimiterLimit> limits = new ArrayList<>();
}

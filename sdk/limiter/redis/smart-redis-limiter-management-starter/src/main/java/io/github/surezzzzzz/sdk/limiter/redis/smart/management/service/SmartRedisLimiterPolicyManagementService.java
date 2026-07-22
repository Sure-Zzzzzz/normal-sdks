package io.github.surezzzzzz.sdk.limiter.redis.smart.management.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyCreateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyUpdateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyMutationResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyPageResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyQuery;

/**
 * 限流策略管理服务接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicyManagementService {

    SmartRedisLimiterPolicyMutationResponse create(SmartRedisLimiterPolicyCreateRequest request, String operator);

    SmartRedisLimiterPolicyMutationResponse update(long id, SmartRedisLimiterPolicyUpdateRequest request,
                                                   String operator);

    SmartRedisLimiterPolicyMutationResponse enable(long id, long rowVersion, String operator);

    SmartRedisLimiterPolicyMutationResponse disable(long id, long rowVersion, String operator);

    SmartRedisLimiterPolicyMutationResponse delete(long id, long rowVersion, String operator);

    SmartRedisLimiterPolicyResponse findById(long id);

    SmartRedisLimiterPolicyPageResponse query(SmartRedisLimiterPolicyQuery query);
}

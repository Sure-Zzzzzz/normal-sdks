package io.github.surezzzzzz.sdk.limiter.redis.smart.management.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyCreateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyUpdateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyMutationResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyPageResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
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

    /**
     * 在已验证的完整 DATA 范围中创建策略。
     */
    SmartRedisLimiterPolicyMutationResponse create(SmartRedisLimiterPolicyCreateRequest request, String operator,
                                                   SmartRedisLimiterPolicyDataScope scope);

    /**
     * 在已验证的完整 DATA 范围中替换窗口。
     */
    SmartRedisLimiterPolicyMutationResponse update(long id, SmartRedisLimiterPolicyUpdateRequest request,
                                                   String operator, SmartRedisLimiterPolicyDataScope scope);

    /**
     * 在已验证的完整 DATA 范围中启用。
     */
    SmartRedisLimiterPolicyMutationResponse enable(long id, long rowVersion, String operator,
                                                   SmartRedisLimiterPolicyDataScope scope);

    /**
     * 在已验证的完整 DATA 范围中禁用。
     */
    SmartRedisLimiterPolicyMutationResponse disable(long id, long rowVersion, String operator,
                                                    SmartRedisLimiterPolicyDataScope scope);

    /**
     * 在已验证的完整 DATA 范围中删除。
     */
    SmartRedisLimiterPolicyMutationResponse delete(long id, long rowVersion, String operator,
                                                   SmartRedisLimiterPolicyDataScope scope);

    /**
     * 按主键与 DATA 范围联合查询。
     */
    SmartRedisLimiterPolicyResponse findById(long id, SmartRedisLimiterPolicyDataScope scope);

    /**
     * 在同一 DATA 范围中查询列表与总数。
     */
    SmartRedisLimiterPolicyPageResponse query(SmartRedisLimiterPolicyQuery query, SmartRedisLimiterPolicyDataScope scope);
}

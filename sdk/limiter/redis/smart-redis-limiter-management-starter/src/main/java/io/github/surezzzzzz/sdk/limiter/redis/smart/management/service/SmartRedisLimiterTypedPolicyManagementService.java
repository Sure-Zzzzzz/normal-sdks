package io.github.surezzzzzz.sdk.limiter.redis.smart.management.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterTypedMutationResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterTypedPageResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterTypedRuleResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterTypedRuleQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;

import java.util.List;

/**
 * v2 类型化规则管理服务：CRUD + 服务快照
 *
 * <p>目录（服务协议模式、资源与维度声明）由 DirectoryProvider 提供；
 * 创建与更新校验身份组合约束（core 2.2.0 Helper）并要求资源声明了对应维度；
 * LEGACY_V1 服务不允许出现类型化规则（409 协议错配）。
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterTypedPolicyManagementService {

    /**
     * 创建类型化规则；身份重复或协议错配抛冲突异常
     */
    SmartRedisLimiterTypedMutationResponse create(SmartRedisLimiterTypedPolicyKey key,
                                                  Boolean enabled,
                                                  List<SmartRedisLimiterLimit> limits,
                                                  String operator,
                                                  SmartRedisLimiterPolicyDataScope scope);

    /**
     * 整体替换窗口（CAS 行版本）
     */
    SmartRedisLimiterTypedMutationResponse update(long id,
                                                  long expectedRowVersion,
                                                  List<SmartRedisLimiterLimit> limits,
                                                  String operator,
                                                  SmartRedisLimiterPolicyDataScope scope);

    /**
     * 更新启停状态（CAS 行版本）
     */
    SmartRedisLimiterTypedMutationResponse state(long id,
                                                 long expectedRowVersion,
                                                 boolean enabled,
                                                 String operator,
                                                 SmartRedisLimiterPolicyDataScope scope);

    /**
     * 删除规则（CAS 行版本）
     */
    SmartRedisLimiterTypedMutationResponse delete(long id,
                                                  long expectedRowVersion,
                                                  String operator,
                                                  SmartRedisLimiterPolicyDataScope scope);

    /**
     * 按主键查询（DATA 范围）
     */
    SmartRedisLimiterTypedRuleResponse findById(long id, SmartRedisLimiterPolicyDataScope scope);

    /**
     * 分页查询（DATA 范围 + 条件过滤）
     */
    SmartRedisLimiterTypedPageResponse query(SmartRedisLimiterTypedRuleQuery query,
                                             SmartRedisLimiterPolicyDataScope scope);

    /**
     * 服务快照视图（schemaVersion=2，含 policyEpoch 与单调 revision）；
     * 目录未声明或 LEGACY_V1 服务抛协议错配冲突
     */
    TypedSnapshotView snapshot(String serviceCode);

    /**
     * 快照视图：core 快照 + ETag
     */
    final class TypedSnapshotView {

        private final String etag;
        private final Object snapshot;

        public TypedSnapshotView(String etag, Object snapshot) {
            this.etag = etag;
            this.snapshot = snapshot;
        }

        public String getEtag() {
            return etag;
        }

        public Object getSnapshot() {
            return snapshot;
        }
    }
}

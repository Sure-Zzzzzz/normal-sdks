package io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyRevisionEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyQuery;

import java.time.Instant;
import java.util.List;

/**
 * 限流策略持久化扩展接口
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterPolicyRepository {

    /**
     * 幂等初始化服务 revision
     */
    void initializeRevision(String serviceCode);

    /**
     * 锁定服务 revision
     */
    SmartRedisLimiterPolicyRevisionEntity lockRevision(String serviceCode);

    /**
     * 查询服务 revision
     */
    SmartRedisLimiterPolicyRevisionEntity findRevision(String serviceCode);

    /**
     * 更新服务 revision
     */
    void updateRevision(String serviceCode, long revision, Instant publishedAt);

    /**
     * 按主键查询完整策略
     */
    SmartRedisLimiterPolicyEntity findById(long id);

    /**
     * 按精确身份查询完整策略
     */
    SmartRedisLimiterPolicyEntity findByIdentity(String serviceCode, String resourceCode, String subject);

    /**
     * 新增策略并返回主键
     */
    long insert(SmartRedisLimiterPolicyEntity entity);

    /**
     * 整体替换限额窗口
     */
    boolean replaceLimits(long id, long expectedRowVersion,
                          List<io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit> limits,
                          Instant updatedAt);

    /**
     * 更新启用状态
     */
    boolean updateEnabled(long id, long expectedRowVersion, boolean enabled, Instant updatedAt);

    /**
     * 删除策略
     */
    boolean delete(long id, long expectedRowVersion);

    /**
     * 查询服务全部已启用策略
     */
    List<SmartRedisLimiterPolicyEntity> findEnabledByServiceCode(String serviceCode);

    /**
     * 分页查询策略
     */
    List<SmartRedisLimiterPolicyEntity> query(SmartRedisLimiterPolicyQuery query);

    /**
     * 统计查询结果
     */
    long count(SmartRedisLimiterPolicyQuery query);

    /**
     * 按主键与完整 DATA 范围联合读取。
     */
    SmartRedisLimiterPolicyEntity findById(long id, SmartRedisLimiterPolicyDataScope scope);

    /**
     * 在相同 DATA 范围中替换全部窗口。
     */
    boolean replaceLimits(long id, long expectedRowVersion,
                          List<io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit> limits,
                          Instant updatedAt, SmartRedisLimiterPolicyDataScope scope);

    /**
     * 按主键、版本与完整 DATA 范围更新状态。
     */
    boolean updateEnabled(long id, long expectedRowVersion, boolean enabled, Instant updatedAt,
                          SmartRedisLimiterPolicyDataScope scope);

    /**
     * 按主键、版本与完整 DATA 范围删除。
     */
    boolean delete(long id, long expectedRowVersion, SmartRedisLimiterPolicyDataScope scope);

    /**
     * 在完整 DATA 范围中分页查询。
     */
    List<SmartRedisLimiterPolicyEntity> query(SmartRedisLimiterPolicyQuery query,
                                              SmartRedisLimiterPolicyDataScope scope);

    /**
     * 使用与列表相同的 DATA 谓词统计。
     */
    long count(SmartRedisLimiterPolicyQuery query, SmartRedisLimiterPolicyDataScope scope);
}

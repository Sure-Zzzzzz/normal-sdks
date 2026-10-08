package io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterTypedRuleQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;

import java.time.Instant;
import java.util.List;

/**
 * v2 类型化限流规则 Repository
 *
 * <p>服务级 revision 复用 {@link SmartRedisLimiterPolicyRepository} 的 revision 接口
 * （v1/v2 同服务编码共用 revision 表，一个服务只属于一种协议模式）。
 * 唯一身份冲突由数据库唯一索引兜底，以 DuplicateKeyException 上抛，
 * 由服务层翻译为身份冲突业务异常。
 *
 * @author surezzzzzz
 */
public interface SmartRedisLimiterTypedRuleRepository {

    /**
     * 按主键查询（全量范围）
     */
    SmartRedisLimiterTypedRuleEntity findById(long id);

    /**
     * 按主键查询并应用 DATA 范围（serviceCode IN 约束）
     */
    SmartRedisLimiterTypedRuleEntity findById(long id, SmartRedisLimiterPolicyDataScope scope);

    /**
     * 按七字段身份查询（不适用字段传空串）
     */
    SmartRedisLimiterTypedRuleEntity findByIdentity(String serviceCode,
                                                    String resourceCode,
                                                    String dimension,
                                                    String selector,
                                                    String namespace,
                                                    String customType,
                                                    String objectId);

    /**
     * 新增规则，返回主键；身份重复时上抛 DuplicateKeyException
     */
    long insert(SmartRedisLimiterTypedRuleEntity entity);

    /**
     * 整体替换窗口并推进行版本（CAS），范围外主键视为不存在返回 false
     */
    boolean replaceLimits(long id, long expectedRowVersion,
                          List<SmartRedisLimiterLimit> limits, Instant updatedAt,
                          SmartRedisLimiterPolicyDataScope scope);

    /**
     * 更新启停状态并推进行版本（CAS），范围外主键视为不存在返回 false
     */
    boolean updateEnabled(long id, long expectedRowVersion, boolean enabled, Instant updatedAt,
                          SmartRedisLimiterPolicyDataScope scope);

    /**
     * 删除规则（CAS + DATA 范围），窗口由外键级联删除
     */
    boolean delete(long id, long expectedRowVersion, SmartRedisLimiterPolicyDataScope scope);

    /**
     * 快照查询：服务的全部启用规则（含窗口）
     */
    List<SmartRedisLimiterTypedRuleEntity> findEnabledByServiceCode(String serviceCode);

    /**
     * 分页查询（DATA 范围 + 条件过滤）
     */
    List<SmartRedisLimiterTypedRuleEntity> query(SmartRedisLimiterTypedRuleQuery query,
                                                 SmartRedisLimiterPolicyDataScope scope);

    /**
     * 条件计数（DATA 范围 + 条件过滤）
     */
    long count(SmartRedisLimiterTypedRuleQuery query, SmartRedisLimiterPolicyDataScope scope);
}

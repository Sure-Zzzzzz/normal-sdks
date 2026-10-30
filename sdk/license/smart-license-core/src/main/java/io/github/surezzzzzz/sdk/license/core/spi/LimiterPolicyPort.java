package io.github.surezzzzzz.sdk.license.core.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * License 对客户 OpenAPI 限频策略的履约端口。
 *
 * <p>License 只拥有商业授权事实；具体的 Management HTTP、AKSK 传输、策略快照
 * 与 Redis 执行均由宿主适配。客户策略固定使用 CUSTOMER + EXACT，避免把 limiter
 * 的运行实现引入 License 领域。</p>
 *
 * @author surezzzzzz
 */
public interface LimiterPolicyPort {

    /**
     * 按完整业务键幂等写入客户策略。已存在时由适配器按 CAS 规则更新窗口。
     *
     * @param request 期望的客户策略
     * @return Management 已确认的规则结果
     */
    PolicyResult upsert(PolicyRequest request);

    /**
     * 按已确认的远端规则版本切换启用状态。
     *
     * @param key 客户策略键
     * @param ruleId 远端规则编号
     * @param expectedRowVersion 远端行版本
     * @param enabled 是否启用
     * @param operationId License 本地幂等操作编号
     * @return Management 已确认的规则结果
     */
    PolicyResult setEnabled(PolicyKey key, String ruleId, long expectedRowVersion,
                            boolean enabled, String operationId);

    /**
     * 按完整策略键回读，用于超时或进程重启后的结果确认。
     *
     * @param key 客户策略键
     * @return 已找到的规则；不存在时返回 null
     */
    PolicyResult find(PolicyKey key);

    /**
     * 客户 OpenAPI 限频策略键；namespace 必须来自 Management 服务声明。
     */
    @Value
    class PolicyKey {
        String serviceCode;
        String resourceCode;
        String namespace;
        String customerId;
    }

    /**
     * License 业务侧的限额窗口，不暴露 limiter 的维度和选择器字段。
     */
    @Value
    class Limit {
        long count;
        long window;
        String unit;

        public Limit(long count, long window, String unit) {
            if (count <= 0L || window <= 0L || unit == null || unit.trim().isEmpty()) {
                throw new IllegalArgumentException("限频窗口必须为正数且时间单位不能为空");
            }
            this.count = count;
            this.window = window;
            this.unit = unit;
        }
    }

    /**
     * 客户策略期望状态。operationId 由 License 生成并在本地台账中持久化。
     */
    @Value
    class PolicyRequest {
        PolicyKey key;
        List<Limit> limits;
        boolean enabled;
        String operationId;

        public PolicyRequest(PolicyKey key, List<Limit> limits, boolean enabled, String operationId) {
            if (key == null || limits == null || limits.isEmpty()
                    || operationId == null || operationId.trim().isEmpty()) {
                throw new IllegalArgumentException("限频策略键、窗口和操作编号不能为空");
            }
            this.key = key;
            this.limits = Collections.unmodifiableList(new ArrayList<Limit>(limits));
            this.enabled = enabled;
            this.operationId = operationId;
        }
    }

    /**
     * Management 已确认的策略结果；revision 是服务快照修订，rowVersion 是规则 CAS 版本。
     */
    @Value
    class PolicyResult {
        /** limiter 幂等命中或仅回读时没有新的服务修订。 */
        public static final long NO_REVISION = -1L;

        PolicyKey key;
        String ruleId;
        long rowVersion;
        long revision;
        boolean enabled;

        public PolicyResult(PolicyKey key, String ruleId, long rowVersion, long revision, boolean enabled) {
            if (key == null || ruleId == null || ruleId.trim().isEmpty()
                    || key.getNamespace() == null || key.getNamespace().trim().isEmpty()
                    || rowVersion < 0L || revision < NO_REVISION) {
                throw new IllegalArgumentException("限频策略结果缺少合法键、规则编号或版本");
            }
            this.key = key;
            this.ruleId = ruleId;
            this.rowVersion = rowVersion;
            this.revision = revision;
            this.enabled = enabled;
        }
    }

    /**
     * 适配器必须区分确定失败和远端结果未知，License 才能选择重试或回读。
     */
    enum FailureKind {
        REJECTED,
        CONFLICT,
        UNAVAILABLE,
        UNCERTAIN,
        INVALID_RESPONSE
    }

    /**
     * 限频履约端口异常；不携带上游原始响应，避免把凭据或管理端内部信息泄露到业务面。
     */
    class Failure extends RuntimeException {
        private final FailureKind kind;

        public Failure(FailureKind kind, Throwable cause) {
            super(kind.name(), cause);
            this.kind = kind;
        }

        public FailureKind getKind() {
            return kind;
        }
    }
}

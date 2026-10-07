package io.github.surezzzzzz.sdk.audit.limiter.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 类型化限流策略管理审计记录
 * <p>只承载受控审计字段：操作、服务、资源、维度、选择器、规则标识、revision、结果与受控原因；
 * 计数对象只保留命名空间受控摘要，不输出原始用户、客户、IP 或自定义 key。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public final class SmartRedisLimiterTypedAuditRecord {

    /**
     * 管理操作类型（CREATE/UPDATE/ENABLE/DISABLE/DELETE）
     */
    private final String operation;

    /**
     * 服务编码
     */
    private final String serviceCode;

    /**
     * 资源编码
     */
    private final String resourceCode;

    /**
     * 计数维度
     */
    private final String dimension;

    /**
     * 规则选择器
     */
    private final String selector;

    /**
     * 计数对象的命名空间受控摘要
     */
    private final String objectDigest;

    /**
     * 管理端分配的规则标识（无则 null）
     */
    private final Long ruleId;

    /**
     * 服务策略版本
     */
    private final Long revision;

    /**
     * 操作结果（SUCCESS/FAILURE）
     */
    private final String result;

    /**
     * 受控原因
     */
    private final String reason;

    /**
     * 操作人短摘要（resource:v1:sha256: 前缀，不含完整身份事实）
     */
    private final String operatorDigest;

    /**
     * 事件发生时间
     */
    private final java.time.Instant occurredAt;
}

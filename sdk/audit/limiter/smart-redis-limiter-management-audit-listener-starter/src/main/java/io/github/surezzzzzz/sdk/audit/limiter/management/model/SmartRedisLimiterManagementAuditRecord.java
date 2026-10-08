package io.github.surezzzzzz.sdk.audit.limiter.management.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 三元组限流策略管理审计记录
 *
 * <p>只提取动作事实字段：策略键三元组与操作人落原文（操作人来自资源上下文的
 * 已验证稳定标识，审计需要可读操作人）；策略窗口数值快照不进入审计记录，
 * 不从审计侧反查身份系统。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmartRedisLimiterManagementAuditRecord {

    /**
     * 管理操作类型编码（CREATE/UPDATE/ENABLE/DISABLE/DELETE）
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
     * 策略对象（三元组身份之一）
     */
    private final String subject;

    /**
     * 变更前启停状态（创建为 null）
     */
    private final Boolean beforeEnabled;

    /**
     * 变更后启停状态（删除为 null）
     */
    private final Boolean afterEnabled;

    /**
     * 服务策略版本
     */
    private final Long revision;

    /**
     * 操作人（事件携带的已验证稳定主体标识）
     */
    private final String operator;

    /**
     * 事件发生时间
     */
    private final Instant occurredAt;
}

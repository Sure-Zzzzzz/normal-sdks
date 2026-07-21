package io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity;

import lombok.*;

import java.sql.Timestamp;

/**
 * Outbox 数据库记录实体，仅供持久化和默认执行链使用
 *
 * @author surezzzzzz
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"recordKey", "payloadJson", "headersJson", "attributesJson", "traceId", "lastErrorSummary"})
public class OutboxRecordEntity {
    /**
     * 主键
     */
    private Long id;
    /**
     * 消息 ID，全局唯一，消费端幂等键
     */
    private String messageId;
    /**
     * 目标 Kafka topic
     */
    private String topic;
    /**
     * Kafka record key，可为 null
     */
    private String recordKey;
    /**
     * route 规则匹配 key，可为 null
     */
    private String routeKey;
    /**
     * 显式 datasource，可为 null
     */
    private String datasourceKey;
    /**
     * 指定分区，可为 null
     */
    private Integer partition;
    /**
     * ProducerRecord 时间戳（ms），可为 null
     */
    private Long messageTimestamp;
    /**
     * 消息类型
     */
    private String messageType;
    /**
     * payload 类型：STRING/JSON/NULL
     */
    private String payloadKind;
    /**
     * payload 快照；NULL 类型时为 null
     */
    private String payloadJson;
    /**
     * headers Map 序列化 JSON，可为 null
     */
    private String headersJson;
    /**
     * attributes Map 序列化 JSON，可为 null
     */
    private String attributesJson;
    /**
     * envelope 开关：null=跟随配置，1=开，0=关
     */
    private Boolean envelopeEnabled;
    /**
     * 保存时捕获的 traceId，可为 null
     */
    private String traceId;
    /**
     * 快照协议版本，当前固定为 1
     */
    private Integer schemaVersion;
    /**
     * 状态：PENDING/PROCESSING/RETRY_WAIT/SENT/POISON
     */
    private String status;
    /**
     * 已尝试投递总次数
     */
    private Integer attempt;
    /**
     * 允许被领取的最早时间
     */
    private Timestamp availableAt;
    /**
     * 当前持有租约的 worker token
     */
    private String ownerToken;
    /**
     * 租约到期时间
     */
    private Timestamp leaseUntil;
    /**
     * 最近一次失败的错误码
     */
    private String lastErrorCode;
    /**
     * 最近一次失败的脱敏摘要
     */
    private String lastErrorSummary;
    /**
     * broker 返回的实际 topic
     */
    private String brokerTopic;
    /**
     * broker 返回的分区
     */
    private Integer brokerPartition;
    /**
     * broker 返回的 offset
     */
    private Long brokerOffset;
    /**
     * broker 返回的时间戳（ms）
     */
    private Long brokerTimestamp;
    /**
     * 创建时间
     */
    private Timestamp createdAt;
    /**
     * 标记为 SENT 的时间
     */
    private Timestamp sentAt;
    /**
     * 更新时间
     */
    private Timestamp updatedAt;
    /**
     * 乐观锁版本号
     */
    private Long version;
}

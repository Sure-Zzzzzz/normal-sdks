package io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupBatchResult;

import java.sql.Timestamp;
import java.util.List;

/**
 * Kafka Outbox 持久化 SPI
 *
 * @author surezzzzzz
 */
public interface KafkaOutboxRepository {

    /**
     * 在当前事务中保存记录
     *
     * @param record 记录
     * @return 主键
     */
    Long save(OutboxRecordEntity record);

    /**
     * 在短事务中扫描并领取记录
     *
     * @param candidateLimit 候选上限
     * @param leaseMicros    租约微秒数
     * @return 已领取记录
     */
    List<OutboxRecordEntity> claim(int candidateLimit, long leaseMicros);

    /**
     * 条件标记发送成功
     *
     * @param record 当前记录
     * @return 是否成功
     */
    boolean markSent(OutboxRecordEntity record);

    /**
     * 条件标记等待重试
     *
     * @param record       当前记录
     * @param delayMicros  延迟微秒数
     * @param errorCode    错误码
     * @param errorSummary 脱敏摘要
     * @return 是否成功
     */
    boolean markRetry(OutboxRecordEntity record, long delayMicros, String errorCode, String errorSummary);

    /**
     * 条件标记毒消息
     *
     * @param record       当前记录
     * @param errorCode    错误码
     * @param errorSummary 脱敏摘要
     * @return 是否成功
     */
    boolean markPoison(OutboxRecordEntity record, String errorCode, String errorSummary);

    /**
     * 停机发送前条件释放租约
     *
     * @param record       当前记录
     * @param errorCode    错误码
     * @param errorSummary 脱敏摘要
     * @return 是否成功
     */
    boolean releaseBeforeSend(OutboxRecordEntity record, String errorCode, String errorSummary);

    /**
     * 按数据库时钟计算固定过期边界
     *
     * @param retentionDays 保留天数
     * @return 过期边界
     */
    Timestamp resolveExpireBefore(int retentionDays);

    /**
     * 在单个短事务中查询并删除一批清理候选
     *
     * @param expireBefore 固定过期边界
     * @param lastSentAt   上一批游标时间
     * @param lastId       上一批游标主键
     * @param batchSize    批次大小
     * @return 单批清理结果
     */
    OutboxCleanupBatchResult cleanupBatch(Timestamp expireBefore, Timestamp lastSentAt,
                                          Long lastId, int batchSize);
}

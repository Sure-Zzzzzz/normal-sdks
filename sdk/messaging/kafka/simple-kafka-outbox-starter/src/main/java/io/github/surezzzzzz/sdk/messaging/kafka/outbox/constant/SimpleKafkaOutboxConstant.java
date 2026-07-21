package io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant;

/**
 * Simple Kafka Outbox 常量
 *
 * @author surezzzzzz
 */
public final class SimpleKafkaOutboxConstant {

    private SimpleKafkaOutboxConstant() {
        throw new UnsupportedOperationException(UTILITY_CLASS_MESSAGE);
    }

    // ==================== 配置常量 ====================

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.messaging.kafka.outbox";
    public static final String CONFIG_PROPERTY_ENABLE = "enable";
    public static final String CONFIG_PROPERTY_WORKER_ENABLE = "worker.enable";
    public static final String CONFIG_PROPERTY_CLEANUP_ENABLE = "cleanup.enable";
    public static final String BOOLEAN_TRUE = "true";
    public static final boolean DEFAULT_ENABLE = false;
    public static final String DEFAULT_RESOURCE_BEAN_NAME = "";
    public static final String DEFAULT_TABLE_NAME = "simple_kafka_outbox";
    public static final boolean DEFAULT_WORKER_ENABLE = true;
    public static final int DEFAULT_WORKER_CONCURRENCY = 1;
    public static final int DEFAULT_WORKER_BATCH_SIZE = 20;
    public static final long DEFAULT_SCAN_INTERVAL_MS = 500L;
    public static final long DEFAULT_IDLE_INTERVAL_MS = 2000L;
    public static final long DEFAULT_LEASE_MS = 30000L;
    public static final long DEFAULT_SHUTDOWN_AWAIT_MS = 20000L;
    public static final long DEFAULT_SEND_TIMEOUT_MS = 25000L;
    public static final int DEFAULT_MAX_ATTEMPTS = 10;
    public static final long DEFAULT_RETRY_INITIAL_INTERVAL_MS = 1000L;
    public static final double DEFAULT_RETRY_MULTIPLIER = 2.0D;
    public static final long DEFAULT_RETRY_MAX_INTERVAL_MS = 300000L;
    public static final double DEFAULT_RETRY_JITTER_FACTOR = 0.2D;
    public static final boolean DEFAULT_CLEANUP_ENABLE = true;
    public static final int DEFAULT_CLEANUP_RETENTION_DAYS = 7;
    public static final int DEFAULT_CLEANUP_BATCH_SIZE = 500;
    public static final long DEFAULT_CLEANUP_INTERVAL_MS = 3600000L;

    // ==================== 协议和边界常量 ====================

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_MESSAGE_ID_LENGTH = 191;
    public static final int MAX_TABLE_NAME_LENGTH = 64;
    public static final int MAX_ERROR_SUMMARY_LENGTH = 512;
    public static final int MAX_SAFE_DISPLAY_LENGTH = 256;
    public static final String TABLE_NAME_PATTERN = "[a-zA-Z0-9_]+";
    public static final String SAFE_VALUE_UNAVAILABLE = "<unsafe>";
    public static final String EMPTY_VALUE = "";
    public static final String UTILITY_CLASS_MESSAGE = "Utility class";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_TRACE_ID_WITH_HYPHEN = "trace-id";
    public static final String MDC_X_TRACE_ID = "X-B3-TraceId";
    public static final long MILLIS_TO_MICROS = 1000L;
    public static final long DAY_TO_MILLIS = 86400000L;
    public static final int ZERO = 0;
    public static final int ONE = 1;
    public static final int TWO = 2;
    public static final long ZERO_LONG = 0L;
    public static final double ZERO_DOUBLE = 0.0D;
    public static final double ONE_DOUBLE = 1.0D;
    public static final int SMART_LIFECYCLE_PHASE = Integer.MAX_VALUE - 100;

    // ==================== Bean 与线程常量 ====================

    public static final String BEAN_NAMED_JDBC_TEMPLATE = "simpleKafkaOutboxNamedParameterJdbcTemplate";
    public static final String BEAN_TRANSACTION_TEMPLATE = "simpleKafkaOutboxTransactionTemplate";
    public static final String BEAN_TASK_EXECUTOR = "simpleKafkaOutboxTaskExecutor";
    public static final String BEAN_WORKER_SCHEDULER = "simpleKafkaOutboxWorkerScheduler";
    public static final String BEAN_CLEANUP_SCHEDULER = "simpleKafkaOutboxCleanupScheduler";
    public static final String WORKER_THREAD_PREFIX = "simple-kafka-outbox-worker-";
    public static final String WORKER_SCHEDULER_THREAD_PREFIX = "simple-kafka-outbox-worker-scheduler-";
    public static final String CLEANUP_SCHEDULER_THREAD_PREFIX = "simple-kafka-outbox-cleanup-scheduler-";
    public static final int DEFAULT_SCHEDULER_POOL_SIZE = ONE;
    public static final String WORKER_INSTANCE_PREFIX = "outbox-worker-";

    // ==================== JDBC 参数常量 ====================

    public static final String PARAM_ID = "id";
    public static final String PARAM_MESSAGE_ID = "messageId";
    public static final String PARAM_TOPIC = "topic";
    public static final String PARAM_RECORD_KEY = "recordKey";
    public static final String PARAM_ROUTE_KEY = "routeKey";
    public static final String PARAM_DATASOURCE_KEY = "datasourceKey";
    public static final String PARAM_PARTITION = "partition";
    public static final String PARAM_MESSAGE_TIMESTAMP = "messageTimestamp";
    public static final String PARAM_MESSAGE_TYPE = "messageType";
    public static final String PARAM_PAYLOAD_KIND = "payloadKind";
    public static final String PARAM_PAYLOAD_JSON = "payloadJson";
    public static final String PARAM_HEADERS_JSON = "headersJson";
    public static final String PARAM_ATTRIBUTES_JSON = "attributesJson";
    public static final String PARAM_ENVELOPE_ENABLED = "envelopeEnabled";
    public static final String PARAM_TRACE_ID = "traceId";
    public static final String PARAM_SCHEMA_VERSION = "schemaVersion";
    public static final String PARAM_STATUS = "status";
    public static final String PARAM_PENDING_STATUS = "pendingStatus";
    public static final String PARAM_PROCESSING_STATUS = "processingStatus";
    public static final String PARAM_RETRY_WAIT_STATUS = "retryWaitStatus";
    public static final String PARAM_SENT_STATUS = "sentStatus";
    public static final String PARAM_POISON_STATUS = "poisonStatus";
    public static final String PARAM_OWNER_TOKEN = "ownerToken";
    public static final String PARAM_VERSION = "version";
    public static final String PARAM_LEASE_MICROS = "leaseMicros";
    public static final String PARAM_DELAY_MICROS = "delayMicros";
    public static final String PARAM_ERROR_CODE = "errorCode";
    public static final String PARAM_ERROR_SUMMARY = "errorSummary";
    public static final String PARAM_BROKER_TOPIC = "brokerTopic";
    public static final String PARAM_BROKER_PARTITION = "brokerPartition";
    public static final String PARAM_BROKER_OFFSET = "brokerOffset";
    public static final String PARAM_BROKER_TIMESTAMP = "brokerTimestamp";
    public static final String PARAM_CANDIDATE_LIMIT = "candidateLimit";
    public static final String PARAM_RETENTION_DAYS = "retentionDays";
    public static final String PARAM_EXPIRE_BEFORE = "expireBefore";
    public static final String PARAM_LAST_SENT_AT = "lastSentAt";
    public static final String PARAM_LAST_ID = "lastId";
    public static final String PARAM_BATCH_SIZE = "batchSize";
    public static final String PARAM_CANDIDATE_IDS = "candidateIds";

    // ==================== SQL 模板 ====================

    public static final String SQL_INSERT_TEMPLATE = "INSERT INTO %s (message_id, topic, record_key, route_key, datasource_key, `partition`, message_timestamp, message_type, payload_kind, payload_json, headers_json, attributes_json, envelope_enabled, trace_id, schema_version, status, attempt, available_at, version) VALUES (:messageId, :topic, :recordKey, :routeKey, :datasourceKey, :partition, :messageTimestamp, :messageType, :payloadKind, :payloadJson, :headersJson, :attributesJson, :envelopeEnabled, :traceId, :schemaVersion, :status, 0, CURRENT_TIMESTAMP(3), 0)";
    public static final String SQL_SELECT_READY_CANDIDATE_TEMPLATE = "SELECT id, version, available_at AS eligible_at FROM %s WHERE status IN (:pendingStatus, :retryWaitStatus) AND available_at <= CURRENT_TIMESTAMP(3) ORDER BY available_at, id LIMIT :candidateLimit";
    public static final String SQL_SELECT_EXPIRED_CANDIDATE_TEMPLATE = "SELECT id, version, lease_until AS eligible_at FROM %s WHERE status = :processingStatus AND lease_until < CURRENT_TIMESTAMP(3) ORDER BY lease_until, id LIMIT :candidateLimit";
    public static final String SQL_CLAIM_TEMPLATE = "UPDATE %s SET status = :processingStatus, owner_token = :ownerToken, lease_until = TIMESTAMPADD(MICROSECOND, :leaseMicros, CURRENT_TIMESTAMP(3)), attempt = attempt + 1, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) WHERE id = :id AND ((status IN (:pendingStatus, :retryWaitStatus) AND available_at <= CURRENT_TIMESTAMP(3)) OR (status = :processingStatus AND lease_until < CURRENT_TIMESTAMP(3))) AND version = :version";
    public static final String SQL_SELECT_BY_OWNER_TEMPLATE = "SELECT * FROM %s WHERE id = :id AND status = :processingStatus AND owner_token = :ownerToken AND version = :version";
    public static final String SQL_MARK_SENT_TEMPLATE = "UPDATE %s SET status = :sentStatus, owner_token = NULL, lease_until = NULL, last_error_code = NULL, last_error_summary = NULL, broker_topic = :brokerTopic, broker_partition = :brokerPartition, broker_offset = :brokerOffset, broker_timestamp = :brokerTimestamp, sent_at = CURRENT_TIMESTAMP(3), version = version + 1, updated_at = CURRENT_TIMESTAMP(3) WHERE id = :id AND status = :processingStatus AND owner_token = :ownerToken AND version = :version";
    public static final String SQL_MARK_RETRY_TEMPLATE = "UPDATE %s SET status = :retryWaitStatus, owner_token = NULL, lease_until = NULL, available_at = TIMESTAMPADD(MICROSECOND, :delayMicros, CURRENT_TIMESTAMP(3)), last_error_code = :errorCode, last_error_summary = :errorSummary, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) WHERE id = :id AND status = :processingStatus AND owner_token = :ownerToken AND version = :version";
    public static final String SQL_MARK_POISON_TEMPLATE = "UPDATE %s SET status = :poisonStatus, owner_token = NULL, lease_until = NULL, last_error_code = :errorCode, last_error_summary = :errorSummary, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) WHERE id = :id AND status = :processingStatus AND owner_token = :ownerToken AND version = :version";
    public static final String SQL_RELEASE_TEMPLATE = "UPDATE %s SET status = :retryWaitStatus, owner_token = NULL, lease_until = NULL, available_at = CURRENT_TIMESTAMP(3), last_error_code = :errorCode, last_error_summary = :errorSummary, version = version + 1, updated_at = CURRENT_TIMESTAMP(3) WHERE id = :id AND status = :processingStatus AND owner_token = :ownerToken AND version = :version";
    public static final String SQL_SELECT_EXPIRE_BEFORE_TEMPLATE = "SELECT DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL %d DAY)";
    public static final String SQL_SELECT_CLEANUP_FIRST_CANDIDATE_TEMPLATE = "SELECT id, sent_at FROM %s WHERE status = :sentStatus AND sent_at < :expireBefore ORDER BY sent_at, id LIMIT :batchSize";
    public static final String SQL_SELECT_CLEANUP_NEXT_CANDIDATE_TEMPLATE = "SELECT id, sent_at FROM %s WHERE status = :sentStatus AND sent_at < :expireBefore AND (sent_at > :lastSentAt OR (sent_at = :lastSentAt AND id > :lastId)) ORDER BY sent_at, id LIMIT :batchSize";
    public static final String SQL_DELETE_CLEANUP_TEMPLATE = "DELETE FROM %s WHERE status = :sentStatus AND sent_at < :expireBefore AND id IN (:candidateIds)";

    // ==================== 校验和错误摘要常量 ====================

    public static final String REASON_PROPERTIES_EMPTY = "properties 不能为空";
    public static final String REASON_DATASOURCE_AMBIGUOUS = "DataSource 数量不唯一，必须配置 data-source-bean-name";
    public static final String REASON_DATASOURCE_MISSING = "指定的 DataSource 不存在或类型不匹配";
    public static final String REASON_TX_MANAGER_AMBIGUOUS = "DataSourceTransactionManager 数量不唯一，必须配置 transaction-manager-bean-name";
    public static final String REASON_TX_MANAGER_MISSING = "指定的事务管理器不存在或类型不匹配";
    public static final String REASON_TX_DATASOURCE_MISMATCH = "事务管理器管理的 DataSource 不是选中的实例";
    public static final String REASON_TABLE_NAME_INVALID = "table-name 仅允许长度不超过 64 的字母、数字和下划线";
    public static final String REASON_WORKER_CONFIG_INVALID = "worker concurrency、batch-size 和时间配置必须为正数";
    public static final String REASON_SEND_TIMEOUT_INVALID = "send.timeout-ms 必须大于 0 且小于 worker.lease-ms";
    public static final String REASON_SHUTDOWN_TIMEOUT_INVALID = "worker.shutdown-await-ms 必须为正数且不大于 worker.lease-ms";
    public static final String REASON_RETRY_CONFIG_INVALID = "retry 配置超出有效范围";
    public static final String REASON_CLEANUP_CONFIG_INVALID = "cleanup 配置必须为正数且不能发生时间乘法溢出";
    public static final String REASON_LEASE_OVERFLOW = "worker.lease-ms 转换为微秒后溢出";
    public static final String REASON_TRANSACTION_INACTIVE = "当前没有活跃的 Spring 本地事务";
    public static final String REASON_TRANSACTION_RESOURCE_MISSING = "当前事务未绑定选中的 DataSource";
    public static final String REASON_TRANSACTION_READ_ONLY = "当前事务是只读事务";
    public static final String REASON_MESSAGE_EMPTY = "message 不能为空";
    public static final String REASON_TOPIC_EMPTY = "topic 必须显式提供且不能为空";
    public static final String REASON_MESSAGE_ID_INVALID = "messageId 不能为空且长度不能超过 191";
    public static final String REASON_PARTITION_INVALID = "partition 不能小于 0";
    public static final String REASON_TIMESTAMP_INVALID = "timestamp 不能小于 0";
    public static final String REASON_FUTURE_EMPTY = "publisher 返回的 Future 不能为空";
    public static final String REASON_RESULT_EMPTY = "publisher 返回的结果不能为空";
    public static final String REASON_RESULT_MESSAGE_ID_MISMATCH = "publisher 结果 messageId 与快照不一致";
    public static final String REASON_RESULT_METADATA_INVALID = "publisher 结果 broker metadata 非法";
    public static final String REASON_SCHEMA_UNSUPPORTED = "schemaVersion 不受支持";
    public static final String REASON_PAYLOAD_KIND_UNSUPPORTED = "payloadKind 不受支持";
    public static final String ERROR_CODE_SEND_UNKNOWN = "KAFKA_OUTBOX_SEND_UNKNOWN";
    public static final String ERROR_CODE_SHUTDOWN_RELEASE = "KAFKA_OUTBOX_SHUTDOWN_RELEASE";
    public static final String ERROR_SUMMARY_TIMEOUT = "发送等待超时，结果未知";
    public static final String ERROR_SUMMARY_INTERRUPTED = "发送等待被中断，结果未知";
    public static final String ERROR_SUMMARY_CANCELLED = "发送 Future 被取消，结果未知";
    public static final String ERROR_SUMMARY_INVALID_RESULT = "publisher 返回非法结果";
    public static final String ERROR_SUMMARY_SHUTDOWN_RELEASE = "应用停机，发送前释放租约";
    public static final String ERROR_SUMMARY_PUBLISHER_FAILURE = "Kafka Publisher 发送失败";
    public static final String ERROR_SUMMARY_SNAPSHOT_FAILURE = "消息快照重建失败";
}

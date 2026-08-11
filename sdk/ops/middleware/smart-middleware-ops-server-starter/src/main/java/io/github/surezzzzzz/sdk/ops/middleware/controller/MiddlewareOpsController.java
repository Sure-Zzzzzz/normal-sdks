package io.github.surezzzzzz.sdk.ops.middleware.controller;

import io.github.surezzzzzz.sdk.ops.middleware.annotation.SmartMiddlewareOpsServerComponent;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditSearchService;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditTimeRange;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditTimeRangeResolver;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogRequest;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogResponse;
import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import io.github.surezzzzzz.sdk.ops.middleware.controller.response.MiddlewareOpsAuditPageResponse;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.*;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.*;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.MysqlDatasourceStatusRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.MysqlDatasourceStatusResponse;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.MysqlSelectRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.MysqlSelectResponse;
import io.github.surezzzzzz.sdk.ops.middleware.redis.*;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsServerEngine;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Middleware Ops 只读资源接口。
 *
 * @author surezzzzzz
 */
@SmartMiddlewareOpsServerComponent
@RestController
@RequestMapping("${io.github.surezzzzzz.sdk.ops.middleware.api-base-path:/api/v1/middleware-ops}")
public class MiddlewareOpsController {

    private final MiddlewareOpsServerEngine engine;
    private final MiddlewareOpsAuditSearchService auditSearchService;
    private final SmartMiddlewareOpsServerProperties properties;

    /**
     * 创建只读资源控制器。
     */
    public MiddlewareOpsController(MiddlewareOpsServerEngine engine, MiddlewareOpsAuditSearchService auditSearchService,
                                   SmartMiddlewareOpsServerProperties properties) {
        this.engine = engine;
        this.auditSearchService = auditSearchService;
        this.properties = properties;
    }

    /**
     * 获取 Elasticsearch 启动期已初始化的数据源目录。
     */
    @GetMapping("/elasticsearch/catalog")
    public DatasourceCatalogResponse elasticsearchDatasourceCatalog() {
        return datasourceCatalog(MiddlewareType.ELASTICSEARCH);
    }

    /**
     * 获取 Redis 启动期已初始化的数据源目录。
     */
    @GetMapping("/redis/catalog")
    public DatasourceCatalogResponse redisDatasourceCatalog() {
        return datasourceCatalog(MiddlewareType.REDIS);
    }

    /**
     * 获取 Kafka 启动期已初始化的数据源目录。
     */
    @GetMapping("/kafka/catalog")
    public DatasourceCatalogResponse kafkaDatasourceCatalog() {
        return datasourceCatalog(MiddlewareType.KAFKA);
    }

    /**
     * 获取 MySQL 启动期已初始化的数据源目录。
     */
    @GetMapping("/mysql/catalog")
    public DatasourceCatalogResponse mysqlDatasourceCatalog() {
        return datasourceCatalog(MiddlewareType.MYSQL);
    }

    /**
     * 获取 Elasticsearch 数据源安全摘要。
     */
    @GetMapping("/elasticsearch/datasources/{datasourceKey}/summary")
    public ElasticsearchSummaryResponse elasticsearchSummary(@PathVariable String datasourceKey) {
        return engine.execute(ElasticsearchSummaryRequest.builder().datasourceKey(datasourceKey).build(),
                ElasticsearchSummaryResponse.class);
    }

    /**
     * 获取指定 Elasticsearch 数据源的受限索引目录。
     */
    @GetMapping("/elasticsearch/datasources/{datasourceKey}/indices")
    public ElasticsearchIndexListResponse elasticsearchIndices(@PathVariable String datasourceKey) {
        return engine.execute(ElasticsearchIndexListRequest.builder().datasourceKey(datasourceKey).build(),
                ElasticsearchIndexListResponse.class);
    }

    /**
     * 使用 GET 参数执行受控 Elasticsearch JSON DSL 查询。
     */
    @GetMapping("/elasticsearch/datasources/{datasourceKey}/documents")
    public ElasticsearchDocumentQueryResponse elasticsearchDocuments(@PathVariable String datasourceKey,
                                                                     @RequestParam String index,
                                                                     @RequestParam String dsl,
                                                                     @RequestParam(required = false) Integer page,
                                                                     @RequestParam(required = false) Integer size) {
        String decodedDsl = ElasticsearchDocumentQueryTransport.decodeDsl(dsl, properties.getQuery().getMaxDslLength());
        return engine.execute(ElasticsearchDocumentQueryRequest.builder().datasourceKey(datasourceKey).index(index)
                        .dsl(decodedDsl).page(resolvePage(page)).size(resolveSize(size)).build(),
                ElasticsearchDocumentQueryResponse.class);
    }

    /**
     * 获取 MySQL 数据源概览安全状态。
     */
    @GetMapping("/mysql/datasources/{datasourceKey}/overview-status")
    public MysqlDatasourceStatusResponse mysqlDatasourceOverviewStatus(@PathVariable String datasourceKey) {
        return engine.execute(MysqlDatasourceStatusRequest.forOverview(datasourceKey), MysqlDatasourceStatusResponse.class);
    }

    /**
     * 探测 MySQL 数据源安全状态。
     */
    @GetMapping("/mysql/datasources/{datasourceKey}/status")
    public MysqlDatasourceStatusResponse mysqlDatasourceStatus(@PathVariable String datasourceKey) {
        return engine.execute(MysqlDatasourceStatusRequest.builder().datasourceKey(datasourceKey).build(),
                MysqlDatasourceStatusResponse.class);
    }

    /**
     * 执行受控的 MySQL 单条 SELECT。
     */
    @GetMapping("/mysql/datasources/{datasourceKey}/select")
    public MysqlSelectResponse mysqlSelect(@PathVariable String datasourceKey, @RequestParam String sql,
                                           @RequestParam(required = false) Integer size) {
        return engine.execute(MysqlSelectRequest.builder().datasourceKey(datasourceKey).sql(sql)
                .size(resolveSize(size)).build(), MysqlSelectResponse.class);
    }

    /**
     * 获取 Redis 数据源概览安全状态。
     */
    @GetMapping("/redis/datasources/overview")
    public RedisDatasourceListResponse redisDatasourceOverview() {
        return engine.execute(RedisDatasourceListRequest.forOverview(), RedisDatasourceListResponse.class);
    }

    /**
     * 获取 Redis 数据源清单。
     */
    @GetMapping("/redis/datasources")
    public RedisDatasourceListResponse redisDatasources() {
        return engine.execute(new RedisDatasourceListRequest(), RedisDatasourceListResponse.class);
    }

    /**
     * 获取 Redis 数据源安全摘要。
     */
    @GetMapping("/redis/datasources/{datasourceKey}/summary")
    public RedisDatasourceResponse redisSummary(@PathVariable String datasourceKey) {
        return engine.execute(RedisSummaryRequest.builder().datasourceKey(datasourceKey).build(),
                RedisDatasourceResponse.class);
    }

    /**
     * 查询 Redis 精确 key 的存在性、类型与 TTL。
     */
    @GetMapping("/redis/datasources/{datasourceKey}/keys/metadata")
    public RedisKeyMetadataResponse redisKeyMetadata(@PathVariable String datasourceKey, @RequestParam String key) {
        return engine.execute(RedisKeyMetadataRequest.builder().datasourceKey(datasourceKey).key(key).build(),
                RedisKeyMetadataResponse.class);
    }

    /**
     * 读取 Redis 精确 key 的已检测类型数据。
     */
    @GetMapping("/redis/datasources/{datasourceKey}/keys/value")
    public RedisKeyReadResponse redisKeyValue(@PathVariable String datasourceKey, @RequestParam String key,
                                              @RequestParam(required = false) String field,
                                              @RequestParam(required = false) Long offset,
                                              @RequestParam(required = false) Integer size) {
        return engine.execute(RedisKeyReadRequest.builder().datasourceKey(datasourceKey).key(key).field(field)
                .offset(offset == null ? 0L : offset).size(resolveSize(size)).build(), RedisKeyReadResponse.class);
    }

    /**
     * 获取 Kafka 数据源概览安全诊断。
     */
    @GetMapping("/kafka/datasources/overview")
    public KafkaDatasourceListResponse kafkaDatasourceOverview() {
        return engine.execute(KafkaDatasourceListRequest.forOverview(), KafkaDatasourceListResponse.class);
    }

    /**
     * 获取 Kafka 数据源诊断清单。
     */
    @GetMapping("/kafka/datasources")
    public KafkaDatasourceListResponse kafkaDatasources() {
        return engine.execute(new KafkaDatasourceListRequest(), KafkaDatasourceListResponse.class);
    }

    /**
     * 获取 Kafka topic 清单。
     */
    @GetMapping("/kafka/datasources/{datasourceKey}/topics")
    public KafkaTopicListResponse kafkaTopics(@PathVariable String datasourceKey,
                                              @RequestParam(required = false) Integer size) {
        return engine.execute(KafkaTopicListRequest.builder().datasourceKey(datasourceKey)
                .size(resolveSize(size)).build(), KafkaTopicListResponse.class);
    }

    /**
     * 获取 Kafka 消费组清单。
     */
    @GetMapping("/kafka/datasources/{datasourceKey}/consumer-groups")
    public KafkaConsumerGroupListResponse kafkaConsumerGroups(@PathVariable String datasourceKey,
                                                              @RequestParam(required = false) Integer size) {
        return engine.execute(KafkaConsumerGroupListRequest.builder().datasourceKey(datasourceKey)
                .size(resolveSize(size)).build(), KafkaConsumerGroupListResponse.class);
    }

    /**
     * 查询手工输入 Topic 的分区与 offset 状态。
     */
    @GetMapping("/kafka/datasources/{datasourceKey}/topics/runtime")
    public KafkaTopicRuntimeResponse kafkaTopicRuntime(@PathVariable String datasourceKey, @RequestParam String topic) {
        return engine.execute(KafkaTopicRuntimeRequest.builder().datasourceKey(datasourceKey).topic(topic).build(),
                KafkaTopicRuntimeResponse.class);
    }

    /**
     * 查询手工输入消费组的分区积压。
     */
    @GetMapping("/kafka/datasources/{datasourceKey}/consumer-groups/lag")
    public KafkaConsumerGroupLagListResponse kafkaConsumerGroupLag(@PathVariable String datasourceKey,
                                                                   @RequestParam String groupId,
                                                                   @RequestParam(required = false) Integer size) {
        return engine.execute(KafkaConsumerGroupLagListRequest.builder().datasourceKey(datasourceKey).groupId(groupId)
                .size(resolveSize(size)).build(), KafkaConsumerGroupLagListResponse.class);
    }

    /**
     * 获取 Elasticsearch 工作区的脱敏审计记录。
     */
    @GetMapping("/audit/elasticsearch/records")
    public MiddlewareOpsAuditPageResponse elasticsearchAuditRecords(@RequestParam(required = false) Integer page,
                                                                    @RequestParam(required = false) Integer size,
                                                                    @RequestParam(required = false) String range,
                                                                    @RequestParam(required = false) String from,
                                                                    @RequestParam(required = false) String to) {
        return auditRecords(MiddlewareType.ELASTICSEARCH, page, size, range, from, to);
    }

    /**
     * 获取 Redis 工作区的脱敏审计记录。
     */
    @GetMapping("/audit/redis/records")
    public MiddlewareOpsAuditPageResponse redisAuditRecords(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            @RequestParam(required = false) String range,
                                                            @RequestParam(required = false) String from,
                                                            @RequestParam(required = false) String to) {
        return auditRecords(MiddlewareType.REDIS, page, size, range, from, to);
    }

    /**
     * 获取 Kafka 工作区的脱敏审计记录。
     */
    @GetMapping("/audit/kafka/records")
    public MiddlewareOpsAuditPageResponse kafkaAuditRecords(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            @RequestParam(required = false) String range,
                                                            @RequestParam(required = false) String from,
                                                            @RequestParam(required = false) String to) {
        return auditRecords(MiddlewareType.KAFKA, page, size, range, from, to);
    }

    /**
     * 获取 MySQL 工作区的脱敏审计记录。
     */
    @GetMapping("/audit/mysql/records")
    public MiddlewareOpsAuditPageResponse mysqlAuditRecords(@RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size,
                                                            @RequestParam(required = false) String range,
                                                            @RequestParam(required = false) String from,
                                                            @RequestParam(required = false) String to) {
        return auditRecords(MiddlewareType.MYSQL, page, size, range, from, to);
    }

    /**
     * 拒绝 Ops 基路径内未发布的 HTTP 方法。
     */
    @RequestMapping(value = "/**", method = {RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
    public void unsupportedMethod() {
        throw new MiddlewareOpsException(HttpStatus.METHOD_NOT_ALLOWED, "不支持的请求方法");
    }

    private DatasourceCatalogResponse datasourceCatalog(MiddlewareType middlewareType) {
        return engine.execute(new DatasourceCatalogRequest(middlewareType), DatasourceCatalogResponse.class);
    }

    private MiddlewareOpsAuditPageResponse auditRecords(MiddlewareType middlewareType, Integer page, Integer size,
                                                        String range, String from, String to) {
        int actualPage = resolvePage(page);
        int actualSize = resolveSize(size);
        if ((long) (actualPage - 1) * actualSize + actualSize > properties.getAudit().getMaxOffset()) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "审计页码超出允许范围");
        }
        MiddlewareOpsAuditTimeRange timeRange = MiddlewareOpsAuditTimeRangeResolver.resolve(range, from, to,
                properties.getAudit());
        return auditSearchService.search(middlewareType, actualPage, actualSize, timeRange);
    }

    private int resolvePage(Integer page) {
        int actualPage = page == null ? 1 : page;
        if (actualPage <= 0) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "页码超出允许范围");
        }
        return actualPage;
    }

    private int resolveSize(Integer size) {
        int actualSize = size == null ? properties.getQuery().getDefaultSize() : size;
        if (actualSize <= 0 || actualSize > properties.getQuery().getMaxSize()) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "结果数量超出允许范围");
        }
        return actualSize;
    }
}

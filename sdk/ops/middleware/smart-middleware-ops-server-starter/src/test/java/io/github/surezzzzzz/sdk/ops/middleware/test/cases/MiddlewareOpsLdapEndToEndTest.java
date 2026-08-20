package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.controller.MiddlewareOpsController;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.adapter.DefaultElasticsearchOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.adapter.ElasticsearchOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.adapter.DefaultKafkaOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.adapter.KafkaOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.adapter.DefaultMysqlOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.adapter.MysqlOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.redis.adapter.DefaultRedisOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.redis.adapter.RedisOperationsViewAdapter;
import io.github.surezzzzzz.sdk.ops.middleware.service.DefaultMiddlewareOpsServerEngine;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsServerEngine;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import io.github.surezzzzzz.sdk.ops.middleware.support.MiddlewareOpsDigestHelper;
import io.github.surezzzzzz.sdk.ops.middleware.test.MiddlewareOpsLdapEndToEndTestConfiguration;
import io.github.surezzzzzz.sdk.ops.middleware.test.SmartMiddlewareOpsServerTestApplication;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LDAP 认证与四类 Route 多数据源默认自动配置的固定端到端测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {SmartMiddlewareOpsServerTestApplication.class,
        MiddlewareOpsLdapEndToEndTestConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MiddlewareOpsLdapEndToEndTest {

    private static final List<String> ELASTICSEARCH_DATASOURCES =
            Arrays.asList("primary", "secondary", "tertiary");
    private static final Set<String> REDIS_DATASOURCES = new HashSet<>(Arrays.asList(
            "redis3Standalone", "redis5Standalone", "redis7Standalone",
            "redis3Cluster", "redis5Cluster", "redis7Cluster"));
    private static final Set<String> REDIS_CLUSTER_DATASOURCES = new HashSet<>(Arrays.asList(
            "redis3Cluster", "redis5Cluster", "redis7Cluster"));
    private static final Set<String> REDIS_POOL_DATASOURCES = new HashSet<>(Arrays.asList(
            "redis3Standalone", "redis3Cluster"));
    private static final String REDIS_HASH_FIXTURE_KEY = "middleware-ops:fixture:local::{hash}";
    private static final String REDIS_DISCOVERY_STANDALONE_PREFIX = "middleware-ops:fixture:discovery:standalone:";
    private static final String REDIS_DISCOVERY_CLUSTER_PREFIX = "middleware-ops:fixture:discovery:cluster:";
    private static final Set<String> REDIS_DISCOVERY_CLUSTER_KEYS = new HashSet<>(Arrays.asList(
            "middleware-ops:fixture:discovery:cluster:{alpha}:key",
            "middleware-ops:fixture:discovery:cluster:{bravo}:key",
            "middleware-ops:fixture:discovery:cluster:{charlie}:key"));
    private static final String REDIS_HASH_FIXTURE_FIELD = "state";
    private static final String REDIS_HASH_FIXTURE_VALUE = "ready";
    private static final DateTimeFormatter AUDIT_TIME_FORMATTER = DateTimeFormatter.ofPattern(
            SmartMiddlewareOpsServerConstant.AUDIT_TIME_PATTERN);
    private static final Set<String> KAFKA_DATASOURCES = new HashSet<>(Arrays.asList(
            "default", "event", "v110", "v28", "v37", "tx37", "cluster"));
    private static final Set<String> MYSQL_DATASOURCES = new LinkedHashSet<>(Arrays.asList(
            "mysql57-ops", "mysql57-audit", "mysql84-ops", "mysql84-audit"));

    @Value("${io.github.surezzzzzz.sdk.ops.middleware.test.ldap.user-password}")
    private String ldapUserPassword;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MiddlewareOpsLdapEndToEndTestConfiguration.RecordingIdentityResolver identityResolver;

    @Autowired
    private MiddlewareOpsServerEngine engine;

    @Autowired
    private MiddlewareOpsController controller;

    @Autowired
    private SmartMiddlewareOpsServerProperties properties;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private ElasticsearchOperationsViewAdapter elasticsearchAdapter;

    @Autowired
    private RedisOperationsViewAdapter redisAdapter;

    @Autowired
    private KafkaOperationsViewAdapter kafkaAdapter;

    @Autowired
    private MysqlOperationsViewAdapter mysqlAdapter;

    @Autowired
    private SimpleElasticsearchRouteRegistry elasticsearchRegistry;

    @Autowired
    private SimpleRedisRouteRegistry redisRegistry;

    @Autowired
    private SimpleKafkaRouteRegistry kafkaRegistry;

    @Autowired
    private SimpleMysqlRouteRegistry mysqlRegistry;

    @Autowired
    private KafkaRouteDiagnostics kafkaDiagnostics;

    @LocalServerPort
    private int port;

    @Test
    void shouldExposeAllReadOnlyCapabilitiesThroughAuthenticatedDefaultRouteChain() throws Exception {
        log.info("开始验证 LDAP 与四类 Route 多数据源默认链路");
        assertDefaultRouteChain();

        assertAuthenticationFailures();
        assertHttpInputFailures();
        assertAutomaticOverviewLoadsDoNotWriteAudit();
        assertElasticsearchFieldCapabilities();
        assertAsyncElasticsearchDocumentAudit();
        assertRedisDatasources();
        assertRedisKeyDiscovery();
        assertAsyncRedisKeyAuditMasking();
        assertKafkaDatasources();
        assertAsyncKafkaDiagnosticAudits();
        assertMysqlDatasources();
        assertAsyncMysqlStatusAudit();
        assertAsyncAuditWriteAndRead();
        JsonNode mysqlAuditRecord = assertAsyncMysqlAuditMasking();
        assertExplicitAuditRange(mysqlAuditRecord);
        assertNotNull(identityResolver.getIdentity());
        log.info("默认链路认证身份：subject={}，mechanism={}", identityResolver.getIdentity().getSubject(),
                identityResolver.getIdentity().getAuthenticationMechanism());
        assertEquals("ops-user", identityResolver.getIdentity().getSubject());
        assertEquals("spring-security", identityResolver.getIdentity().getAuthenticationMechanism());
    }

    private void assertAuthenticationFailures() {
        log.info("验证未认证与错误 LDAP 凭据均被安全链拒绝");
        ResponseEntity<String> unauthenticated = restTemplate.getForEntity(url("/redis/datasources"), String.class);
        assertEquals(401, unauthenticated.getStatusCodeValue());
        assertEquals("no-store", unauthenticated.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertNotNull(unauthenticated.getHeaders().getFirst("X-Request-Id"));
        ResponseEntity<String> wrongPassword = restTemplate.withBasicAuth("ops-user", "wrong-password")
                .getForEntity(url("/redis/datasources"), String.class);
        assertEquals(401, wrongPassword.getStatusCodeValue());
        assertEquals("no-store", wrongPassword.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertNotNull(wrongPassword.getHeaders().getFirst("X-Request-Id"));
    }

    private void assertHttpInputFailures() throws Exception {
        log.info("验证数据源、分页与 HTTP 方法错误均返回受控安全响应");
        assertError("/elasticsearch/datasources/missing/summary", 404);
        assertError("/elasticsearch/datasources/missing/indices", 404);
        assertError("/redis/datasources/missing/summary", 404);
        assertError(redisDiscoveryUri("missing", "middleware-ops:fixture:", 1), 404);
        assertError(redisDiscoveryUri("redis7Standalone", "middleware-ops:fixture:*", 1), 400);
        assertError(redisDiscoveryUri("redis7Standalone", "middleware-ops:fixture:", 0), 400);
        assertError("/kafka/datasources/missing/topics?size=1", 404);
        assertError("/kafka/datasources/missing/consumer-groups?size=1", 404);
        assertError("/mysql/datasources/missing/status", 404);
        String rejectedSql = "SELECT * FROM test_route_marker";
        ResponseEntity<String> rejected = restTemplate.withBasicAuth("ops-user", ldapUserPassword)
                .getForEntity(mysqlSelectUri("mysql57-ops", rejectedSql, 1), String.class);
        assertEquals(400, rejected.getStatusCodeValue(), rejected.getBody());
        assertEquals("no-store", rejected.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        JsonNode rejectedBody = objectMapper.readTree(rejected.getBody());
        assertEquals("SELECT 投影仅支持显式的当前表字段", rejectedBody.path("message").asText());
        assertFalse(rejected.getBody().contains(rejectedSql));
        assertFalse(rejected.getBody().contains("test_route_marker"));
        String rejectedRequestId = requestId(rejected);
        JsonNode rejectedAudit = awaitAuditRecord("mysql", rejectedRequestId);
        assertEquals(400, rejectedAudit.path("httpStatus").asInt());
        assertEquals("MYSQL_SELECT", rejectedAudit.path("capability").asText());
        assertEquals("mysql57-ops", rejectedAudit.path("datasourceKey").asText());
        assertEquals(1, rejectedAudit.path("size").asInt());
        assertFalse(rejectedAudit.path("mysqlSql").asText().isEmpty());
        assertNotEquals(rejectedSql, rejectedAudit.path("mysqlSql").asText());
        assertSafeProjection(rejectedAudit, "exception", "message", "response", "request");
        assertError("/kafka/datasources/v110/topics?size=0", 400);
        assertError("/kafka/datasources/v110/topics?size=101", 400);
        ResponseEntity<String> csrfRejected = restTemplate.withBasicAuth("ops-user", ldapUserPassword)
                .postForEntity(url("/redis/datasources"), null, String.class);
        assertEquals(403, csrfRejected.getStatusCodeValue());
        assertEquals("no-store", csrfRejected.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertNotNull(csrfRejected.getHeaders().getFirst("X-Request-Id"));
    }

    private void assertDefaultRouteChain() throws Exception {
        assertTrue(engine instanceof DefaultMiddlewareOpsServerEngine);
        assertNotNull(controller);
        assertTrue(requestMappingHandlerMapping.getHandlerMethods().keySet().stream()
                .anyMatch(mapping -> matchesElasticsearchSummaryPath(mapping)));
        assertTrue(matchesPath("/api/v1/middleware-ops/elasticsearch/datasources/{datasourceKey}/indices"));
        assertTrue(matchesPath("/api/v1/middleware-ops/redis/datasources/overview"));
        assertTrue(matchesPath("/api/v1/middleware-ops/redis/datasources/{datasourceKey}/keys/discovery"));
        assertTrue(matchesPath("/api/v1/middleware-ops/kafka/datasources/overview"));
        assertTrue(matchesPath("/api/v1/middleware-ops/mysql/datasources/{datasourceKey}/overview-status"));
        assertCatalogRoutes();
        assertCatalogMiddlewareTypeCodes();
        assertTrue(elasticsearchAdapter instanceof DefaultElasticsearchOperationsViewAdapter);
        assertTrue(redisAdapter instanceof DefaultRedisOperationsViewAdapter);
        assertTrue(kafkaAdapter instanceof DefaultKafkaOperationsViewAdapter);
        assertTrue(mysqlAdapter instanceof DefaultMysqlOperationsViewAdapter);
        for (String datasourceKey : ELASTICSEARCH_DATASOURCES) {
            assertNotNull(elasticsearchRegistry.getClusterInfo(datasourceKey));
        }
        assertEquals(REDIS_DATASOURCES, redisRegistry.getDatasourceKeys());
        assertRedisPoolConfiguration();
        assertEquals(KAFKA_DATASOURCES, kafkaRegistry.getDatasourceKeys());
        assertEquals(MYSQL_DATASOURCES, mysqlRegistry.getDatasources());
        for (String datasourceKey : KAFKA_DATASOURCES) {
            assertNotNull(kafkaDiagnostics.getDiagnosticResult(datasourceKey));
        }
    }

    private void assertRedisPoolConfiguration() {
        log.info("验证 Redis Route 启用连接池与默认非池化 datasource 的 client configuration");
        for (String datasourceKey : REDIS_DATASOURCES) {
            RedisConnectionFactory connectionFactory = redisRegistry.getConnectionFactory(datasourceKey);
            assertTrue(connectionFactory instanceof LettuceConnectionFactory,
                    "Redis datasource 必须由 LettuceConnectionFactory 管理：" + datasourceKey);
            LettuceConnectionFactory lettuceConnectionFactory = (LettuceConnectionFactory) connectionFactory;
            assertEquals(REDIS_POOL_DATASOURCES.contains(datasourceKey),
                    lettuceConnectionFactory.getClientConfiguration() instanceof LettucePoolingClientConfiguration,
                    "Redis datasource 连接池状态不符合 fixture 配置：" + datasourceKey);
        }
    }

    private void assertCatalogRoutes() {
        assertTrue(matchesPath("/api/v1/middleware-ops/elasticsearch/catalog"));
        assertTrue(matchesPath("/api/v1/middleware-ops/redis/catalog"));
        assertTrue(matchesPath("/api/v1/middleware-ops/kafka/catalog"));
        assertTrue(matchesPath("/api/v1/middleware-ops/mysql/catalog"));
        assertFalse(matchesPath("/api/v1/middleware-ops/catalog"));
    }

    private void assertCatalogMiddlewareTypeCodes() throws Exception {
        for (MiddlewareType middlewareType : MiddlewareType.values()) {
            JsonNode items = success("/" + middlewareType.getCode() + "/catalog").path("items");
            for (JsonNode item : items) {
                assertEquals(middlewareType.getCode(), item.path("middlewareType").asText());
            }
        }
    }

    private boolean matchesPath(String path) {
        return requestMappingHandlerMapping.getHandlerMethods().keySet().stream().anyMatch(mapping -> {
            if (mapping.getPathPatternsCondition() != null) {
                return mapping.getPathPatternsCondition().getPatternValues().contains(path);
            }
            return mapping.getPatternsCondition() != null && mapping.getPatternsCondition().getPatterns().contains(path);
        });
    }

    private boolean matchesElasticsearchSummaryPath(org.springframework.web.servlet.mvc.method.RequestMappingInfo mapping) {
        if (mapping.getPathPatternsCondition() != null) {
            return mapping.getPathPatternsCondition().getPatternValues()
                    .contains("/api/v1/middleware-ops/elasticsearch/datasources/{datasourceKey}/summary");
        }
        return mapping.getPatternsCondition() != null && mapping.getPatternsCondition().getPatterns()
                .contains("/api/v1/middleware-ops/elasticsearch/datasources/{datasourceKey}/summary");
    }

    private String assertElasticsearchCatalog() throws Exception {
        log.info("验证 Elasticsearch 启动期数据源目录不写审计");
        ResponseEntity<String> operation = successResponse("/elasticsearch/catalog");
        JsonNode items = objectMapper.readTree(operation.getBody()).path("items");
        assertEquals(ELASTICSEARCH_DATASOURCES, stringValues(items, "datasourceKey"));
        return requestId(operation);
    }

    private List<String> assertElasticsearchSummaries() throws Exception {
        log.info("验证 Elasticsearch Route 多数据源安全摘要不写审计");
        List<String> requestIds = new ArrayList<>();
        for (String datasourceKey : ELASTICSEARCH_DATASOURCES) {
            ResponseEntity<String> operation = successResponse("/elasticsearch/datasources/" + datasourceKey + "/summary");
            JsonNode response = objectMapper.readTree(operation.getBody());
            assertEquals(datasourceKey, response.path("datasourceKey").asText());
            assertTrue(response.path("detected").asBoolean());
            assertFalse(response.path("detectedVersion").asText().isEmpty());
            assertFalse(response.path("effectiveVersion").asText().isEmpty());
            assertSafeProjection(response, "urls", "hosts", "username", "password", "index", "document");
            requestIds.add(requestId(operation));
        }
        return requestIds;
    }

    private List<String> assertElasticsearchIndices() throws Exception {
        log.info("验证 Elasticsearch Route 多数据源索引候选目录不写审计");
        List<String> requestIds = new ArrayList<>();
        for (String datasourceKey : ELASTICSEARCH_DATASOURCES) {
            ResponseEntity<String> operation = successResponse("/elasticsearch/datasources/" + datasourceKey + "/indices");
            JsonNode response = objectMapper.readTree(operation.getBody());
            assertEquals(datasourceKey, response.path("datasourceKey").asText());
            assertTrue(response.path("items").isArray());
            assertTrue(response.path("items").size() <= 100);
            String previous = null;
            for (JsonNode item : response.path("items")) {
                String index = item.asText();
                assertFalse(index.startsWith("."));
                if (previous != null) {
                    assertTrue(previous.compareTo(index) < 0);
                }
                previous = index;
            }
            assertSafeProjection(response, "urls", "hosts", "username", "password", "mapping", "settings", "aliases");
            requestIds.add(requestId(operation));
        }
        return requestIds;
    }

    private void assertAutomaticOverviewLoadsDoNotWriteAudit() throws Exception {
        List<String> elasticsearchRequestIds = new ArrayList<>();
        elasticsearchRequestIds.add(assertElasticsearchCatalog());
        elasticsearchRequestIds.addAll(assertElasticsearchSummaries());
        elasticsearchRequestIds.addAll(assertElasticsearchIndices());
        assertNoAuditRecords("elasticsearch", elasticsearchRequestIds);

        List<String> redisRequestIds = new ArrayList<>();
        redisRequestIds.add(requestId(successResponse("/redis/catalog")));
        ResponseEntity<String> redisOverview = successResponse("/redis/datasources/overview");
        assertEquals(REDIS_DATASOURCES, itemsBy(objectMapper.readTree(redisOverview.getBody()).path("items"),
                "datasourceKey").keySet());
        redisRequestIds.add(requestId(redisOverview));
        assertNoAuditRecords("redis", redisRequestIds);

        List<String> kafkaRequestIds = new ArrayList<>();
        kafkaRequestIds.add(requestId(successResponse("/kafka/catalog")));
        ResponseEntity<String> kafkaOverview = successResponse("/kafka/datasources/overview");
        assertEquals(KAFKA_DATASOURCES, itemsBy(objectMapper.readTree(kafkaOverview.getBody()).path("items"),
                "datasourceKey").keySet());
        kafkaRequestIds.add(requestId(kafkaOverview));
        assertNoAuditRecords("kafka", kafkaRequestIds);

        List<String> mysqlRequestIds = new ArrayList<>();
        mysqlRequestIds.add(requestId(successResponse("/mysql/catalog")));
        for (String datasourceKey : MYSQL_DATASOURCES) {
            ResponseEntity<String> mysqlOverview = successResponse("/mysql/datasources/" + datasourceKey
                    + "/overview-status");
            JsonNode status = objectMapper.readTree(mysqlOverview.getBody());
            assertEquals(datasourceKey, status.path("datasourceKey").asText());
            assertTrue(status.path("connected").asBoolean());
            assertSafeProjection(status, "url", "username", "password", "target", "host", "port");
            mysqlRequestIds.add(requestId(mysqlOverview));
        }
        assertNoAuditRecords("mysql", mysqlRequestIds);
    }

    private void assertElasticsearchFieldCapabilities() throws Exception {
        log.info("验证 Elasticsearch 字段能力返回 keyword 子字段及可聚合标记");
        JsonNode response = success("/elasticsearch/datasources/primary/fields?index=test_index_a");
        Map<String, JsonNode> fields = itemsBy(response.path("items"), "name");
        assertTrue(fields.containsKey("sequence"), "请先按 LOCAL_TEST_COMMANDS.md 准备固定 Elasticsearch fixture");
        assertTrue(fields.containsKey("message"), "请先按 LOCAL_TEST_COMMANDS.md 准备固定 Elasticsearch fixture");
        assertTrue(fields.containsKey("message.keyword"), "请先按 LOCAL_TEST_COMMANDS.md 准备固定 Elasticsearch fixture");
        assertTrue(fields.get("sequence").path("searchable").asBoolean());
        assertTrue(fields.get("sequence").path("aggregatable").asBoolean());
        assertTrue(fields.get("message").path("searchable").asBoolean());
        assertFalse(fields.get("message").path("aggregatable").asBoolean());
        assertTrue(fields.get("message.keyword").path("searchable").asBoolean());
        assertTrue(fields.get("message.keyword").path("aggregatable").asBoolean());
        assertSafeProjection(response, "mapping", "settings", "endpoint", "host", "port");
    }

    private void assertAsyncElasticsearchDocumentAudit() throws Exception {
        log.info("验证 Elasticsearch 原生响应、过滤、聚合与受限 DSL 均通过 Route 受控查询链路");
        String firstPageDsl = "{\"query\":{\"match_all\":{}},\"from\":0,\"size\":20}";
        ResponseEntity<String> firstPageOperation = successResponse(elasticsearchDocumentUri(firstPageDsl));
        JsonNode firstPage = objectMapper.readTree(firstPageOperation.getBody());
        assertEquals(21, firstPage.path("hits").path("total").asInt());
        assertEquals(20, firstPage.path("hits").path("hits").size());
        assertTrue(firstPage.has("took"));
        assertFalse(firstPage.has("page"));
        assertFalse(firstPage.has("items"));
        assertFalse(firstPage.has("hasMore"));

        String secondPageDsl = "{\"query\":{\"match_all\":{}},\"from\":20,\"size\":20}";
        JsonNode secondPage = success(elasticsearchDocumentUri(secondPageDsl));
        assertEquals(1, secondPage.path("hits").path("hits").size());

        String termDsl = "{\"query\":{\"term\":{\"message.keyword\":\"middleware-ops-fixture-7\"}},\"size\":20}";
        JsonNode exactMatch = success(elasticsearchDocumentUri(termDsl));
        assertEquals(1, exactMatch.path("hits").path("hits").size());
        assertEquals(7, exactMatch.path("hits").path("hits").get(0).path("_source").path("sequence").asInt());
        assertEquals("middleware-ops-fixture-7", exactMatch.path("hits").path("hits").get(0).path("_source")
                .path("message").asText());

        String emptyDsl = "{\"query\":{\"term\":{\"message.keyword\":\"middleware-ops-fixture-missing\"}},\"size\":20}";
        JsonNode empty = success(elasticsearchDocumentUri(emptyDsl));
        assertTrue(empty.path("hits").path("hits").isEmpty());

        String statsDsl = "{\"size\":0,\"aggs\":{\"sequence_stats\":{\"stats\":{\"field\":\"sequence\"}}}}";
        JsonNode stats = success(elasticsearchDocumentUri(statsDsl));
        assertTrue(stats.path("hits").path("hits").isEmpty());
        assertEquals(21, stats.path("aggregations").path("sequence_stats").path("count").asInt());
        assertEquals(1.0d, stats.path("aggregations").path("sequence_stats").path("min").asDouble());
        assertEquals(21.0d, stats.path("aggregations").path("sequence_stats").path("max").asDouble());
        assertEquals(231.0d, stats.path("aggregations").path("sequence_stats").path("sum").asDouble());
        assertEquals(11.0d, stats.path("aggregations").path("sequence_stats").path("avg").asDouble());

        String termsDsl = "{\"size\":0,\"aggs\":{\"by_message\":{\"terms\":{\"field\":\"message.keyword\",\"size\":30}}}}";
        JsonNode terms = success(elasticsearchDocumentUri(termsDsl));
        assertEquals(21, terms.path("aggregations").path("by_message").path("buckets").size());

        String queryWithAggregationDsl = "{\"query\":{\"range\":{\"sequence\":{\"gte\":10}}},\"size\":20,\"aggs\":{\"sequence_stats\":{\"stats\":{\"field\":\"sequence\"}}}}";
        JsonNode queryWithAggregation = success(elasticsearchDocumentUri(queryWithAggregationDsl));
        assertEquals(12, queryWithAggregation.path("hits").path("hits").size());
        assertEquals(12, queryWithAggregation.path("aggregations").path("sequence_stats").path("count").asInt());
        assertEquals(10.0d, queryWithAggregation.path("aggregations").path("sequence_stats").path("min").asDouble());
        assertEquals(21.0d, queryWithAggregation.path("aggregations").path("sequence_stats").path("max").asDouble());

        assertRejectedElasticsearchDsl("{\"scroll\":\"1m\"}");
        assertRejectedElasticsearchDsl("{\"pit\":{\"id\":\"fixture\"}}");
        assertRejectedElasticsearchDsl("{\"search_after\":[1]}");
        assertRejectedElasticsearchDsl("{\"runtime_mappings\":{\"derived\":{\"type\":\"keyword\"}}}");
        assertRejectedElasticsearchDsl("{\"script_fields\":{\"derived\":{\"script\":\"1\"}}}");
        assertRejectedElasticsearchDsl("{\"profile\":true}");
        assertRejectedElasticsearchDsl("{\"query\":{\"script\":{\"script\":\"1\"}}}");

        String requestId = requestId(firstPageOperation);
        JsonNode record = awaitAuditRecord("elasticsearch", requestId);
        assertEquals(requestId, record.path("id").asText());
        assertEquals("ELASTICSEARCH_DOCUMENT_QUERY", record.path("capability").asText());
        assertEquals("elasticsearch", record.path("middlewareType").asText());
        assertEquals("primary", record.path("datasourceKey").asText());
        assertEquals(200, record.path("httpStatus").asInt());
        assertNotEquals("test_index_a", record.path("elasticsearchIndex").asText());
        assertNotEquals(firstPageDsl, record.path("elasticsearchDsl").asText());
        assertSafeProjection(record, "url", "username", "password", "request", "response", "exception");
    }

    private URI elasticsearchDocumentUri(String dsl) {
        String encodedDsl = Base64.getUrlEncoder().withoutPadding().encodeToString(dsl.getBytes(StandardCharsets.UTF_8));
        return UriComponentsBuilder.fromHttpUrl(url("/elasticsearch/datasources/{datasourceKey}/documents"))
                .queryParam("index", "test_index_a").queryParam("dsl", encodedDsl).buildAndExpand("primary").encode()
                .toUri();
    }

    private void assertRejectedElasticsearchDsl(String dsl) throws Exception {
        assertError(elasticsearchDocumentUri(dsl), 400);
    }

    private void assertRedisDatasources() throws Exception {
        log.info("验证 Redis Route 六个固定数据源安全摘要");
        JsonNode response = success("/redis/datasources");
        Map<String, JsonNode> datasources = itemsBy(response.path("items"), "datasourceKey");
        assertEquals(REDIS_DATASOURCES, datasources.keySet());
        for (String datasourceKey : REDIS_DATASOURCES) {
            JsonNode datasource = datasources.get(datasourceKey);
            assertTrue(datasource.path("versionKnown").asBoolean());
            assertFalse(datasource.path("version").asText().isEmpty());
            assertEquals(REDIS_CLUSTER_DATASOURCES.contains(datasourceKey) ? "cluster" : "standalone",
                    datasource.path("deploymentMode").asText());
            JsonNode summary = success("/redis/datasources/" + datasourceKey + "/summary");
            assertEquals(datasource, summary);
        }
    }

    private void assertRedisKeyDiscovery() throws Exception {
        log.info("验证 Redis 字面量前缀 Key 发现的受限 standalone 与 Cluster 链路");
        ResponseEntity<String> standaloneOperation = successResponse(redisDiscoveryUri("redis7Standalone",
                REDIS_DISCOVERY_STANDALONE_PREFIX, 1));
        JsonNode standalone = objectMapper.readTree(standaloneOperation.getBody());
        assertEquals(1, standalone.path("limit").asInt());
        assertEquals(1, standalone.path("returned").asInt());
        assertTrue(standalone.path("truncated").asBoolean());
        assertFalse(standalone.path("traversalComplete").asBoolean());
        assertEquals("RESULT_LIMIT", standalone.path("stopReason").asText());
        assertTrue(standalone.path("items").get(0).asText().startsWith(REDIS_DISCOVERY_STANDALONE_PREFIX),
                "请先按 LOCAL_TEST_COMMANDS.md 准备固定 standalone discovery fixture");
        assertSafeProjection(standalone, "cursor", "nextCursor", "value", "topology", "endpoint", "host", "port", "slot");

        JsonNode standaloneAudit = awaitAuditRecord("redis", requestId(standaloneOperation));
        assertEquals("REDIS_KEY_DISCOVERY", standaloneAudit.path("capability").asText());
        assertEquals("redis7Standalone", standaloneAudit.path("datasourceKey").asText());
        assertEquals(MiddlewareOpsDigestHelper.sha256("key-discovery"), standaloneAudit.path("resourceDigest").asText());
        assertTrue(standaloneAudit.path("redisKey").isMissingNode() || standaloneAudit.path("redisKey").isNull());
        assertTrue(standaloneAudit.path("redisField").isMissingNode() || standaloneAudit.path("redisField").isNull());
        assertTrue(standaloneAudit.path("size").isMissingNode() || standaloneAudit.path("size").isNull());
        assertFalse(standaloneAudit.toString().contains(REDIS_DISCOVERY_STANDALONE_PREFIX));
        assertSafeProjection(standaloneAudit, "cursor", "nextCursor", "value", "topology", "endpoint", "host", "port", "slot");

        JsonNode cluster = success(redisDiscoveryUri("redis7Cluster", REDIS_DISCOVERY_CLUSTER_PREFIX, 100));
        assertEquals(100, cluster.path("limit").asInt());
        assertFalse(cluster.path("truncated").asBoolean());
        assertTrue(cluster.path("traversalComplete").asBoolean());
        assertEquals("COMPLETED", cluster.path("stopReason").asText());
        assertTrue(stringValues(cluster.path("items")).containsAll(REDIS_DISCOVERY_CLUSTER_KEYS),
                "请先按 LOCAL_TEST_COMMANDS.md 在至少两个 Redis Cluster master 准备固定 discovery fixture");
        assertSafeProjection(cluster, "cursor", "nextCursor", "value", "topology", "endpoint", "host", "port", "slot");
    }

    private void assertAsyncRedisKeyAuditMasking() throws Exception {
        log.info("验证 Redis 固定 Hash 元数据与 field 读取经 Search MASK 展示");
        URI metadataUri = UriComponentsBuilder.fromHttpUrl(url("/redis/datasources/{datasourceKey}/keys/metadata"))
                .buildAndExpand("redis7Standalone").encode().toUri();
        metadataUri = UriComponentsBuilder.fromUri(metadataUri).queryParam("key", REDIS_HASH_FIXTURE_KEY)
                .build().encode().toUri();
        ResponseEntity<String> metadataOperation = successResponse(metadataUri);
        JsonNode metadata = objectMapper.readTree(metadataOperation.getBody());
        log.info("Redis Hash 元数据：key={}，exists={}，type={}", REDIS_HASH_FIXTURE_KEY,
                metadata.path("exists").asBoolean(), metadata.path("dataType").asText());
        assertTrue(metadata.path("exists").asBoolean(), "请先按 LOCAL_TEST_COMMANDS.md 准备固定 Redis Hash fixture");
        assertEquals("hash", metadata.path("dataType").asText());
        assertEquals("PERSISTENT", metadata.path("ttlState").asText());
        assertSafeProjection(metadata, "key", "field", "value", "entry");

        JsonNode metadataRecord = awaitAuditRecord("redis", requestId(metadataOperation));
        assertEquals("REDIS_KEY_METADATA", metadataRecord.path("capability").asText());
        assertEquals("redis7Standalone", metadataRecord.path("datasourceKey").asText());
        assertFalse(metadataRecord.path("redisKey").asText().isEmpty());
        assertNotEquals(REDIS_HASH_FIXTURE_KEY, metadataRecord.path("redisKey").asText());
        assertTrue(metadataRecord.path("redisField").isMissingNode() || metadataRecord.path("redisField").isNull());
        assertSafeProjection(metadataRecord, "url", "username", "password", "request", "response", "exception",
                "value", "hashEntries", "stringValue", "listEntries", "setMembers", "zSetEntries", "streamEntries");

        URI valueUri = UriComponentsBuilder.fromHttpUrl(url("/redis/datasources/{datasourceKey}/keys/value"))
                .buildAndExpand("redis7Standalone").encode().toUri();
        valueUri = UriComponentsBuilder.fromUri(valueUri).queryParam("key", REDIS_HASH_FIXTURE_KEY)
                .queryParam("field", REDIS_HASH_FIXTURE_FIELD).queryParam("offset", 0).queryParam("size", 1)
                .build().encode().toUri();
        ResponseEntity<String> valueOperation = successResponse(valueUri);
        JsonNode value = objectMapper.readTree(valueOperation.getBody());
        log.info("Redis Hash field 读取：field={}，entryCount={}", REDIS_HASH_FIXTURE_FIELD,
                value.path("hashEntries").size());
        assertEquals("hash", value.path("dataType").asText());
        assertEquals(1, value.path("hashEntries").size());
        assertEquals(REDIS_HASH_FIXTURE_FIELD, value.path("hashEntries").get(0).path("field").asText());
        assertEquals(REDIS_HASH_FIXTURE_VALUE, value.path("hashEntries").get(0).path("value").asText());
        assertSafeProjection(value, "key", "offset", "size", "cursor", "nextCursor");

        JsonNode valueRecord = awaitAuditRecord("redis", requestId(valueOperation));
        assertEquals("REDIS_KEY_READ", valueRecord.path("capability").asText());
        assertEquals("redis7Standalone", valueRecord.path("datasourceKey").asText());
        assertFalse(valueRecord.path("redisKey").asText().isEmpty());
        assertNotEquals(REDIS_HASH_FIXTURE_KEY, valueRecord.path("redisKey").asText());
        assertFalse(valueRecord.path("redisField").asText().isEmpty());
        assertNotEquals(REDIS_HASH_FIXTURE_FIELD, valueRecord.path("redisField").asText());
        assertEquals(0L, valueRecord.path("offset").asLong());
        assertEquals(1, valueRecord.path("size").asInt());
        assertSafeProjection(valueRecord, "url", "username", "password", "request", "response", "exception",
                "value", "hashEntries", "stringValue", "listEntries", "setMembers", "zSetEntries", "streamEntries");
    }

    private void assertKafkaDatasources() throws Exception {
        log.info("验证 Kafka Route 七个逻辑数据源与固定本地首窗口");
        JsonNode response = success("/kafka/datasources");
        Map<String, JsonNode> datasources = itemsBy(response.path("items"), "datasourceKey");
        assertEquals(KAFKA_DATASOURCES, datasources.keySet());
        for (String datasourceKey : KAFKA_DATASOURCES) {
            JsonNode datasource = datasources.get(datasourceKey);
            assertEquals("tx37".equals(datasourceKey) ? "WARN" : "SUCCESS",
                    datasource.path("diagnosticStatus").asText());
            if ("tx37".equals(datasourceKey)) {
                assertEquals("已配置事务生产者，但 broker Feature API 未确认事务能力",
                        datasource.path("diagnosticReason").asText());
            } else {
                assertTrue(datasource.path("diagnosticReason").isNull());
            }
            assertTrue(datasource.path("nodeCount").asInt() > 0);
            assertTrue(datasource.path("controllerVisible").asBoolean());
            if ("cluster".equals(datasourceKey)) {
                assertTrue(datasource.path("nodeCount").asInt() >= 3);
            }
            assertSafeProjection(datasource, "bootstrapServers", "security", "password", "endpoint");
            assertKafkaTopicWindow(datasourceKey);
            assertKafkaConsumerGroupWindow(datasourceKey);
        }
    }

    private void assertAsyncKafkaDiagnosticAudits() throws Exception {
        log.info("验证 Kafka Topic 运行态与消费组积压进入审计并经 Search MASK 展示");
        String topic = "middleware-ops-fixture";
        String groupId = "middleware-ops-fixture-group";
        ResponseEntity<String> topicOperation = successResponse(UriComponentsBuilder
                .fromHttpUrl(url("/kafka/datasources/{datasourceKey}/topics/runtime"))
                .queryParam("topic", topic).buildAndExpand("v37").encode().toUri());
        JsonNode topicResponse = objectMapper.readTree(topicOperation.getBody());
        assertEquals(topic, topicResponse.path("topic").asText());
        assertTrue(topicResponse.path("truncated").isBoolean());
        assertTrue(topicResponse.path("partitions").isArray());
        assertFalse(topicResponse.path("partitions").isEmpty());
        assertTrue(topicResponse.path("partitions").size() <= 100);
        assertFalse(topicResponse.path("truncated").asBoolean());
        assertSafeProjection(topicResponse, "payload", "header", "endpoint", "bootstrapServers");
        JsonNode topicRecord = awaitAuditRecord("kafka", requestId(topicOperation));
        assertEquals("KAFKA_TOPIC_RUNTIME", topicRecord.path("capability").asText());
        assertEquals("v37", topicRecord.path("datasourceKey").asText());
        assertFalse(topicRecord.path("kafkaTopic").asText().isEmpty());
        assertNotEquals(topic, topicRecord.path("kafkaTopic").asText());
        assertSafeProjection(topicRecord, "url", "username", "password", "request", "response", "exception",
                "payload", "header");

        ResponseEntity<String> lagOperation = successResponse(UriComponentsBuilder
                .fromHttpUrl(url("/kafka/datasources/{datasourceKey}/consumer-groups/lag"))
                .queryParam("groupId", groupId).queryParam("size", 50).buildAndExpand("v37").encode().toUri());
        JsonNode lagResponse = objectMapper.readTree(lagOperation.getBody());
        assertTrue(lagResponse.path("items").isArray());
        assertFalse(lagResponse.path("items").isEmpty());
        for (JsonNode item : lagResponse.path("items")) {
            assertTrue(item.path("topic").asText().length() > 0);
            assertTrue(item.path("partition").isInt());
            assertTrue(item.path("committedOffset").isNumber());
            assertTrue(item.path("endOffset").isNumber());
            assertTrue(item.path("lag").isNumber());
            assertSafeProjection(item, "payload", "header", "endpoint");
        }
        JsonNode lagRecord = awaitAuditRecord("kafka", requestId(lagOperation));
        assertEquals("KAFKA_CONSUMER_GROUP_LAG_LIST", lagRecord.path("capability").asText());
        assertEquals("v37", lagRecord.path("datasourceKey").asText());
        assertFalse(lagRecord.path("kafkaGroupId").asText().isEmpty());
        assertNotEquals(groupId, lagRecord.path("kafkaGroupId").asText());
        assertEquals(50, lagRecord.path("size").asInt());
        assertSafeProjection(lagRecord, "url", "username", "password", "request", "response", "exception",
                "payload", "header");
    }

    private void assertAsyncMysqlStatusAudit() throws Exception {
        log.info("验证 MySQL status 显式请求进入审计且不泄漏连接信息");
        ResponseEntity<String> operation = successResponse("/mysql/datasources/mysql84-ops/status");
        JsonNode status = objectMapper.readTree(operation.getBody());
        assertEquals("mysql84-ops", status.path("datasourceKey").asText());
        assertTrue(status.path("connected").asBoolean());
        assertFalse(status.path("database").asText().isEmpty());
        assertFalse(status.path("serverVersion").asText().isEmpty());
        JsonNode record = awaitAuditRecord("mysql", requestId(operation));
        assertEquals("MYSQL_DATASOURCE_STATUS", record.path("capability").asText());
        assertEquals("mysql", record.path("middlewareType").asText());
        assertEquals("mysql84-ops", record.path("datasourceKey").asText());
        assertEquals(200, record.path("httpStatus").asInt());
        assertSafeProjection(record, "url", "username", "password", "request", "response", "exception",
                "database", "serverVersion", "host", "port", "jdbcUrl");
    }

    private void assertExplicitAuditRange(JsonNode mysqlAuditRecord) throws Exception {
        log.info("验证真实 MySQL 审计记录经动态 UTC 月范围通配读取并保持工作区隔离");
        String requestId = mysqlAuditRecord.path("id").asText();
        assertFalse(requestId.isEmpty());
        LocalDate occurredDate = Instant.parse(mysqlAuditRecord.path("occurredAt").asText())
                .atZone(ZoneOffset.UTC).toLocalDate();
        LocalDateTime monthStart = occurredDate.withDayOfMonth(1).atStartOfDay();
        String from = AUDIT_TIME_FORMATTER.format(monthStart);
        String to = AUDIT_TIME_FORMATTER.format(monthStart.plusMonths(1));

        JsonNode record = findAuditRecord("mysql", requestId, from, to);
        assertNotNull(record, "真实 MySQL 审计记录必须可经动态 UTC 月范围和日索引通配查询");
        assertEquals(requestId, record.path("id").asText());
        assertEquals("mysql", record.path("middlewareType").asText());
        assertEquals("MYSQL_SELECT", record.path("capability").asText());
        assertFalse(record.path("mysqlSql").asText().isEmpty());
        assertSafeProjection(record, "url", "username", "password", "request", "response", "exception");

        assertNull(findAuditRecord("redis", requestId, from, to),
                "MySQL 审计记录不得穿透 Redis 工作区类型过滤");
    }

    private void assertAsyncAuditWriteAndRead() throws Exception {
        log.info("验证运维请求异步写入当天审计索引，并可通过日索引通配查询");
        ResponseEntity<String> operation = restTemplate.withBasicAuth("ops-user", ldapUserPassword)
                .getForEntity(url("/redis/datasources/redis7Standalone/summary"), String.class);
        assertEquals(200, operation.getStatusCodeValue(), operation.getBody());
        String requestId = operation.getHeaders().getFirst("X-Request-Id");
        assertNotNull(requestId);

        JsonNode record = awaitAuditRecord("redis", requestId);
        assertEquals(requestId, record.path("id").asText());
        assertEquals("ops-user", record.path("subject").asText());
        assertEquals("REDIS_SUMMARY", record.path("capability").asText());
        assertEquals("redis", record.path("middlewareType").asText());
        assertEquals("redis7Standalone", record.path("datasourceKey").asText());
        assertEquals(200, record.path("httpStatus").asInt());
        assertTrue(record.path("durationMillis").asLong() >= 0L);
        assertSafeProjection(record, "url", "username", "password", "request", "response", "exception");
    }

    private JsonNode assertAsyncMysqlAuditMasking() throws Exception {
        log.info("验证 MySQL 受控查询完整入库后经 Search MASK 展示");
        String sql = "SELECT cluster_id, database_name FROM test_route_marker";
        ResponseEntity<String> operation = restTemplate.withBasicAuth("ops-user", ldapUserPassword)
                .getForEntity(mysqlSelectUri("mysql84-ops", sql, 1), String.class);
        assertEquals(200, operation.getStatusCodeValue(), operation.getBody());
        String requestId = operation.getHeaders().getFirst("X-Request-Id");
        assertNotNull(requestId);

        JsonNode record = awaitAuditRecord("mysql", requestId);
        assertEquals(requestId, record.path("id").asText());
        assertEquals("MYSQL_SELECT", record.path("capability").asText());
        assertEquals("mysql", record.path("middlewareType").asText());
        assertEquals("mysql84-ops", record.path("datasourceKey").asText());
        assertEquals(1, record.path("size").asInt());
        assertFalse(record.path("mysqlSql").asText().isEmpty());
        assertNotEquals(sql, record.path("mysqlSql").asText());
        assertTrue(record.path("mysqlSql").asText().startsWith("SELECT clust"));
        assertTrue(record.path("mysqlSql").asText().endsWith("e_marker"));
        assertSafeProjection(record, "url", "username", "password", "request", "response", "exception");
        return record;
    }

    private void assertNoAuditRecords(String middlewareType, Collection<String> requestIds) throws Exception {
        log.info("验证 {} 自动概览请求均不写审计", middlewareType);
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            JsonNode record = findAuditRecordOnce(middlewareType, requestIds, null, null);
            assertNull(record, "自动概览请求不得写入审计，requestId="
                    + (record == null ? "" : record.path("id").asText()));
            Thread.sleep(100L);
        }
    }

    private JsonNode awaitAuditRecord(String middlewareType, String requestId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            JsonNode record = findAuditRecordOnce(middlewareType, Collections.singleton(requestId), null, null);
            if (record != null) {
                return record;
            }
            Thread.sleep(100L);
        }
        fail("异步审计记录未在限定时间内经日索引通配查询可见，requestId=" + requestId);
        return null;
    }

    private JsonNode findAuditRecord(String middlewareType, String requestId, String from, String to) throws Exception {
        return findAuditRecordOnce(middlewareType, Collections.singleton(requestId), from, to);
    }

    private JsonNode findAuditRecordOnce(String middlewareType, Collection<String> requestIds, String from, String to)
            throws Exception {
        for (int page = 1; ; page++) {
            JsonNode response = success(auditRecordsUri(middlewareType, page, from, to));
            assertEquals(page, response.path("page").asInt());
            assertEquals(auditPageSize(), response.path("size").asInt());
            assertAuditRange(response, from, to);
            long total = response.path("total").asLong();
            assertTrue(total >= 0L);
            assertTrue(total <= maximumInspectableAuditRecords(),
                    "审计记录超出公开 offset 查询可验证窗口，middlewareType=" + middlewareType + "，total=" + total);
            for (JsonNode item : response.path("items")) {
                if (requestIds.contains(item.path("id").asText())) {
                    return item;
                }
            }
            if ((long) page * auditPageSize() >= total) {
                return null;
            }
        }
    }

    private int auditPageSize() {
        return Math.min(properties.getQuery().getMaxSize(), properties.getAudit().getMaxOffset());
    }

    private long maximumInspectableAuditRecords() {
        return (long) (properties.getAudit().getMaxOffset() / auditPageSize()) * auditPageSize();
    }

    private URI auditRecordsUri(String middlewareType, int page, String from, String to) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(url("/audit/" + middlewareType + "/records"))
                .queryParam("page", page).queryParam("size", auditPageSize());
        if (from != null) {
            builder.queryParam("from", from).queryParam("to", to);
        }
        return builder.build().encode().toUri();
    }

    private void assertAuditRange(JsonNode response, String from, String to) {
        if (from == null) {
            assertFalse(response.path("from").asText().isEmpty());
            assertFalse(response.path("to").asText().isEmpty());
            return;
        }
        assertEquals(from, response.path("from").asText());
        assertEquals(to, response.path("to").asText());
    }

    private void assertMysqlDatasources() throws Exception {
        log.info("验证 MySQL Route 四个固定只读数据源的目录、状态与受控查询");
        Map<String, JsonNode> catalog = itemsBy(success("/mysql/catalog").path("items"), "datasourceKey");
        assertEquals(MYSQL_DATASOURCES, catalog.keySet());
        for (String datasourceKey : MYSQL_DATASOURCES) {
            assertEquals("mysql", catalog.get(datasourceKey).path("middlewareType").asText());
            assertFalse(catalog.get(datasourceKey).path("clusterTag").asText().isEmpty());
            assertSafeProjection(catalog.get(datasourceKey), "url", "username", "password", "database", "target");
        }

        assertMysqlTarget("mysql57-ops", "mysql57", "test_ops", "5.7.");
        assertMysqlTarget("mysql57-audit", "mysql57", "test_audit", "5.7.");
        assertMysqlTarget("mysql84-ops", "mysql84", "test_ops", "8.4.");
        assertMysqlTarget("mysql84-audit", "mysql84", "test_audit", "8.4.");
    }

    private void assertMysqlTarget(String datasourceKey, String expectedCluster, String expectedDatabase,
                                   String expectedVersionPrefix) throws Exception {
        JsonNode status = success("/mysql/datasources/" + datasourceKey + "/status");
        assertEquals(datasourceKey, status.path("datasourceKey").asText());
        assertEquals(expectedDatabase, status.path("database").asText());
        assertTrue(status.path("connected").asBoolean());
        assertFalse(status.path("serverVersion").asText().isEmpty());
        assertTrue(status.path("serverVersion").asText().startsWith(expectedVersionPrefix));
        assertTrue(status.path("durationMillis").asLong() >= 0L);
        assertSafeProjection(status, "url", "username", "password", "target", "host", "port");

        JsonNode select = success(mysqlSelectUri(datasourceKey,
                "SELECT cluster_id, database_name FROM test_route_marker", 1));
        assertEquals(Arrays.asList("cluster_id", "database_name"), stringValues(select.path("columns")));
        assertEquals(1, select.path("rows").size());
        assertEquals(expectedCluster, select.path("rows").get(0).get(0).asText());
        assertEquals(expectedDatabase, select.path("rows").get(0).get(1).asText());
        assertFalse(select.path("truncated").asBoolean());
        assertSafeProjection(select, "url", "username", "password", "target", "host", "port");
    }

    private List<String> stringValues(JsonNode values) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            result.add(value.asText());
        }
        return result;
    }

    private List<String> stringValues(JsonNode values, String field) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            result.add(value.path(field).asText());
        }
        return result;
    }

    private void assertKafkaTopicWindow(String datasourceKey) throws Exception {
        JsonNode items = success("/kafka/datasources/" + datasourceKey + "/topics?size=100").path("items");
        assertFalse(items.isEmpty(), "请先按 LOCAL_TEST_COMMANDS.md 准备固定 Kafka topic");
        String previous = null;
        for (JsonNode item : items) {
            String name = item.path("name").asText();
            assertFalse(name.isEmpty());
            if (previous != null) {
                assertTrue(previous.compareTo(name) < 0, "Kafka topic 首窗口必须稳定排序");
            }
            previous = name;
            assertSafeProjection(item, "payload", "header", "endpoint");
        }
    }

    private void assertKafkaConsumerGroupWindow(String datasourceKey) throws Exception {
        JsonNode items = success("/kafka/datasources/" + datasourceKey + "/consumer-groups?size=100").path("items");
        assertFalse(items.isEmpty(), "请先按 LOCAL_TEST_COMMANDS.md 准备固定 Kafka 消费组");
        String previous = null;
        for (JsonNode item : items) {
            String groupId = item.path("groupId").asText();
            assertFalse(groupId.isEmpty());
            assertTrue("consumer".equals(item.path("protocolType").asText())
                    || "simple".equals(item.path("protocolType").asText()));
            if (previous != null) {
                assertTrue(previous.compareTo(groupId) < 0, "Kafka 消费组首窗口必须稳定排序");
            }
            previous = groupId;
            assertSafeProjection(item, "member", "host", "client", "endpoint", "payload", "header");
        }
    }

    private URI redisDiscoveryUri(String datasourceKey, String prefix, int size) {
        return UriComponentsBuilder.fromHttpUrl(url("/redis/datasources/{datasourceKey}/keys/discovery"))
                .queryParam("prefix", prefix).queryParam("size", size).buildAndExpand(datasourceKey).encode().toUri();
    }

    private URI mysqlSelectUri(String datasourceKey, String sql, int size) {
        return UriComponentsBuilder.fromHttpUrl(url("/mysql/datasources/{datasourceKey}/select"))
                .queryParam("sql", sql).queryParam("size", size).buildAndExpand(datasourceKey).encode().toUri();
    }

    private void assertError(String path, int expectedStatus) throws Exception {
        ResponseEntity<String> response = restTemplate.withBasicAuth("ops-user", ldapUserPassword)
                .getForEntity(url(path), String.class);
        assertEquals(expectedStatus, response.getStatusCodeValue(), response.getBody());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertNotNull(requestId);
        assertNotNull(response.getBody());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertFalse(body.path("message").asText().isEmpty());
        assertFalse(body.path("timestamp").asText().isEmpty());
        assertEquals(requestId, body.path("requestId").asText());
    }

    private void assertError(URI uri, int expectedStatus) throws Exception {
        ResponseEntity<String> response = restTemplate.withBasicAuth("ops-user", ldapUserPassword)
                .getForEntity(uri, String.class);
        assertEquals(expectedStatus, response.getStatusCodeValue(), response.getBody());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertNotNull(requestId);
        assertNotNull(response.getBody());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertFalse(body.path("message").asText().isEmpty());
        assertFalse(body.path("timestamp").asText().isEmpty());
        assertEquals(requestId, body.path("requestId").asText());
    }

    private JsonNode success(String path) throws Exception {
        return success(URI.create(url(path)));
    }

    private JsonNode success(URI uri) throws Exception {
        return objectMapper.readTree(successResponse(uri).getBody());
    }

    private ResponseEntity<String> successResponse(String path) {
        return successResponse(URI.create(url(path)));
    }

    private ResponseEntity<String> successResponse(URI uri) {
        ResponseEntity<String> response = restTemplate.withBasicAuth("ops-user", ldapUserPassword)
                .getForEntity(uri, String.class);
        assertEquals(200, response.getStatusCodeValue(), response.getBody());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        requestId(response);
        assertNotNull(response.getBody());
        return response;
    }

    private String requestId(ResponseEntity<String> response) {
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertNotNull(requestId);
        return requestId;
    }

    private Map<String, JsonNode> itemsBy(JsonNode items, String field) {
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode item : items) {
            assertNull(result.put(item.path(field).asText(), item), "安全投影列表不得存在重复 datasource key");
        }
        return result;
    }

    private void assertSafeProjection(JsonNode node, String... forbiddenFields) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        for (String field : forbiddenFields) {
            assertFalse(fields.contains(field), "安全投影不得包含字段：" + field);
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + "/api/v1/middleware-ops" + path;
    }

}

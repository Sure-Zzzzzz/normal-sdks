package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditEvent;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditPublisher;
import io.github.surezzzzzz.sdk.ops.middleware.authentication.MiddlewareOpsIdentity;
import io.github.surezzzzzz.sdk.ops.middleware.authentication.MiddlewareOpsIdentityResolver;
import io.github.surezzzzzz.sdk.ops.middleware.authorization.MiddlewareOpsAuthorizationPolicy;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceCatalogRequest;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceTagResolver;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.ElasticsearchDocumentQueryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.ElasticsearchFieldCapabilitiesRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.ElasticsearchIndexListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.ElasticsearchSummaryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.kafka.KafkaDatasourceListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.mysql.*;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisDatasourceListRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.RedisSummaryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.service.*;
import io.github.surezzzzzz.sdk.ops.middleware.support.MiddlewareOpsConcurrencyGuard;
import io.github.surezzzzzz.sdk.ops.middleware.support.MiddlewareOpsDigestHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 默认运维编排器授权与类型边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultMiddlewareOpsServerEngineTest {

    @Test
    void shouldRejectUnauthenticatedRequestBeforeExecutorInvocation() {
        MiddlewareOpsServerEngine engine = engine(() -> null, context -> true);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> engine.execute(request(), String.class));
        log.info("未认证执行结果：status={}，message={}", exception.getStatus(), exception.getMessage());

        assertEquals(401, exception.getStatus().value());
        assertEquals("需要先完成身份认证", exception.getMessage());
    }

    @Test
    void shouldRejectAuthenticatedButDeniedRequest() {
        MiddlewareOpsServerEngine engine = engine(identity(), context -> false);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> engine.execute(request(), String.class));
        log.info("授权拒绝结果：status={}，message={}", exception.getStatus(), exception.getMessage());

        assertEquals(403, exception.getStatus().value());
        assertEquals("当前身份无权执行该运维查询", exception.getMessage());
    }

    @Test
    void shouldExecuteOnlyMatchingRequestAndResponseTypes() {
        MiddlewareOpsServerEngine engine = engine(identity(), context -> true);

        String response = engine.execute(request(), String.class);
        log.info("类型匹配执行结果：response={}", response);
        assertEquals("safe", response);
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> engine.execute(request(), Integer.class));
        log.info("类型不匹配结果：status={}，message={}", exception.getStatus(), exception.getMessage());
        assertEquals(400, exception.getStatus().value());
        assertEquals("运维查询响应类型不匹配", exception.getMessage());
    }

    @Test
    void shouldPublishClusterTagFromStartupSnapshotOnly() {
        AtomicReference<MiddlewareOpsAuditEvent> event = new AtomicReference<>();
        DatasourceTagResolver tagResolver = (middlewareType, datasourceKey) -> middlewareType == MiddlewareType.REDIS
                && "default".equals(datasourceKey) ? "蓝色缓存集群" : null;
        MiddlewareOpsServerEngine engine = engine(identity(), context -> true, event::set, tagResolver);

        engine.execute(request(), String.class);
        log.info("审计标签：clusterTag={}", event.get().getClusterTag());

        assertEquals("蓝色缓存集群", event.get().getClusterTag());
        assertEquals("default", event.get().getDatasourceKey());
        assertEquals(MiddlewareType.REDIS, event.get().getMiddlewareType());
    }

    @Test
    void shouldKeepClusterTagNullWhenDatasourceIsNotInStartupSnapshot() {
        AtomicReference<MiddlewareOpsAuditEvent> event = new AtomicReference<>();
        MiddlewareOpsServerEngine engine = engine(identity(), context -> true, event::set,
                (middlewareType, datasourceKey) -> null);

        engine.execute(request(), String.class);
        log.info("未知数据源审计标签：clusterTag={}", event.get().getClusterTag());

        assertNull(event.get().getClusterTag());
    }

    @Test
    void shouldSkipAuditForAutomaticOverviewLoads() {
        AtomicReference<MiddlewareOpsAuditEvent> event = new AtomicReference<>();
        MiddlewareOpsServerEngine engine = genericEngine(event::set);

        for (MiddlewareType middlewareType : MiddlewareType.values()) {
            assertEquals("safe", engine.execute(new DatasourceCatalogRequest(middlewareType), String.class));
            assertNull(event.get());
        }
        assertEquals("safe", engine.execute(ElasticsearchSummaryRequest.builder().datasourceKey("primary").build(),
                String.class));
        assertNull(event.get());
        assertEquals("safe", engine.execute(ElasticsearchIndexListRequest.builder().datasourceKey("primary").build(),
                String.class));
        assertNull(event.get());
        assertEquals("safe", engine.execute(ElasticsearchFieldCapabilitiesRequest.builder().datasourceKey("primary")
                .index("orders").build(), String.class));
        assertNull(event.get());
        assertEquals("safe", engine.execute(RedisDatasourceListRequest.forOverview(), String.class));
        assertNull(event.get());
        assertEquals("safe", engine.execute(KafkaDatasourceListRequest.forOverview(), String.class));
        assertNull(event.get());
        assertEquals("safe", engine.execute(MysqlDatasourceStatusRequest.forOverview("orders-reader"), String.class));
        assertNull(event.get());

        assertThrows(MiddlewareOpsException.class, () -> engine.execute(MysqlDatasourceStatusRequest.forOverview(null),
                String.class));
        assertNull(event.get());
    }

    @Test
    void shouldKeepAuditForManualDatasourceOperations() {
        AtomicReference<MiddlewareOpsAuditEvent> event = new AtomicReference<>();
        MiddlewareOpsServerEngine engine = genericEngine(event::set);

        assertEquals("safe", engine.execute(new RedisDatasourceListRequest(), String.class));
        assertEquals(MiddlewareOpsCapability.REDIS_DATASOURCE_LIST, event.get().getCapability());
        event.set(null);
        assertEquals("safe", engine.execute(new KafkaDatasourceListRequest(), String.class));
        assertEquals(MiddlewareOpsCapability.KAFKA_DATASOURCE_LIST, event.get().getCapability());
        event.set(null);
        assertEquals("safe", engine.execute(MysqlDatasourceStatusRequest.builder().datasourceKey("orders-reader").build(),
                String.class));
        assertEquals(MiddlewareOpsCapability.MYSQL_DATASOURCE_STATUS, event.get().getCapability());
    }

    @Test
    void shouldKeepAuditForElasticsearchDocumentQuery() {
        AtomicReference<MiddlewareOpsAuditEvent> event = new AtomicReference<>();
        MiddlewareOpsServerEngine engine = genericEngine(event::set);
        ElasticsearchDocumentQueryRequest request = ElasticsearchDocumentQueryRequest.builder().datasourceKey("primary")
                .index("orders-2026.08.07").dsl("{\"query\":{\"match_all\":{}},\"from\":0,\"size\":20}").build();

        assertEquals("safe", engine.execute(request, String.class));

        assertNotNull(event.get());
        assertEquals(MiddlewareOpsCapability.ELASTICSEARCH_DOCUMENT_QUERY, event.get().getCapability());
        assertEquals(MiddlewareType.ELASTICSEARCH, event.get().getMiddlewareType());
        assertEquals("primary", event.get().getDatasourceKey());
    }

    @Test
    void shouldAuditMysqlStatusWithoutPhysicalTargetContent() {
        AtomicReference<MiddlewareOpsAuditEvent> event = new AtomicReference<>();
        MysqlOperationsViewAdapter adapter = mock(MysqlOperationsViewAdapter.class);
        MysqlDatasourceStatusRequest request = MysqlDatasourceStatusRequest.builder().datasourceKey("orders-reader")
                .build();
        MysqlDatasourceStatusResponse response = MysqlDatasourceStatusResponse.builder().datasourceKey("orders-reader")
                .database("orders").connected(true).readOnly(true).superReadOnly(true).build();
        when(adapter.getStatus("orders-reader")).thenReturn(response);
        MiddlewareOpsExecutorRegistry registry = new DefaultMiddlewareOpsExecutorRegistry(
                Collections.<MiddlewareOpsExecutor<?, ?>>singletonList(new MysqlDatasourceStatusExecutor(adapter)),
                Collections.<MiddlewareOpsRequestValidator<?>>singletonList(
                        new DefaultMiddlewareOpsRequestValidator<MysqlDatasourceStatusRequest>(
                                MysqlDatasourceStatusRequest.class) {
                            @Override
                            public void validate(MysqlDatasourceStatusRequest value) {
                                requireDatasource(value.getDatasourceKey());
                            }
                        }));
        MiddlewareOpsServerEngine engine = new DefaultMiddlewareOpsServerEngine(identity(), context -> true, registry,
                new MiddlewareOpsConcurrencyGuard(2, 1), event::set,
                (middlewareType, datasourceKey) -> "orders-cluster");

        assertEquals(response, engine.execute(request, MysqlDatasourceStatusResponse.class));
        log.info("MySQL 状态审计：capability={}，resourceDigest={}", event.get().getCapability(),
                event.get().getResourceDigest());

        assertEquals(MiddlewareOpsCapability.MYSQL_DATASOURCE_STATUS, event.get().getCapability());
        assertEquals(MiddlewareType.MYSQL, event.get().getMiddlewareType());
        assertEquals("orders-reader", event.get().getDatasourceKey());
        assertEquals("orders-cluster", event.get().getClusterTag());
        assertEquals(MiddlewareOpsDigestHelper.sha256("datasource-status"), event.get().getResourceDigest());
        assertEquals(200, event.get().getStatus());
    }

    @Test
    void shouldAuditValidatedMysqlSelectWithSqlContext() {
        AtomicReference<MiddlewareOpsAuditEvent> event = new AtomicReference<>();
        MysqlOperationsViewAdapter adapter = mock(MysqlOperationsViewAdapter.class);
        MysqlSelectRequest request = MysqlSelectRequest.builder().datasourceKey("orders-reader")
                .sql("SELECT id FROM orders WHERE id = 7").size(1).build();
        MysqlSelectResponse response = MysqlSelectResponse.builder().columns(Collections.singletonList("id"))
                .rows(Collections.singletonList(Collections.singletonList("7"))).truncated(false).build();
        when(adapter.select(request)).thenReturn(response);
        MiddlewareOpsExecutorRegistry registry = new DefaultMiddlewareOpsExecutorRegistry(
                Collections.<MiddlewareOpsExecutor<?, ?>>singletonList(new MysqlSelectExecutor(adapter)),
                Collections.<MiddlewareOpsRequestValidator<?>>singletonList(
                        new DefaultMiddlewareOpsRequestValidator<MysqlSelectRequest>(MysqlSelectRequest.class) {
                            @Override
                            public void validate(MysqlSelectRequest value) {
                                requireDatasource(value.getDatasourceKey());
                            }
                        }));
        MiddlewareOpsServerEngine engine = new DefaultMiddlewareOpsServerEngine(identity(), context -> true, registry,
                new MiddlewareOpsConcurrencyGuard(2, 1), event::set,
                (middlewareType, datasourceKey) -> "orders-cluster");

        assertEquals(response, engine.execute(request, MysqlSelectResponse.class));
        log.info("MySQL SELECT 审计：capability={}，resourceDigest={}", event.get().getCapability(),
                event.get().getResourceDigest());

        assertEquals(MiddlewareOpsCapability.MYSQL_SELECT, event.get().getCapability());
        assertEquals(MiddlewareType.MYSQL, event.get().getMiddlewareType());
        assertEquals("orders-reader", event.get().getDatasourceKey());
        assertEquals("orders-cluster", event.get().getClusterTag());
        assertEquals(MiddlewareOpsDigestHelper.sha256("controlled-select"), event.get().getResourceDigest());
        assertNotNull(event.get().getContext());
        assertEquals("SELECT id FROM orders WHERE id = 7", event.get().getContext().getMysqlSql());
        assertEquals(1, event.get().getContext().getSize());
        assertNull(event.get().getContext().getElasticsearchDsl());
        assertNull(event.get().getContext().getRedisKey());
        assertEquals(200, event.get().getStatus());
    }

    @Test
    void shouldCaptureRejectedMysqlSqlContextWithoutExceptionDetail() {
        AtomicReference<MiddlewareOpsAuditEvent> event = new AtomicReference<>();
        MysqlSelectRequest request = MysqlSelectRequest.builder().datasourceKey("orders-reader")
                .sql("SELECT secret FROM orders").size(1).build();
        MiddlewareOpsExecutorRegistry registry = new DefaultMiddlewareOpsExecutorRegistry(
                Collections.<MiddlewareOpsExecutor<?, ?>>singletonList(new MiddlewareOpsExecutor<MysqlSelectRequest, String>() {
                    @Override
                    public Class<MysqlSelectRequest> getRequestType() {
                        return MysqlSelectRequest.class;
                    }

                    @Override
                    public String execute(MysqlSelectRequest value) {
                        return "unreachable";
                    }
                }), Collections.<MiddlewareOpsRequestValidator<?>>singletonList(
                new DefaultMiddlewareOpsRequestValidator<MysqlSelectRequest>(MysqlSelectRequest.class) {
                    @Override
                    public void validate(MysqlSelectRequest value) {
                        throw new MiddlewareOpsException(org.springframework.http.HttpStatus.BAD_REQUEST, "SQL 超出允许范围");
                    }
                }));
        MiddlewareOpsServerEngine engine = new DefaultMiddlewareOpsServerEngine(identity(), context -> true, registry,
                new MiddlewareOpsConcurrencyGuard(2, 1), event::set, null);

        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> engine.execute(request, String.class));

        assertEquals(400, exception.getStatus().value());
        assertNotNull(event.get());
        assertEquals(MiddlewareOpsCapability.MYSQL_SELECT, event.get().getCapability());
        assertNotNull(event.get().getContext());
        assertEquals("SELECT secret FROM orders", event.get().getContext().getMysqlSql());
        assertEquals(1, event.get().getContext().getSize());
        assertNull(event.get().getContext().getElasticsearchDsl());
        assertNull(event.get().getContext().getRedisKey());
        assertEquals(400, event.get().getStatus());
    }

    private MiddlewareOpsServerEngine engine(MiddlewareOpsIdentityResolver resolver,
                                             MiddlewareOpsAuthorizationPolicy policy) {
        return engine(resolver, policy, null, null);
    }

    private MiddlewareOpsServerEngine engine(MiddlewareOpsIdentityResolver resolver,
                                             MiddlewareOpsAuthorizationPolicy policy,
                                             MiddlewareOpsAuditPublisher auditPublisher,
                                             DatasourceTagResolver tagResolver) {
        MiddlewareOpsExecutor<RedisSummaryRequest, String> executor = new MiddlewareOpsExecutor<RedisSummaryRequest, String>() {
            @Override
            public Class<RedisSummaryRequest> getRequestType() {
                return RedisSummaryRequest.class;
            }

            @Override
            public String execute(RedisSummaryRequest request) {
                return "safe";
            }
        };
        MiddlewareOpsRequestValidator<RedisSummaryRequest> validator =
                new DefaultMiddlewareOpsRequestValidator<RedisSummaryRequest>(RedisSummaryRequest.class) {
                    @Override
                    public void validate(RedisSummaryRequest request) {
                        requireDatasource(request.getDatasourceKey());
                    }
                };
        MiddlewareOpsExecutorRegistry registry = new DefaultMiddlewareOpsExecutorRegistry(
                Collections.<MiddlewareOpsExecutor<?, ?>>singletonList(executor),
                Collections.<MiddlewareOpsRequestValidator<?>>singletonList(validator));
        return new DefaultMiddlewareOpsServerEngine(resolver, policy, registry,
                new MiddlewareOpsConcurrencyGuard(2, 1), auditPublisher, tagResolver);
    }

    private MiddlewareOpsServerEngine genericEngine(MiddlewareOpsAuditPublisher auditPublisher) {
        MiddlewareOpsExecutor<MiddlewareOpsRequest, String> executor = new MiddlewareOpsExecutor<MiddlewareOpsRequest, String>() {
            @Override
            public Class<MiddlewareOpsRequest> getRequestType() {
                return MiddlewareOpsRequest.class;
            }

            @Override
            public String execute(MiddlewareOpsRequest request) {
                return "safe";
            }
        };
        MiddlewareOpsRequestValidator<MiddlewareOpsRequest> validator =
                new DefaultMiddlewareOpsRequestValidator<MiddlewareOpsRequest>(MiddlewareOpsRequest.class) {
                    @Override
                    public void validate(MiddlewareOpsRequest request) {
                        if (request.getCapability().name().endsWith("DATASOURCE_CATALOG")) {
                            return;
                        }
                        requireDatasource(request.getDatasourceKey());
                    }
                };
        MiddlewareOpsExecutorRegistry registry = new DefaultMiddlewareOpsExecutorRegistry(
                Collections.<MiddlewareOpsExecutor<?, ?>>singletonList(executor),
                Collections.<MiddlewareOpsRequestValidator<?>>singletonList(validator));
        return new DefaultMiddlewareOpsServerEngine(identity(), context -> true, registry,
                new MiddlewareOpsConcurrencyGuard(2, 1), auditPublisher, null);
    }

    private MiddlewareOpsIdentityResolver identity() {
        return () -> MiddlewareOpsIdentity.builder().subject("tester").displayName("tester")
                .authenticationMechanism("test").build();
    }

    private RedisSummaryRequest request() {
        return RedisSummaryRequest.builder().datasourceKey("default").build();
    }
}

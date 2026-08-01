package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditContext;
import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditEvent;
import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditPublisher;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.context.MySqlRouteContextHolder;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.resolver.MySqlRouteResolver;
import io.github.surezzzzzz.sdk.mysql.route.support.MySqlRouteDigestHelper;
import io.github.surezzzzzz.sdk.mysql.route.template.MySqlRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
public class MySqlRouteTemplateTest {

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 模板与审计测试");
    }

    @AfterEach
    public void clearThreadState() {
        MySqlRouteContextHolder.clear();
        TransactionSynchronizationManager.clear();
    }

    @Test
    public void shouldRestoreContextAndPublishSuccessEvent() {
        SimpleMysqlRouteRegistry registry = registryWith("test-ops-a");
        RecordingPublisher publisher = new RecordingPublisher();
        MySqlRouteTemplate template = template(registry, routeKey -> "test-ops-a", publisher);

        try (MySqlRouteAuditContext.Scope ignored = MySqlRouteAuditContext.open(
                new MySqlRouteAuditContext("test-subject", "MYSQL_QUERY", "test-request", null))) {
            assertEquals("test-value", template.execute("test_order", () -> {
                assertEquals("test-ops-a", MySqlRouteContextHolder.current());
                return "test-value";
            }));
        }
        assertNull(MySqlRouteContextHolder.current());
        assertEquals(1, publisher.events.size());
        MySqlRouteAuditEvent event = publisher.events.get(0);
        assertEquals(SimpleMysqlRouteConstant.AUDIT_STATUS_SUCCESS, event.getStatus());
        assertEquals(SimpleMysqlRouteConstant.MIDDLEWARE_TYPE_MYSQL, event.getMiddlewareType());
        assertEquals("test-request", event.getRequestId());
        assertEquals(MySqlRouteDigestHelper.sha256("test_order"), event.getResourceDigest());
    }

    @Test
    public void shouldKeepOuterAuditContextAfterNestedScope() {
        SimpleMysqlRouteRegistry registry = registryWith("test-ops-a");
        MySqlRouteTemplate template = template(registry, routeKey -> "test-ops-a", new RecordingPublisher());
        MySqlRouteAuditContext outer = new MySqlRouteAuditContext("outer", "OUTER", "outer-request", "outer-digest");
        MySqlRouteAuditContext inner = new MySqlRouteAuditContext("inner", "INNER", "inner-request", "inner-digest");

        try (MySqlRouteAuditContext.Scope outerScope = MySqlRouteAuditContext.open(outer)) {
            try (MySqlRouteAuditContext.Scope innerScope = MySqlRouteAuditContext.open(inner)) {
                template.execute("test_order", () -> null);
            }
            assertEquals(outer, MySqlRouteAuditContext.current());
        }
        assertNull(MySqlRouteAuditContext.current());
    }

    @Test
    public void shouldRejectDifferentDatasourceBeforeCallback() {
        SimpleMysqlRouteRegistry registry = registryWith("test-ops-a", "test-audit-a");
        RecordingPublisher publisher = new RecordingPublisher();
        MySqlRouteResolver resolver = routeKey -> routeKey.endsWith("a") ? "test-ops-a" : "test-audit-a";
        MySqlRouteTemplate template = template(registry, resolver, publisher);
        AtomicBoolean invoked = new AtomicBoolean(false);

        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> template.executeOnSameDatasource(Arrays.asList("test_a", "test_b"), () -> {
                    invoked.set(true);
                    return null;
                }));
        assertEquals(ErrorCode.OPERATION_CROSS_DATASOURCE, exception.getCode());
        assertFalse(invoked.get());
        assertEquals(1, publisher.events.size());
        assertEquals(SimpleMysqlRouteConstant.AUDIT_STATUS_CONFLICT, publisher.events.get(0).getStatus());
        assertNull(publisher.events.get(0).getDatasourceKey());
    }

    @Test
    public void shouldPublishRouteResolutionFailureOnce() {
        RecordingPublisher publisher = new RecordingPublisher();
        MySqlRouteTemplate template = template(mock(SimpleMysqlRouteRegistry.class), routeKey -> {
            throw new SimpleMysqlRouteException(ErrorCode.ROUTE_NOT_FOUND, "controlled");
        }, publisher);

        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> template.execute("test_unknown", () -> null));
        assertEquals(ErrorCode.ROUTE_NOT_FOUND, exception.getCode());
        assertEquals(1, publisher.events.size());
        assertEquals(SimpleMysqlRouteConstant.AUDIT_STATUS_NOT_FOUND, publisher.events.get(0).getStatus());
        assertNull(publisher.events.get(0).getDatasourceKey());
        assertEquals(MySqlRouteDigestHelper.sha256("test_unknown"), publisher.events.get(0).getResourceDigest());
    }

    @Test
    public void shouldPublishDirectTargetAndCallbackFailuresOnce() {
        SimpleMysqlRouteRegistry registry = mock(SimpleMysqlRouteRegistry.class);
        RecordingPublisher publisher = new RecordingPublisher();
        MySqlRouteTemplate template = template(registry, routeKey -> "test-ops-a", publisher);
        when(registry.getDataSource("test-unknown")).thenThrow(
                new SimpleMysqlRouteException(ErrorCode.DATASOURCE_NOT_FOUND, "controlled"));

        SimpleMysqlRouteException unknown = assertThrows(SimpleMysqlRouteException.class,
                () -> template.executeOn("test-unknown", () -> null));
        assertEquals(ErrorCode.DATASOURCE_NOT_FOUND, unknown.getCode());
        assertEquals(SimpleMysqlRouteConstant.AUDIT_STATUS_NOT_FOUND, publisher.events.get(0).getStatus());
        assertEquals(MySqlRouteDigestHelper.sha256("test-unknown"), publisher.events.get(0).getResourceDigest());

        SimpleMysqlRouteException callback = assertThrows(SimpleMysqlRouteException.class,
                () -> template.executeOn("test-ops-a", null));
        assertEquals(ErrorCode.CALLBACK_INVALID, callback.getCode());
        assertEquals(2, publisher.events.size());
        assertEquals(SimpleMysqlRouteConstant.AUDIT_STATUS_BAD_REQUEST, publisher.events.get(1).getStatus());
    }

    @Test
    public void shouldRejectDirectTargetAccessDuringTransaction() {
        MySqlRouteTemplate template = template(mock(SimpleMysqlRouteRegistry.class), routeKey -> "test-ops-a",
                new RecordingPublisher());
        TransactionSynchronizationManager.setActualTransactionActive(true);

        SimpleMysqlRouteException exception = assertThrows(SimpleMysqlRouteException.class,
                () -> template.dataSource("test-ops-a"));
        assertEquals(ErrorCode.DIRECT_TARGET_IN_TRANSACTION, exception.getCode());
    }

    @Test
    public void shouldUseProvidedResourceDigestAndContainPublisherFailure() {
        SimpleMysqlRouteRegistry registry = registryWith("test-ops-a");
        RecordingPublisher publisher = new RecordingPublisher();
        MySqlRouteTemplate template = template(registry, routeKey -> "test-ops-a", publisher);

        try (MySqlRouteAuditContext.Scope ignored = MySqlRouteAuditContext.open(
                new MySqlRouteAuditContext("test-subject", "MYSQL_QUERY", "test-request", "provided-digest"))) {
            template.execute("test_order", () -> null);
        }
        assertEquals("provided-digest", publisher.events.get(0).getResourceDigest());

        MySqlRouteTemplate failingPublisherTemplate = template(registry, routeKey -> "test-ops-a",
                event -> {
                    throw new IllegalStateException("controlled");
                });
        assertEquals("test-value", failingPublisherTemplate.execute("test_order", () -> "test-value"));
    }

    @Test
    public void shouldPublishCallbackFailureWithServerStatus() {
        SimpleMysqlRouteRegistry registry = registryWith("test-ops-a");
        RecordingPublisher publisher = new RecordingPublisher();
        MySqlRouteTemplate template = template(registry, routeKey -> "test-ops-a", publisher);

        assertThrows(IllegalStateException.class,
                () -> template.execute("test_order", () -> {
                    throw new IllegalStateException("controlled");
                }));
        assertEquals(1, publisher.events.size());
        assertEquals(SimpleMysqlRouteConstant.AUDIT_STATUS_INTERNAL_SERVER_ERROR, publisher.events.get(0).getStatus());
    }

    private SimpleMysqlRouteRegistry registryWith(String... datasourceKeys) {
        SimpleMysqlRouteRegistry registry = mock(SimpleMysqlRouteRegistry.class);
        for (String datasourceKey : datasourceKeys) {
            when(registry.getDataSource(datasourceKey)).thenReturn(mock(DataSource.class));
        }
        return registry;
    }

    private MySqlRouteTemplate template(SimpleMysqlRouteRegistry registry, MySqlRouteResolver resolver,
                                        MySqlRouteAuditPublisher publisher) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(mock(DataSource.class));
        return new MySqlRouteTemplate(registry, resolver, jdbcTemplate,
                new NamedParameterJdbcTemplate(jdbcTemplate), publisher);
    }

    private static class RecordingPublisher implements MySqlRouteAuditPublisher {
        private final List<MySqlRouteAuditEvent> events = new java.util.ArrayList<>();

        @Override
        public void publish(MySqlRouteAuditEvent event) {
            events.add(event);
        }
    }
}

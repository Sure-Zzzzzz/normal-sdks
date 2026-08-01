package io.github.surezzzzzz.sdk.mysql.route.template;

import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditContext;
import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditEvent;
import io.github.surezzzzzz.sdk.mysql.route.audit.MySqlRouteAuditPublisher;
import io.github.surezzzzzz.sdk.mysql.route.audit.NoopMySqlRouteAuditPublisher;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.mysql.route.constant.SimpleMysqlRouteConstant;
import io.github.surezzzzzz.sdk.mysql.route.context.MySqlRouteContextHolder;
import io.github.surezzzzzz.sdk.mysql.route.datasource.MySqlRoutingDataSource;
import io.github.surezzzzzz.sdk.mysql.route.exception.SimpleMysqlRouteException;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.resolver.MySqlRouteResolver;
import io.github.surezzzzzz.sdk.mysql.route.support.MySqlRouteDigestHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * MySQL Route 显式作用域模板。
 *
 * @author surezzzzzz
 */
@Slf4j
public class MySqlRouteTemplate {

    private final SimpleMysqlRouteRegistry registry;
    private final MySqlRouteResolver resolver;
    private final JdbcTemplate routingJdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final MySqlRouteAuditPublisher auditPublisher;

    public MySqlRouteTemplate(SimpleMysqlRouteRegistry registry, MySqlRouteResolver resolver,
                              JdbcTemplate routingJdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                              MySqlRouteAuditPublisher auditPublisher) {
        this.registry = registry;
        this.resolver = resolver;
        this.routingJdbcTemplate = routingJdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.auditPublisher = auditPublisher == null ? new NoopMySqlRouteAuditPublisher() : auditPublisher;
    }

    /**
     * 解析业务路由键并在对应数据源作用域内执行回调。
     *
     * @param routeKey 调用方提供的业务路由键
     * @param callback 在路由作用域内执行的回调
     * @param <T>      回调返回类型
     * @return 回调返回结果
     */
    public <T> T execute(String routeKey, Supplier<T> callback) {
        String datasourceKey;
        try {
            datasourceKey = resolver.resolve(routeKey);
        } catch (RuntimeException e) {
            publishAudit(routeKey, null, auditStatus(e), 0L);
            throw e;
        }
        return executeResolved(routeKey, datasourceKey, callback);
    }

    /**
     * 使用调用方显式指定的数据源键执行回调。
     *
     * @param datasourceKey 已注册的数据源键
     * @param callback      在路由作用域内执行的回调
     * @param <T>           回调返回类型
     * @return 回调返回结果
     */
    public <T> T executeOn(String datasourceKey, Supplier<T> callback) {
        return executeResolved(datasourceKey, datasourceKey, callback);
    }

    /**
     * 预先确认全部业务路由键解析到同一数据源后执行回调。
     *
     * @param routeKeys 待共同执行的业务路由键
     * @param callback  在路由作用域内执行的回调
     * @param <T>       回调返回类型
     * @return 回调返回结果
     */
    public <T> T executeOnSameDatasource(Collection<String> routeKeys, Supplier<T> callback) {
        String firstRouteKey = routeKeys == null || routeKeys.isEmpty() ? null : routeKeys.iterator().next();
        if (routeKeys == null || routeKeys.isEmpty()) {
            publishAudit(null, null, SimpleMysqlRouteConstant.AUDIT_STATUS_BAD_REQUEST, 0L);
            throw new SimpleMysqlRouteException(ErrorCode.ROUTE_KEYS_INVALID, ErrorMessage.ROUTE_KEYS_INVALID);
        }
        Set<String> datasourceKeys = new HashSet<>();
        try {
            for (String routeKey : routeKeys) {
                datasourceKeys.add(resolver.resolve(routeKey));
            }
        } catch (RuntimeException e) {
            publishAudit(firstRouteKey, null, auditStatus(e), 0L);
            throw e;
        }
        if (datasourceKeys.size() != 1) {
            publishAudit(firstRouteKey, null, SimpleMysqlRouteConstant.AUDIT_STATUS_CONFLICT, 0L);
            throw new SimpleMysqlRouteException(ErrorCode.OPERATION_CROSS_DATASOURCE,
                    ErrorMessage.OPERATION_CROSS_DATASOURCE);
        }
        return executeResolved(firstRouteKey, datasourceKeys.iterator().next(), callback);
    }

    /**
     * 获取必须在 Route 作用域内使用的路由 JdbcTemplate。
     *
     * @return 路由 JdbcTemplate
     */
    public JdbcTemplate routingJdbcTemplate() {
        return routingJdbcTemplate;
    }

    /**
     * 获取必须在 Route 作用域内使用的命名参数 JdbcTemplate。
     *
     * @return 路由 NamedParameterJdbcTemplate
     */
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate() {
        return namedParameterJdbcTemplate;
    }

    /**
     * 获取必须在 Route 作用域内使用的路由数据源。
     *
     * @return 路由数据源
     */
    public DataSource routingDataSource() {
        return routingJdbcTemplate.getDataSource();
    }

    /**
     * 获取显式目标的 JdbcTemplate。
     *
     * <p>当前线程存在 Spring 事务时禁止调用，避免绕过路由事务边界。</p>
     *
     * @param datasourceKey 已注册的数据源键
     * @return 目标 JdbcTemplate
     */
    public JdbcTemplate jdbcTemplate(String datasourceKey) {
        ensureNoTransactionForDirectTarget();
        return registry.getJdbcTemplate(datasourceKey);
    }

    /**
     * 获取显式目标的物理数据源。
     *
     * <p>当前线程存在 Spring 事务时禁止调用，避免绕过路由事务边界。</p>
     *
     * @param datasourceKey 已注册的数据源键
     * @return 目标物理数据源
     */
    public DataSource dataSource(String datasourceKey) {
        ensureNoTransactionForDirectTarget();
        return registry.getDataSource(datasourceKey);
    }

    private <T> T executeResolved(String routeKey, String datasourceKey, Supplier<T> callback) {
        long startedAt = System.currentTimeMillis();
        int status = SimpleMysqlRouteConstant.AUDIT_STATUS_INTERNAL_SERVER_ERROR;
        try {
            if (callback == null) {
                throw new SimpleMysqlRouteException(ErrorCode.CALLBACK_INVALID, ErrorMessage.CALLBACK_INVALID);
            }
            ensureDatasource(datasourceKey);
            bindTransactionDatasource(datasourceKey);
            try (MySqlRouteContextHolder.Scope ignored = MySqlRouteContextHolder.push(datasourceKey)) {
                T result = callback.get();
                status = SimpleMysqlRouteConstant.AUDIT_STATUS_SUCCESS;
                return result;
            }
        } catch (RuntimeException e) {
            status = auditStatus(e);
            throw e;
        } finally {
            publishAudit(routeKey, datasourceKey, status, System.currentTimeMillis() - startedAt);
        }
    }

    private void publishAudit(String routeKey, String datasourceKey, int status, long durationMillis) {
        MySqlRouteAuditContext context = MySqlRouteAuditContext.current();
        String digest = context == null || context.getResourceDigest() == null
                ? digestRouteKey(routeKey) : context.getResourceDigest();
        try {
            auditPublisher.publish(MySqlRouteAuditEvent.builder().occurredAt(Instant.now())
                    .subject(context == null ? null : context.getSubject())
                    .capability(context == null ? null : context.getCapability())
                    .middlewareType(SimpleMysqlRouteConstant.MIDDLEWARE_TYPE_MYSQL).datasourceKey(datasourceKey).resourceDigest(digest)
                    .status(status).durationMillis(durationMillis)
                    .requestId(context == null ? null : context.getRequestId()).build());
        } catch (RuntimeException e) {
            log.warn("MySQL Route 审计事件发布失败，datasourceKey={}，status={}", datasourceKey, status);
        }
    }

    private String digestRouteKey(String routeKey) {
        return MySqlRouteDigestHelper.sha256(routeKey);
    }

    private void ensureDatasource(String datasourceKey) {
        registry.getDataSource(datasourceKey);
    }

    private void bindTransactionDatasource(String datasourceKey) {
        DataSource dataSource = routingDataSource();
        if (dataSource instanceof MySqlRoutingDataSource) {
            ((MySqlRoutingDataSource) dataSource).bindTransactionDatasource(datasourceKey);
        }
    }

    private void ensureNoTransactionForDirectTarget() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new SimpleMysqlRouteException(ErrorCode.DIRECT_TARGET_IN_TRANSACTION,
                    ErrorMessage.DIRECT_TARGET_IN_TRANSACTION);
        }
    }

    private int auditStatus(RuntimeException exception) {
        if (!(exception instanceof SimpleMysqlRouteException)) {
            return SimpleMysqlRouteConstant.AUDIT_STATUS_INTERNAL_SERVER_ERROR;
        }
        String code = ((SimpleMysqlRouteException) exception).getCode();
        if (ErrorCode.CALLBACK_INVALID.equals(code) || ErrorCode.ROUTE_KEYS_INVALID.equals(code)
                || ErrorCode.ROUTE_KEY_INVALID.equals(code)) {
            return SimpleMysqlRouteConstant.AUDIT_STATUS_BAD_REQUEST;
        }
        if (ErrorCode.ROUTE_NOT_FOUND.equals(code) || ErrorCode.DATASOURCE_NOT_FOUND.equals(code)) {
            return SimpleMysqlRouteConstant.AUDIT_STATUS_NOT_FOUND;
        }
        if (ErrorCode.TRANSACTION_CROSS_DATASOURCE.equals(code)
                || ErrorCode.OPERATION_CROSS_DATASOURCE.equals(code)
                || ErrorCode.DIRECT_TARGET_IN_TRANSACTION.equals(code)) {
            return SimpleMysqlRouteConstant.AUDIT_STATUS_CONFLICT;
        }
        return SimpleMysqlRouteConstant.AUDIT_STATUS_INTERNAL_SERVER_ERROR;
    }
}

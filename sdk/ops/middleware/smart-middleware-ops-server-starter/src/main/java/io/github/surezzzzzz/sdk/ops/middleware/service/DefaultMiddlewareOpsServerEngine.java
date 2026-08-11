package io.github.surezzzzzz.sdk.ops.middleware.service;

import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditContext;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditContextFactory;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditEvent;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditPublisher;
import io.github.surezzzzzz.sdk.ops.middleware.authentication.MiddlewareOpsIdentity;
import io.github.surezzzzzz.sdk.ops.middleware.authentication.MiddlewareOpsIdentityResolver;
import io.github.surezzzzzz.sdk.ops.middleware.authorization.MiddlewareOpsAuthorizationContext;
import io.github.surezzzzzz.sdk.ops.middleware.authorization.MiddlewareOpsAuthorizationPolicy;
import io.github.surezzzzzz.sdk.ops.middleware.catalog.DatasourceTagResolver;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.support.MiddlewareOpsConcurrencyGuard;
import io.github.surezzzzzz.sdk.ops.middleware.support.MiddlewareOpsDigestHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;

/**
 * 默认受控只读能力编排器。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultMiddlewareOpsServerEngine implements MiddlewareOpsServerEngine {

    private final MiddlewareOpsIdentityResolver identityResolver;
    private final MiddlewareOpsAuthorizationPolicy authorizationPolicy;
    private final MiddlewareOpsExecutorRegistry executorRegistry;
    private final MiddlewareOpsConcurrencyGuard concurrencyGuard;
    private final MiddlewareOpsAuditPublisher auditPublisher;
    private final DatasourceTagResolver datasourceTagResolver;

    /**
     * 创建默认编排器。
     *
     * @param identityResolver    身份解析器
     * @param authorizationPolicy 授权策略
     * @param executorRegistry    类型化执行器注册表
     * @param concurrencyGuard    并发守卫
     */
    public DefaultMiddlewareOpsServerEngine(MiddlewareOpsIdentityResolver identityResolver,
                                            MiddlewareOpsAuthorizationPolicy authorizationPolicy,
                                            MiddlewareOpsExecutorRegistry executorRegistry,
                                            MiddlewareOpsConcurrencyGuard concurrencyGuard) {
        this(identityResolver, authorizationPolicy, executorRegistry, concurrencyGuard, null, null);
    }

    /**
     * 创建具备审计事件发布能力的默认编排器。
     *
     * @param identityResolver    身份解析器
     * @param authorizationPolicy 授权策略
     * @param executorRegistry    类型化执行器注册表
     * @param concurrencyGuard    并发守卫
     * @param auditPublisher      审计事件发布器
     */
    public DefaultMiddlewareOpsServerEngine(MiddlewareOpsIdentityResolver identityResolver,
                                            MiddlewareOpsAuthorizationPolicy authorizationPolicy,
                                            MiddlewareOpsExecutorRegistry executorRegistry,
                                            MiddlewareOpsConcurrencyGuard concurrencyGuard,
                                            MiddlewareOpsAuditPublisher auditPublisher) {
        this(identityResolver, authorizationPolicy, executorRegistry, concurrencyGuard, auditPublisher, null);
    }

    /**
     * 创建具备审计标签解析能力的默认编排器。
     *
     * @param identityResolver      身份解析器
     * @param authorizationPolicy   授权策略
     * @param executorRegistry      类型化执行器注册表
     * @param concurrencyGuard      并发守卫
     * @param auditPublisher        审计事件发布器
     * @param datasourceTagResolver 启动期数据源标签解析器
     */
    public DefaultMiddlewareOpsServerEngine(MiddlewareOpsIdentityResolver identityResolver,
                                            MiddlewareOpsAuthorizationPolicy authorizationPolicy,
                                            MiddlewareOpsExecutorRegistry executorRegistry,
                                            MiddlewareOpsConcurrencyGuard concurrencyGuard,
                                            MiddlewareOpsAuditPublisher auditPublisher,
                                            DatasourceTagResolver datasourceTagResolver) {
        this.identityResolver = identityResolver;
        this.authorizationPolicy = authorizationPolicy;
        this.executorRegistry = executorRegistry;
        this.concurrencyGuard = concurrencyGuard;
        this.auditPublisher = auditPublisher;
        this.datasourceTagResolver = datasourceTagResolver;
    }

    @Override
    public <Res> Res execute(MiddlewareOpsRequest request, Class<Res> responseType) {
        long startedAt = System.currentTimeMillis();
        MiddlewareOpsIdentity identity = null;
        MiddlewareOpsAuditContext auditContext = null;
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        try {
            if (request == null || request.getCapability() == null) {
                throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "运维查询请求无效");
            }
            auditContext = MiddlewareOpsAuditContextFactory.capture(request);
            executorRegistry.validate(request);
            identity = identityResolver.resolve();
            if (identity == null) {
                throw new MiddlewareOpsException(HttpStatus.UNAUTHORIZED, "需要先完成身份认证");
            }
            MiddlewareOpsAuthorizationContext authorizationContext = MiddlewareOpsAuthorizationContext.builder()
                    .identity(identity).capability(request.getCapability())
                    .middlewareType(request.getCapability().getMiddlewareType()).datasourceKey(request.getDatasourceKey())
                    .resourceScope(request.getResourceScope()).build();
            if (!authorizationPolicy.isAllowed(authorizationContext)) {
                throw new MiddlewareOpsException(HttpStatus.FORBIDDEN, "当前身份无权执行该运维查询");
            }
            try (AutoCloseable ignored = concurrencyGuard.acquire(request)) {
                Object result = executeTyped(executorRegistry.getExecutor(request), request);
                if (!responseType.isInstance(result)) {
                    throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "运维查询响应类型不匹配");
                }
                status = HttpStatus.OK;
                return responseType.cast(result);
            }
        } catch (MiddlewareOpsException e) {
            status = e.getStatus();
            throw e;
        } catch (Exception e) {
            throw new MiddlewareOpsException(HttpStatus.SERVICE_UNAVAILABLE, "中间件运维查询暂不可用");
        } finally {
            publishAudit(request, auditContext, identity, status, System.currentTimeMillis() - startedAt);
        }
    }

    /**
     * 发布不含查询结果与异常内容的审计事件。
     */
    private void publishAudit(MiddlewareOpsRequest request, MiddlewareOpsAuditContext auditContext,
                              MiddlewareOpsIdentity identity, HttpStatus status, long durationMillis) {
        if (request == null || request.getCapability() == null || auditPublisher == null
                || !request.isAuditRequired() || !isAuditable(request.getCapability())) {
            return;
        }
        try {
            auditPublisher.publish(MiddlewareOpsAuditEvent.builder().occurredAt(Instant.now())
                    .subject(identity == null ? null : identity.getSubject()).capability(request.getCapability())
                    .middlewareType(request.getCapability().getMiddlewareType()).datasourceKey(request.getDatasourceKey())
                    .clusterTag(clusterTag(request)).resourceDigest(MiddlewareOpsDigestHelper.sha256(request.getResourceScope()))
                    .context(auditContext).status(status.value())
                    .durationMillis(durationMillis).requestId(requestId()).build());
        } catch (RuntimeException e) {
            log.warn("中间件运维审计事件发布失败，capability={}，status={}", request.getCapability(), status.value());
        }
    }

    private boolean isAuditable(MiddlewareOpsCapability capability) {
        return capability != MiddlewareOpsCapability.ELASTICSEARCH_DATASOURCE_CATALOG
                && capability != MiddlewareOpsCapability.REDIS_DATASOURCE_CATALOG
                && capability != MiddlewareOpsCapability.KAFKA_DATASOURCE_CATALOG
                && capability != MiddlewareOpsCapability.MYSQL_DATASOURCE_CATALOG
                && capability != MiddlewareOpsCapability.ELASTICSEARCH_SUMMARY
                && capability != MiddlewareOpsCapability.ELASTICSEARCH_INDEX_LIST;
    }

    /**
     * 从启动期快照解析审计展示标签。
     */
    private String clusterTag(MiddlewareOpsRequest request) {
        return datasourceTagResolver == null ? null : datasourceTagResolver.resolve(
                request.getCapability().getMiddlewareType(), request.getDatasourceKey());
    }

    /**
     * 获取当前 HTTP 请求标识；非 HTTP 调用不伪造请求标识。
     */
    private String requestId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
        Object value = request.getAttribute(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private Object executeTyped(MiddlewareOpsExecutor<?, ?> executor, MiddlewareOpsRequest request) {
        if (!executor.getRequestType().isInstance(request)) {
            throw new MiddlewareOpsException(HttpStatus.BAD_REQUEST, "运维查询请求类型不匹配");
        }
        return ((MiddlewareOpsExecutor<MiddlewareOpsRequest, Object>) executor).execute(request);
    }
}

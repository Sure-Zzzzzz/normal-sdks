package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ElasticsearchWriteApiHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.validator.PersistenceRequestValidatorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.ParameterizedType;

/**
 * Abstract Persistence Executor
 *
 * @author surezzzzzz
 */
@Slf4j
public abstract class AbstractPersistenceExecutor<Req extends PersistenceRequest, Res>
        implements PersistenceExecutor<Req, Res> {

    @Autowired
    protected SimpleElasticsearchRouteRegistry registry;

    @Autowired
    protected RouteResolver routeResolver;

    @Autowired
    protected WriteIndexResolver writeIndexResolver;

    @Autowired
    protected ApplicationEventPublisher eventPublisher;

    @Autowired
    protected ElasticsearchWriteApiHelper writeApiHelper;

    @Autowired
    protected DocumentPreProcessorChain documentPreProcessorChain;

    @Autowired
    protected PersistenceRequestValidatorRegistry validatorRegistry;

    private final Class<Req> requestType;

    @SuppressWarnings("unchecked")
    protected AbstractPersistenceExecutor() {
        this.requestType = (Class<Req>) ((ParameterizedType) getClass()
                .getGenericSuperclass()).getActualTypeArguments()[0];
    }

    @Override
    public Class<Req> getRequestType() {
        return requestType;
    }

    @Override
    public final Res execute(Req request) {
        long startTimeMs = System.currentTimeMillis();
        PersistenceExecutionContext context = createContext(request, startTimeMs);
        try {
            validate(request);
            validatorRegistry.validate(request);
            String index = getIndex(request);
            String datasource = resolveDatasource(request, index);
            context.setIndex(index);
            context.setDatasource(datasource);
            Res result = doExecute(request, datasource, context);
            context.setTookMs(System.currentTimeMillis() - startTimeMs);
            publishSuccessEvent(request, result, context);
            return result;
        } catch (Exception e) {
            context.setTookMs(System.currentTimeMillis() - startTimeMs);
            publishErrorEvent(request, e, context);
            throw wrap(e);
        }
    }

    protected PersistenceExecutionContext createContext(Req request, long startTimeMs) {
        return PersistenceExecutionContext.builder()
                .operationType(getOperationType())
                .startTimeMs(startTimeMs)
                .build();
    }

    protected String resolveDatasource(Req request, String index) {
        return routeResolver.resolveDataSource(index);
    }

    /**
     * 将 raw index name 按命中的路由规则渲染为当日写索引。
     * 未命中规则或无模板时返回原值。
     */
    protected String resolveWriteIndex(String rawIndex) {
        return writeIndexResolver.resolveWriteIndex(rawIndex);
    }

    protected abstract PersistenceOperationType getOperationType();

    protected abstract void validate(Req request);

    protected abstract String getIndex(Req request);

    protected abstract Res doExecute(Req request, String datasource,
                                     PersistenceExecutionContext context) throws Exception;

    protected abstract SimpleElasticsearchPersistenceException wrap(Exception e);

    private void publishSuccessEvent(Req request, Res result, PersistenceExecutionContext context) {
        try {
            eventPublisher.publishEvent(new EsPersistenceEvent(this, request, result, context));
        } catch (Exception e) {
            log.warn("persistence 成功事件发布失败，operationType=[{}]", context.getOperationType(), e);
        }
    }

    private void publishErrorEvent(Req request, Throwable error, PersistenceExecutionContext context) {
        try {
            eventPublisher.publishEvent(new EsPersistenceErrorEvent(this, request, error, context));
        } catch (Exception e) {
            log.warn("persistence 失败事件发布失败，operationType=[{}]", context.getOperationType(), e);
        }
    }
}

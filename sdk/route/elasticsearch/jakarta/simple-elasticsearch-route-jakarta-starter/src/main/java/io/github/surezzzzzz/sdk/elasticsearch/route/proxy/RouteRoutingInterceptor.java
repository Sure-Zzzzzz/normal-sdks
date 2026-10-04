package io.github.surezzzzzz.sdk.elasticsearch.route.proxy;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties.RouteRule;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexNameExtractor;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.script.ScriptOperations;
import org.springframework.data.elasticsearch.core.sql.SqlOperations;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * Spring Data Elasticsearch 5 的核心路由拦截器。
 *
 * <p>每次调用先从最高优先级的索引信息源中提取所有候选索引，再判定唯一数据源。跨数据源
 * 请求和无法安全改写到 {@link IndexCoordinates} 的分片操作均在网络调用前失败，不能退回
 * 默认数据源。这里的重载适配基于 Spring Data Elasticsearch 5 的准确签名，而不是在原
 * 调用尾部盲目追加参数。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class RouteRoutingInterceptor {

    private static final Set<String> WRITE_METHODS = new HashSet<>(
            Arrays.asList(SimpleElasticsearchRouteConstant.WRITE_METHODS.split(",")));
    private static final Set<String> READ_METHODS = new HashSet<>(
            Arrays.asList(SimpleElasticsearchRouteConstant.READ_METHODS.split(",")));

    /**
     * PIT 关闭调用只有 PIT ID、没有索引参数。缓存有上限，条目被逐出后关闭调用必须失败，
     * 不能默认为 primary 而关闭错误集群上的 PIT。
     */
    private final Map<String, String> pointInTimeDatasourceMap = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > SimpleElasticsearchRouteConstant.PIT_ROUTE_CACHE_CAPACITY;
                }
            });

    private final Map<String, ElasticsearchOperations> templates;
    private final RouteResolver routeResolver;
    private final List<IndexNameExtractor> indexNameExtractors;
    private final Map<String, ExecutorService> asyncWriteExecutorMap;
    private final WriteIndexResolver writeIndexResolver;

    /**
     * 路由并执行一次 Spring Data 操作。
     *
     * @param method     调用的 ElasticsearchOperations 方法
     * @param args       调用参数
     * @param target     已判定的数据源和模板
     * @param indexNames 本次调用涉及的全部索引
     * @return 原始 Spring Data 返回值
     */
    public Object route(Method method, Object[] args, RouteTarget target, String[] indexNames) {
        rejectUnsupportedOperation(method);
        rejectAmbiguousWriteBatch(method, indexNames);
        log.debug("ES 路由操作开始，method=[{}]，datasource=[{}]，indexCount=[{}]，write=[{}]，read=[{}]",
                method.getName(), target.getDatasourceKey(), indexNames.length,
                isWriteOperation(method), isReadOperation(method));

        String indexName = indexNames.length == 1 ? indexNames[0] : null;
        RouteRule rule = indexName == null ? null : routeResolver.resolveRule(indexName);
        ElasticsearchOperations template = target.getTemplate();

        if (isIndexOperationsByClass(method)) {
            if (rule != null && StringUtils.hasText(rule.getEffectiveWriteIndexTemplate())) {
                throw unsupportedIndexRewrite(method, "索引管理");
            }
            // indexOps(Class) 会保留实体绑定，createWithMapping() 依赖该 Class 创建映射。
            log.debug("ES 路由操作委派完成，method=[{}]，datasource=[{}]，mode=[entity-bound-index-ops]",
                    method.getName(), target.getDatasourceKey());
            return invokeOriginal(template, method, args);
        }

        if (isWriteOperation(method) && rule != null && rule.isAsyncWrite()) {
            doAsyncWrite(target, method, args, rule);
            return null;
        }

        Object result;
        if (isWriteOperation(method) && rule != null
                && StringUtils.hasText(rule.getEffectiveWriteIndexTemplate())) {
            result = invokeWithIndexCoordinates(template, method, args,
                    writeIndexResolver.resolveWriteIndex(rule), "写");
        } else if (isReadOperation(method) && rule != null
                && StringUtils.hasText(rule.getEffectiveReadIndexPattern())) {
            result = doReadRoute(template, method, args, rule.getEffectiveReadIndexPattern());
        } else {
            result = invokeOriginal(template, method, args);
        }

        recordPointInTimeRoute(method, args, target.getDatasourceKey(), result);
        log.debug("ES 路由操作委派完成，method=[{}]，datasource=[{}]",
                method.getName(), target.getDatasourceKey());
        return result;
    }

    /**
     * 根据候选索引选择唯一数据源；找不到对应模板也是配置错误，禁止回退到默认模板。
     */
    RouteTarget determineTarget(String[] indexNames) {
        String datasourceKey = routeResolver.resolveDataSourceOrThrow(indexNames);
        ElasticsearchOperations template = templates.get(datasourceKey);
        if (template == null) {
            throw new RouteException(ErrorCode.ROUTE_DATASOURCE_NOT_FOUND,
                    String.format(ErrorMessage.ROUTE_DATASOURCE_NOT_FOUND, datasourceKey, templates.keySet()));
        }
        return new RouteTarget(datasourceKey, template);
    }

    /**
     * 关闭 PIT 时从打开请求记录的源定位模板，缓存不存在时 fail-closed。
     */
    RouteTarget determinePointInTimeTarget(String pointInTimeId) {
        String datasourceKey = pointInTimeDatasourceMap.get(pointInTimeId);
        if (!StringUtils.hasText(datasourceKey)) {
            throw new RouteException(ErrorCode.ROUTE_POINT_IN_TIME_UNRESOLVED,
                    String.format(ErrorMessage.ROUTE_POINT_IN_TIME_UNRESOLVED, pointInTimeId));
        }
        ElasticsearchOperations template = templates.get(datasourceKey);
        if (template == null) {
            throw new RouteException(ErrorCode.ROUTE_DATASOURCE_NOT_FOUND,
                    String.format(ErrorMessage.ROUTE_DATASOURCE_NOT_FOUND, datasourceKey, templates.keySet()));
        }
        return new RouteTarget(datasourceKey, template);
    }

    boolean isClosePointInTime(Method method) {
        return SimpleElasticsearchRouteConstant.METHOD_CLOSE_POINT_IN_TIME.equals(method.getName());
    }

    boolean isFluentConfigurationMethod(Method method) {
        String methodName = method.getName();
        return SimpleElasticsearchRouteConstant.METHOD_WITH_ROUTING.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_WITH_REFRESH_POLICY.equals(methodName);
    }

    boolean isWriteOperation(Method method) {
        return WRITE_METHODS.contains(method.getName());
    }

    boolean isReadOperation(Method method) {
        return READ_METHODS.contains(method.getName());
    }

    /**
     * 以提取器优先级决定索引来源。显式 IndexCoordinates / Query 的索引优先于实体注解；
     * 同一来源中的全部索引均会参与跨数据源校验。
     */
    String[] extractIndexNames(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return new String[0];
        }
        for (IndexNameExtractor extractor : indexNameExtractors) {
            List<String> extracted = extractor.extractAll(method, args);
            if (extracted == null || extracted.isEmpty()) {
                continue;
            }
            Set<String> distinct = new LinkedHashSet<>();
            for (String indexName : extracted) {
                if (StringUtils.hasText(indexName)) {
                    distinct.add(indexName);
                }
            }
            if (!distinct.isEmpty()) {
                return distinct.toArray(new String[0]);
            }
        }
        return new String[0];
    }

    /**
     * 为保留原有包内测试和扩展调用，提供单索引兼容入口。
     */
    String extractIndexName(Method method, Object[] args) {
        String[] indexNames = extractIndexNames(method, args);
        return indexNames.length == 0 ? null : indexNames[0];
    }

    /**
     * 拒绝无法从调用参数中唯一确定数据源的操作，避免将其静默发送到默认集群。
     */
    private void rejectUnsupportedOperation(Method method) {
        String methodName = method.getName();
        if (SimpleElasticsearchRouteConstant.METHOD_REINDEX.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_SUBMIT_REINDEX.equals(methodName)
                || isDeclaredOperation(SqlOperations.class, method)
                || isDeclaredOperation(ScriptOperations.class, method)) {
            throw new RouteException(ErrorCode.ROUTE_OPERATION_UNSUPPORTED,
                    String.format(ErrorMessage.ROUTE_OPERATION_UNSUPPORTED, methodName));
        }
    }

    /**
     * JDK 代理传入的 Method 可能声明在聚合接口 ElasticsearchOperations 上。按接口继承关系判断会把
     * 所有普通 ElasticsearchOperations 方法误判为 ScriptOperations；必须按 SQL/脚本接口自身声明的
     * 精确签名判定。
     */
    private boolean isDeclaredOperation(Class<?> operationType, Method method) {
        return Arrays.stream(operationType.getDeclaredMethods())
                .anyMatch(candidate -> candidate.getName().equals(method.getName())
                        && Arrays.equals(candidate.getParameterTypes(), method.getParameterTypes()));
    }

    private void rejectAmbiguousWriteBatch(Method method, String[] indexNames) {
        if (isWriteOperation(method) && indexNames.length > 1) {
            throw new RouteException(ErrorCode.ROUTE_WRITE_MULTI_INDEX_UNSUPPORTED,
                    String.format(ErrorMessage.ROUTE_WRITE_MULTI_INDEX_UNSUPPORTED,
                            method.getName(), Arrays.asList(indexNames)));
        }
    }

    private boolean isIndexOperationsByClass(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return SimpleElasticsearchRouteConstant.METHOD_INDEX_OPS.equals(method.getName())
                && parameterTypes.length == 1
                && parameterTypes[0] == Class.class;
    }

    private void doAsyncWrite(RouteTarget target, Method method, Object[] args, RouteRule rule) {
        String targetIndex = writeIndexResolver.resolveWriteIndex(rule);
        ExecutorService executor = asyncWriteExecutorMap.get(target.getDatasourceKey());
        if (executor == null) {
            log.error("异步写线程池不存在，datasource=[{}]，method=[{}]，请求已丢弃",
                    target.getDatasourceKey(), method.getName());
            return;
        }

        Runnable task = () -> {
            try {
                if (StringUtils.hasText(targetIndex)) {
                    invokeWithIndexCoordinates(target.getTemplate(), method, args, targetIndex, "异步写");
                } else {
                    invokeOriginal(target.getTemplate(), method, args);
                }
            } catch (RuntimeException e) {
                log.warn("异步写失败，datasource=[{}]，method=[{}]，请求已丢弃",
                        target.getDatasourceKey(), method.getName(), e);
            }
        };
        try {
            executor.execute(task);
            log.debug("异步写任务已提交，datasource=[{}]，method=[{}]",
                    target.getDatasourceKey(), method.getName());
        } catch (RuntimeException e) {
            // 第三方自定义 executor 仍可能抛出拒绝异常。异步写契约要求调用线程立即返回。
            log.warn("异步写任务提交失败，datasource=[{}]，method=[{}]，请求已丢弃",
                    target.getDatasourceKey(), method.getName(), e);
        }
    }

    private Object doReadRoute(ElasticsearchOperations template, Method method,
                               Object[] args, String targetIndexPattern) {
        if (isGetById(method, args)) {
            return getByIdFromReadIndex(template, args, targetIndexPattern);
        }
        return invokeWithIndexCoordinates(template, method, args, targetIndexPattern, "读");
    }

    private boolean isGetById(Method method, Object[] args) {
        return SimpleElasticsearchRouteConstant.METHOD_GET.equals(method.getName())
                && args != null
                && args.length >= 2
                && args[0] instanceof String
                && args[1] instanceof Class;
    }

    /**
     * {@code GET /{index}/_doc/{id}} 不支持通配索引。日期分片的按 ID 查询改为 ids 条件的
     * search；发现重复 ID 时明确失败，避免静默返回任意一个分片中的文档。
     */
    @SuppressWarnings("unchecked")
    private Object getByIdFromReadIndex(ElasticsearchOperations template, Object[] args,
                                        String targetIndexPattern) {
        String id = (String) args[0];
        Class<Object> entityClass = (Class<Object>) args[1];
        CriteriaQuery query = new CriteriaQuery(new Criteria("_id").is(id));
        query.setMaxResults(2);
        SearchHits<Object> hits = template.search(query, entityClass, IndexCoordinates.of(targetIndexPattern));
        if (hits.isEmpty()) {
            return null;
        }
        if (hits.getSearchHits().size() > 1) {
            throw new RouteException(ErrorCode.ROUTE_READ_INDEX_AMBIGUOUS,
                    String.format(ErrorMessage.ROUTE_READ_INDEX_AMBIGUOUS, targetIndexPattern, id));
        }
        return hits.getSearchHit(0).getContent();
    }

    /**
     * 把逻辑索引改为 Spring Data 5 的准确重载。没有准确重载时拒绝调用，避免日期分片
     * 配置看似启用、实际却落入实体默认索引。
     */
    private Object invokeWithIndexCoordinates(ElasticsearchOperations template, Method method,
                                              Object[] args, String targetIndex, String operationName) {
        if (!StringUtils.hasText(targetIndex)) {
            throw unsupportedIndexRewrite(method, operationName);
        }
        IndexCoordinates coordinates = IndexCoordinates.of(targetIndex);
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] invokeArgs = args == null ? new Object[0] : Arrays.copyOf(args, args.length);

        if (hasLastIndexCoordinates(parameterTypes)) {
            invokeArgs[invokeArgs.length - 1] = coordinates;
            return invokeOperation(template, method.getName(), parameterTypes, invokeArgs);
        }

        if (replacesLeadingIndexCoordinates(method)) {
            invokeArgs[0] = coordinates;
            return invokeOperation(template, method.getName(), parameterTypes, invokeArgs);
        }

        if (isSaveVarargs(method)) {
            if (invokeArgs.length != 1 || !(invokeArgs[0] instanceof Object[])) {
                throw unsupportedIndexRewrite(method, operationName);
            }
            return invokeOperation(template, SimpleElasticsearchRouteConstant.METHOD_SAVE,
                    new Class<?>[]{Iterable.class, IndexCoordinates.class},
                    new Object[]{Arrays.asList((Object[]) invokeArgs[0]), coordinates});
        }

        if (replacesTrailingClassWithIndexCoordinates(method)) {
            Class<?>[] targetTypes = Arrays.copyOf(parameterTypes, parameterTypes.length);
            targetTypes[targetTypes.length - 1] = IndexCoordinates.class;
            invokeArgs[invokeArgs.length - 1] = coordinates;
            return invokeOperation(template, method.getName(), targetTypes, invokeArgs);
        }

        if (supportsAppendingIndexCoordinates(method)) {
            Class<?>[] targetTypes = Arrays.copyOf(parameterTypes, parameterTypes.length + 1);
            targetTypes[targetTypes.length - 1] = IndexCoordinates.class;
            Object[] targetArgs = Arrays.copyOf(invokeArgs, invokeArgs.length + 1);
            targetArgs[targetArgs.length - 1] = coordinates;
            return invokeOperation(template, method.getName(), targetTypes, targetArgs);
        }

        throw unsupportedIndexRewrite(method, operationName);
    }

    private boolean hasLastIndexCoordinates(Class<?>[] parameterTypes) {
        return parameterTypes.length > 0
                && parameterTypes[parameterTypes.length - 1] == IndexCoordinates.class;
    }

    private boolean isSaveVarargs(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return SimpleElasticsearchRouteConstant.METHOD_SAVE.equals(method.getName())
                && parameterTypes.length == 1
                && parameterTypes[0].isArray();
    }

    /**
     * {@code openPointInTime(IndexCoordinates, Duration...)} 的索引参数位于首位，不能按
     * 通用的末尾参数规则处理。PIT 需要覆盖日期分片时，只替换其唯一的索引参数。
     */
    private boolean replacesLeadingIndexCoordinates(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        return SimpleElasticsearchRouteConstant.METHOD_OPEN_POINT_IN_TIME.equals(method.getName())
                && parameterTypes.length >= 2
                && parameterTypes[0] == IndexCoordinates.class;
    }

    /**
     * 这些重载在 SDE 5 中以 IndexCoordinates 取代最后一个 Class 参数。其余 Class 参数
     * 仍承载实体映射语义，必须走追加型重载，不能泛化替换。
     */
    private boolean replacesTrailingClassWithIndexCoordinates(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0 || parameterTypes[parameterTypes.length - 1] != Class.class) {
            return false;
        }
        String methodName = method.getName();
        if (SimpleElasticsearchRouteConstant.METHOD_INDEX_OPS.equals(methodName)) {
            return parameterTypes.length == 1;
        }
        if (SimpleElasticsearchRouteConstant.METHOD_EXISTS.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_DELETE.equals(methodName)) {
            return parameterTypes.length == 2 && parameterTypes[0] == String.class;
        }
        if (SimpleElasticsearchRouteConstant.METHOD_BULK_INDEX.equals(methodName)) {
            return parameterTypes.length == 2 || parameterTypes.length == 3;
        }
        return SimpleElasticsearchRouteConstant.METHOD_BULK_UPDATE.equals(methodName)
                && parameterTypes.length == 2;
    }

    private boolean supportsAppendingIndexCoordinates(Method method) {
        String methodName = method.getName();
        if (SimpleElasticsearchRouteConstant.METHOD_SAVE.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_GET.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_MULTI_GET.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_UPDATE.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_COUNT.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_SEARCH.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_SEARCH_ONE.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_SEARCH_FOR_STREAM.equals(methodName)
                || SimpleElasticsearchRouteConstant.METHOD_MULTI_SEARCH.equals(methodName)) {
            return true;
        }
        return SimpleElasticsearchRouteConstant.METHOD_DELETE.equals(methodName)
                && (method.getParameterTypes().length == 1
                || method.getParameterTypes().length == 2);
    }

    private Object invokeOperation(ElasticsearchOperations template, String methodName,
                                   Class<?>[] parameterTypes, Object[] args) {
        try {
            Method targetMethod = ElasticsearchOperations.class.getMethod(methodName, parameterTypes);
            return invokeReflect(targetMethod, template, args);
        } catch (NoSuchMethodException e) {
            throw new RouteException(ErrorCode.ROUTE_INDEX_REWRITE_UNSUPPORTED,
                    String.format(ErrorMessage.ROUTE_INDEX_REWRITE_UNSUPPORTED, methodName), e);
        }
    }

    private Object invokeReflect(Method targetMethod, Object target, Object[] args) {
        try {
            return targetMethod.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof RuntimeException) {
                // 保留 Spring Data / Elasticsearch 的异常类型，路由层不应改写调用方的错误语义。
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw reflectionException(cause);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            throw reflectionException(e);
        }
    }

    Object invokeOriginal(ElasticsearchOperations template, Method method, Object[] args) {
        return invokeOperation(template, method.getName(), method.getParameterTypes(), args);
    }

    private void recordPointInTimeRoute(Method method, Object[] args, String datasourceKey, Object result) {
        if (SimpleElasticsearchRouteConstant.METHOD_OPEN_POINT_IN_TIME.equals(method.getName())
                && result instanceof String && StringUtils.hasText((String) result)) {
            pointInTimeDatasourceMap.put((String) result, datasourceKey);
            return;
        }
        if (isClosePointInTime(method) && args != null && args.length == 1
                && args[0] instanceof String && Boolean.TRUE.equals(result)) {
            pointInTimeDatasourceMap.remove(args[0]);
        }
    }

    private RouteException unsupportedIndexRewrite(Method method, String operationName) {
        return new RouteException(ErrorCode.ROUTE_INDEX_REWRITE_UNSUPPORTED,
                String.format(ErrorMessage.ROUTE_INDEX_REWRITE_UNSUPPORTED,
                        operationName, method.getName()));
    }

    private RouteException reflectionException(Throwable cause) {
        if (cause instanceof RouteException) {
            return (RouteException) cause;
        }
        return new RouteException(ErrorCode.ROUTE_REFLECTION_INVOKE_FAILED,
                ErrorMessage.ROUTE_REFLECTION_INVOKE_FAILED, cause);
    }

    /**
     * 模板选择结果，避免 fluent 代理重新根据第一项索引推断数据源。
     */
    @Getter
    @RequiredArgsConstructor
    static class RouteTarget {
        private final String datasourceKey;
        private final ElasticsearchOperations template;
    }
}

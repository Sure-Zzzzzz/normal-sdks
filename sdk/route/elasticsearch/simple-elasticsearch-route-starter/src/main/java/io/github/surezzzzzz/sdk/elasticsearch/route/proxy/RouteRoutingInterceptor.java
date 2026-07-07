package io.github.surezzzzzz.sdk.elasticsearch.route.proxy;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties.RouteRule;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.RouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.SimpleElasticsearchRouteException;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexNameExtractor;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * 核心路由拦截器
 *
 * <p>抽取 CGLIB 和 JDK 代理共用所有路由逻辑，包含：
 * <ul>
 *   <li>写/读操作判定</li>
 *   <li>日期分片模板渲染</li>
 *   <li>IndexCoordinates 重载动态查找</li>
 *   <li>异步写执行</li>
 * </ul>
 *
 * <p>被 {@link RouteTemplateProxy}（CGLIB）和 {@link JdkRouteTemplateProxy}（JDK）共用。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class RouteRoutingInterceptor {

    private static final Class<?> INDEX_COORDINATES_CLASS;
    private static final java.lang.reflect.Method INDEX_COORDINATES_OF;
    private static final boolean HAS_SAVE_METHOD;
    private static final boolean HAS_GET_METHOD;
    private static final Class<?> INDEX_QUERY_CLASS;

    static {
        Class<?> coordsClazz = null;
        java.lang.reflect.Method coordsOf = null;
        try {
            coordsClazz = Class.forName(SimpleElasticsearchRouteConstant.CLASS_INDEX_COORDINATES);
            coordsOf = coordsClazz.getMethod(SimpleElasticsearchRouteConstant.METHOD_OF, String[].class);
            log.debug("检测到 IndexCoordinates API，启用 Spring Data Elasticsearch 4.x 索引路由能力");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            log.info("当前 Spring Data Elasticsearch 版本未提供 IndexCoordinates API，将在需要时使用 3.x 兼容路由");
        }
        INDEX_COORDINATES_CLASS = coordsClazz;
        INDEX_COORDINATES_OF = coordsOf;

        Class<?> templateClass = null;
        try {
            templateClass = Class.forName(SimpleElasticsearchRouteConstant.CLASS_ELASTICSEARCH_REST_TEMPLATE);
        } catch (ClassNotFoundException ignored) {
            log.warn("未找到 ElasticsearchRestTemplate 类，路由代理无法初始化 template 兼容能力");
        }

        boolean hasSave = false;
        if (templateClass != null) {
            try {
                templateClass.getMethod(SimpleElasticsearchRouteConstant.METHOD_SAVE, Object.class);
                hasSave = true;
            } catch (NoSuchMethodException ignored) {
                log.info("当前 Spring Data Elasticsearch API 未声明 save(Object)，启用 HTTP 兼容写入路由");
            }
        }
        HAS_SAVE_METHOD = hasSave;

        boolean hasGet = false;
        if (templateClass != null) {
            try {
                templateClass.getMethod(SimpleElasticsearchRouteConstant.METHOD_GET, String.class, Class.class);
                hasGet = true;
            } catch (NoSuchMethodException ignored) {
                log.info("当前 Spring Data Elasticsearch API 未声明 get(String, Class)，启用 HTTP 兼容按 ID 查询路由");
            }
        }
        HAS_GET_METHOD = hasGet;

        Class<?> iqClass = null;
        try {
            iqClass = Class.forName(SimpleElasticsearchRouteConstant.CLASS_INDEX_QUERY);
        } catch (ClassNotFoundException e) {
            log.warn("未找到 IndexQuery 类，IndexQuery 兼容路由不可用：{}", e.getMessage());
        }
        INDEX_QUERY_CLASS = iqClass;
    }

    private final Map<String, ElasticsearchRestTemplate> templates;
    private final ElasticsearchRestTemplate defaultTemplate;
    private final RouteResolver routeResolver;
    private final List<IndexNameExtractor> indexNameExtractors;
    private final Map<String, ExecutorService> asyncWriteExecutorMap;
    private final WriteIndexResolver writeIndexResolver;

    Map<String, ElasticsearchRestTemplate> getTemplates() {
        return templates;
    }

    ElasticsearchRestTemplate getDefaultTemplate() {
        return defaultTemplate;
    }

    /**
     * 写操作方法名单（静态初始化，避免每次创建）
     */
    private static final Set<String> WRITE_METHODS = new HashSet<>(
            Arrays.asList(SimpleElasticsearchRouteConstant.WRITE_METHODS.split(","))
    );

    /**
     * 读操作方法名单
     */
    private static final Set<String> READ_METHODS = new HashSet<>(
            Arrays.asList(SimpleElasticsearchRouteConstant.READ_METHODS.split(","))
    );

    /**
     * 第一个 String 参数表示索引名的索引级方法。
     */
    private static final Set<String> STRING_INDEX_METHODS = new HashSet<>(
            Arrays.asList(SimpleElasticsearchRouteConstant.METHOD_CREATE_INDEX, SimpleElasticsearchRouteConstant.METHOD_DELETE_INDEX, SimpleElasticsearchRouteConstant.METHOD_INDEX_EXISTS, SimpleElasticsearchRouteConstant.METHOD_INDEX_OPS)
    );

    /**
     * 路由主入口
     *
     * @param method   调用的方法
     * @param args     方法参数
     * @param template 被代理的 template（用于实际调用）
     * @return 方法返回值；若规则启用了 {@code asyncWrite}，写操作提交线程池后立即返回 {@code null}
     */
    public Object route(Method method, Object[] args, ElasticsearchRestTemplate template) {
        String indexName = extractIndexName(method, args);
        RouteRule rule = routeResolver.resolveRule(indexName);

        if (!HAS_SAVE_METHOD && STRING_INDEX_METHODS.contains(method.getName())
                && args != null && args.length > 0 && args[0] instanceof String) {
            return doStringIndexForEs3x(template, method, args);
        }

        if (!HAS_SAVE_METHOD && SimpleElasticsearchRouteConstant.METHOD_SAVE.equals(method.getName()) && args != null && args.length == 1) {
            return doSaveForEs3x(template, method, args, rule);
        }

        if (!HAS_SAVE_METHOD && SimpleElasticsearchRouteConstant.METHOD_INDEX.equals(method.getName()) && args != null && args.length > 0
                && INDEX_QUERY_CLASS != null && INDEX_QUERY_CLASS.isInstance(args[0])) {
            return doIndexForEs3x(template, args, rule, indexName);
        }

        if (!HAS_GET_METHOD && SimpleElasticsearchRouteConstant.METHOD_GET.equals(method.getName())
                && args != null && args.length == 2 && args[1] instanceof Class) {
            if (rule != null && StringUtils.hasText(rule.getEffectiveReadIndexPattern())) {
                return doGetBySearchRoute(template, args, rule);
            }
            return doGetForEs3x(template, method, args, rule);
        }

        if (rule == null) {
            return invokeOriginal(template, method, args);
        }

        boolean isWrite = isWriteOperation(method);
        boolean isRead = isReadOperation(method);

        // asyncWrite 优先：配置了异步写的规则，忽略 template/pattern，直接异步执行
        if (isWrite && rule.isAsyncWrite()) {
            doAsyncWrite(template, method, args, rule);
            return null;
        }

        // 写操作 + writeIndexTemplate
        if (isWrite && StringUtils.hasText(rule.getEffectiveWriteIndexTemplate())) {
            String targetIndex = writeIndexResolver.resolveWriteIndex(rule);
            return doIndexRoute(template, method, args, targetIndex);
        }

        // 读操作 + readIndexPattern
        if (isRead && StringUtils.hasText(rule.getEffectiveReadIndexPattern())) {
            if (SimpleElasticsearchRouteConstant.METHOD_GET.equals(method.getName())
                    && args != null && args.length == 2 && args[1] instanceof Class) {
                return doGetBySearchRoute(template, args, rule);
            }
            return doSearchRoute(template, method, args, rule.getEffectiveReadIndexPattern());
        }

        return invokeOriginal(template, method, args);
    }

    /**
     * Spring Data Elasticsearch 3.x save(Object) 兼容：按路由结果直接调用 Elasticsearch HTTP 写入。
     */
    private Object doSaveForEs3x(ElasticsearchRestTemplate template, Method method,
                                 Object[] args, RouteRule rule) {
        Object doc = args[0];
        String targetIndex = writeIndexResolver.resolveWriteIndex(rule);
        ElasticsearchRestTemplate targetTemplate = template;

        if (rule != null && StringUtils.hasText(rule.getDatasource())) {
            ElasticsearchRestTemplate dsTemplate = templates.get(rule.getDatasource());
            if (dsTemplate != null) {
                targetTemplate = dsTemplate;
            }
        }

        final ElasticsearchRestTemplate finalTemplate = targetTemplate;
        final String finalTargetIndex = targetIndex;
        Runnable writeTask = () -> doIndexQueryForEs3x(finalTemplate, doc, finalTargetIndex);

        if (rule != null && rule.isAsyncWrite()) {
            String executorKey = StringUtils.hasText(rule.getDatasource()) ? rule.getDatasource() : SimpleElasticsearchRouteConstant.DEFAULT_ASYNC_WRITE_EXECUTOR_KEY;
            ExecutorService executor = asyncWriteExecutorMap.get(executorKey);
            if (executor == null) {
                log.warn("异步写线程池不存在 [{}]，执行同步写", executorKey);
                writeTask.run();
            } else {
                executor.execute(writeTask);
            }
            return null;
        }

        writeTask.run();
        return doc;
    }

    private Object doStringIndexForEs3x(ElasticsearchRestTemplate template, Method method, Object[] args) {
        String indexName = (String) args[0];
        try {
            RestClient client = getLowLevelClient(template);
            String path = SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + indexName;
            if (SimpleElasticsearchRouteConstant.METHOD_INDEX_EXISTS.equals(method.getName())) {
                int status = performJdkHttp(client, SimpleElasticsearchRouteConstant.HTTP_METHOD_GET, path, null);
                return status == SimpleElasticsearchRouteConstant.HTTP_STATUS_OK;
            }
            if (SimpleElasticsearchRouteConstant.METHOD_CREATE_INDEX.equals(method.getName())) {
                HttpResult response = performJdkHttpWithBody(client, SimpleElasticsearchRouteConstant.HTTP_METHOD_PUT, path, null);
                return isSuccessStatus(response.status)
                        || (response.status == SimpleElasticsearchRouteConstant.HTTP_STATUS_BAD_REQUEST
                        && response.body.contains(SimpleElasticsearchRouteConstant.ES_EXCEPTION_RESOURCE_ALREADY_EXISTS));
            }
            if (SimpleElasticsearchRouteConstant.METHOD_DELETE_INDEX.equals(method.getName())) {
                int status = performJdkHttp(client, SimpleElasticsearchRouteConstant.HTTP_METHOD_DELETE, path, null);
                return isSuccessStatus(status);
            }
            return invokeOriginal(template, method, args);
        } catch (Exception e) {
            throw routeException(String.format(ErrorMessage.ROUTE_ES3X_STRING_INDEX_FAILED, method.getName()), e);
        }
    }

    private Object doIndexForEs3x(ElasticsearchRestTemplate template, Object[] args, RouteRule rule, String routeIndex) {
        Object query = args[0];
        Object doc = getIndexQueryObject(query);
        String indexName = routeIndex;
        if (rule != null && StringUtils.hasText(rule.getEffectiveWriteIndexTemplate())) {
            indexName = writeIndexResolver.resolveWriteIndex(rule);
        }
        ElasticsearchRestTemplate targetTemplate = template;
        if (rule != null && StringUtils.hasText(rule.getDatasource())) {
            ElasticsearchRestTemplate dsTemplate = templates.get(rule.getDatasource());
            if (dsTemplate != null) {
                targetTemplate = dsTemplate;
            }
        }
        String queryId = getIndexQueryId(query);
        doIndexQueryForEs3x(targetTemplate, doc, indexName, queryId);
        return queryId;
    }

    private void doIndexQueryForEs3x(ElasticsearchRestTemplate template, Object doc, String targetIndex) {
        doIndexQueryForEs3x(template, doc, targetIndex, null);
    }

    private void doIndexQueryForEs3x(ElasticsearchRestTemplate template, Object doc, String targetIndex, String queryId) {
        try {
            String indexName = StringUtils.hasText(targetIndex) ? targetIndex : extractIndexFromDocument(doc.getClass());
            String id = StringUtils.hasText(queryId) ? queryId : (String) doc.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_GET_ID).invoke(doc);
            String json = writeJson(doc);
            int status = performJdkHttp(getLowLevelClient(template), SimpleElasticsearchRouteConstant.HTTP_METHOD_PUT,
                    SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + indexName + SimpleElasticsearchRouteConstant.ENDPOINT_DOC_TYPE + id, json);
            if (!isSuccessStatus(status)) {
                throw routeException(String.format(ErrorMessage.ROUTE_UNEXPECTED_INDEX_STATUS, status));
            }
        } catch (Exception e) {
            throw routeException(ErrorMessage.ROUTE_ES3X_INDEX_FAILED, e);
        }
    }

    private Object doGetForEs3x(ElasticsearchRestTemplate template, Method method,
                                Object[] args, RouteRule rule) {
        String id = (String) args[0];
        Class<?> clazz = (Class<?>) args[1];
        ElasticsearchRestTemplate targetTemplate = template;
        String indexName = extractIndexName(method, args);
        if (rule != null && StringUtils.hasText(rule.getDatasource())) {
            ElasticsearchRestTemplate dsTemplate = templates.get(rule.getDatasource());
            if (dsTemplate != null) {
                targetTemplate = dsTemplate;
            }
        }
        try {
            HttpResult response = performJdkHttpWithBody(getLowLevelClient(targetTemplate),
                    SimpleElasticsearchRouteConstant.HTTP_METHOD_GET,
                    SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + indexName + SimpleElasticsearchRouteConstant.ENDPOINT_DOC_TYPE + id, null);
            if (response.status == SimpleElasticsearchRouteConstant.HTTP_STATUS_NOT_FOUND) {
                return null;
            }
            if (!isSuccessStatus(response.status)) {
                throw routeException(String.format(ErrorMessage.ROUTE_UNEXPECTED_GET_STATUS, response.status));
            }
            return readSourceJson(response.body, clazz);
        } catch (Exception e) {
            throw routeException(ErrorMessage.ROUTE_ES3X_GET_FAILED, e);
        }
    }

    private Object doGetBySearchRoute(ElasticsearchRestTemplate template, Object[] args, RouteRule rule) {
        String id = (String) args[0];
        Class<?> clazz = (Class<?>) args[1];
        ElasticsearchRestTemplate targetTemplate = template;
        if (StringUtils.hasText(rule.getDatasource())) {
            ElasticsearchRestTemplate dsTemplate = templates.get(rule.getDatasource());
            if (dsTemplate != null) {
                targetTemplate = dsTemplate;
            }
        }
        try {
            String query = buildIdsQueryJson(id);
            HttpResult response = performJdkHttpWithBody(getLowLevelClient(targetTemplate),
                    SimpleElasticsearchRouteConstant.HTTP_METHOD_POST,
                    SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + rule.getEffectiveReadIndexPattern()
                            + SimpleElasticsearchRouteConstant.ENDPOINT_SEARCH, query);
            if (!isSuccessStatus(response.status)) {
                throw routeException(String.format(ErrorMessage.ROUTE_UNEXPECTED_SEARCH_STATUS, response.status));
            }
            return readFirstHitSourceJson(response.body, clazz);
        } catch (Exception e) {
            throw routeException(ErrorMessage.ROUTE_ES3X_GET_FAILED, e);
        }
    }

    /**
     * 根据 indexName 确定要使用的 template（CGLIB 代理调用时使用）
     */
    ElasticsearchRestTemplate determineTemplate(String indexName) {
        String dataSourceKey = routeResolver.resolveDataSource(indexName);
        ElasticsearchRestTemplate template = templates.get(dataSourceKey);
        if (template == null) {
            log.warn("索引 [{}] 对应的数据源 [{}] 不存在，使用默认 template",
                    indexName, dataSourceKey);
            return defaultTemplate;
        }
        log.trace("索引 [{}] 路由到数据源 [{}]", indexName, dataSourceKey);
        return template;
    }

    /**
     * 写操作判定
     */
    boolean isWriteOperation(Method method) {
        return WRITE_METHODS.contains(method.getName());
    }

    /**
     * 读操作判定
     */
    boolean isReadOperation(Method method) {
        return READ_METHODS.contains(method.getName());
    }

    /**
     * 从方法参数中提取索引名称（CGLIB 代理调用时需要）
     */
    String extractIndexName(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        if (STRING_INDEX_METHODS.contains(method.getName()) && args[0] instanceof String) {
            return (String) args[0];
        }
        for (IndexNameExtractor extractor : indexNameExtractors) {
            String indexName = extractor.extract(method, args);
            if (indexName != null) {
                return indexName;
            }
        }
        log.trace("方法 [{}] 的参数未提取到索引名", method.getName());
        return null;
    }

    /**
     * 异步写：主线程渲染模板后丢线程池，立即返回 null
     */
    private void doAsyncWrite(ElasticsearchRestTemplate template, Method method,
                              Object[] args, RouteRule rule) {
        String targetIndex = writeIndexResolver.resolveWriteIndex(rule);
        String datasourceKey = rule.getDatasource();
        String executorKey = StringUtils.hasText(datasourceKey) ? datasourceKey : SimpleElasticsearchRouteConstant.DEFAULT_ASYNC_WRITE_EXECUTOR_KEY;
        ExecutorService executor = asyncWriteExecutorMap.get(executorKey);
        if (executor == null) {
            log.warn("异步写线程池不存在 [{}]，执行同步写", executorKey);
            if (StringUtils.hasText(targetIndex)) {
                doIndexRoute(template, method, args, targetIndex);
            } else {
                invokeOriginal(template, method, args);
            }
            return;
        }
        executor.execute(() -> {
            try {
                if (StringUtils.hasText(targetIndex)) {
                    doIndexRoute(template, method, args, targetIndex);
                } else {
                    invokeOriginal(template, method, args);
                }
            } catch (Exception e) {
                log.error("异步写失败，method=[{}]，targetIndex=[{}]", method.getName(), targetIndex, e);
            }
        });
    }

    /**
     * 写操作索引路由：动态查找 IndexCoordinates 重载
     */
    Object doIndexRoute(ElasticsearchRestTemplate template, Method method,
                        Object[] args, String targetIndexName) {
        if (INDEX_COORDINATES_CLASS == null) {
            // Spring Data Elasticsearch 3.x 没有 IndexCoordinates API，保持原始调用。
            return invokeOriginal(template, method, args);
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] invokeArgs;
        if (paramTypes.length > 0 && paramTypes[paramTypes.length - 1] == INDEX_COORDINATES_CLASS) {
            // 最后一个参数已经是 IndexCoordinates，替换它的值
            invokeArgs = Arrays.copyOf(args, args.length);
            invokeArgs[args.length - 1] = createIndexCoordinates(targetIndexName);
        } else {
            // 追加 IndexCoordinates 参数
            invokeArgs = new Object[args.length + 1];
            System.arraycopy(args, 0, invokeArgs, 0, args.length);
            invokeArgs[args.length] = createIndexCoordinates(targetIndexName);
            Class<?>[] newParamTypes = new Class[paramTypes.length + 1];
            System.arraycopy(paramTypes, 0, newParamTypes, 0, paramTypes.length);
            newParamTypes[paramTypes.length] = INDEX_COORDINATES_CLASS;
            paramTypes = newParamTypes;
        }
        try {
            Method targetMethod = template.getClass().getMethod(method.getName(), paramTypes);
            return invokeReflect(targetMethod, template, invokeArgs);
        } catch (NoSuchMethodException e) {
            log.warn("方法 [{}] 的 IndexCoordinates 重载在当前版本不存在，已跳过索引路由替换。"
                    + "请确认 spring-data-elasticsearch 版本。", method.getName());
            return invokeOriginal(template, method, args);
        }
    }

    /**
     * 读操作索引路由：动态查找 IndexCoordinates 重载
     */
    Object doSearchRoute(ElasticsearchRestTemplate template, Method method,
                         Object[] args, String targetIndexPattern) {
        if (INDEX_COORDINATES_CLASS == null) {
            // Spring Data Elasticsearch 3.x 没有 IndexCoordinates API，保持原始调用。
            return invokeOriginal(template, method, args);
        }
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] invokeArgs;
        if (paramTypes.length > 0 && paramTypes[paramTypes.length - 1] == INDEX_COORDINATES_CLASS) {
            // 最后一个参数已经是 IndexCoordinates，替换它的值
            invokeArgs = Arrays.copyOf(args, args.length);
            invokeArgs[args.length - 1] = createIndexCoordinates(targetIndexPattern);
        } else {
            // 追加 IndexCoordinates 参数
            Class<?>[] newParamTypes = new Class[paramTypes.length + 1];
            System.arraycopy(paramTypes, 0, newParamTypes, 0, paramTypes.length);
            newParamTypes[paramTypes.length] = INDEX_COORDINATES_CLASS;
            invokeArgs = new Object[args.length + 1];
            System.arraycopy(args, 0, invokeArgs, 0, args.length);
            invokeArgs[args.length] = createIndexCoordinates(targetIndexPattern);
            paramTypes = newParamTypes;
        }
        try {
            Method targetMethod = template.getClass().getMethod(method.getName(), paramTypes);
            return invokeReflect(targetMethod, template, invokeArgs);
        } catch (NoSuchMethodException e) {
            log.warn("读方法 [{}] 的 IndexCoordinates 重载在当前版本不存在，已跳过索引路由替换。"
                    + "请确认 spring-data-elasticsearch 版本。", method.getName());
            return invokeOriginal(template, method, args);
        }
    }

    private RestClient getLowLevelClient(ElasticsearchRestTemplate template) throws Exception {
        RestHighLevelClient highLevelClient = getHighLevelClient(template);
        return highLevelClient.getLowLevelClient();
    }

    private RestHighLevelClient getHighLevelClient(ElasticsearchRestTemplate template) throws Exception {
        try {
            Method getClient = template.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_GET_CLIENT);
            return (RestHighLevelClient) getClient.invoke(template);
        } catch (NoSuchMethodException ignored) {
            Field clientField = findField(template.getClass(), SimpleElasticsearchRouteConstant.FIELD_CLIENT);
            clientField.setAccessible(true);
            return (RestHighLevelClient) clientField.get(template);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private boolean isSuccessStatus(int status) {
        return status >= SimpleElasticsearchRouteConstant.HTTP_STATUS_OK
                && status < SimpleElasticsearchRouteConstant.HTTP_STATUS_REDIRECT_MIN;
    }

    private int performJdkHttp(RestClient client, String method, String path, String body) throws Exception {
        return performJdkHttpWithBody(client, method, path, body).status;
    }

    private HttpResult performJdkHttpWithBody(RestClient client, String method, String path, String body) throws Exception {
        // 旧版 RestClient 访问新版服务端时可能因 warning header 抛错，3.x 兼容分支绕开该限制。
        Object node = client.getNodes().get(0);
        Method getHost = node.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_GET_HOST);
        Object host = getHost.invoke(node);
        Method toURI = host.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_TO_URI);
        URL url = new URL(toURI.invoke(host).toString() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(SimpleElasticsearchRouteConstant.ES3X_COMPAT_HTTP_TIMEOUT_MS);
        connection.setReadTimeout(SimpleElasticsearchRouteConstant.ES3X_COMPAT_HTTP_TIMEOUT_MS);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty(SimpleElasticsearchRouteConstant.HTTP_HEADER_CONTENT_TYPE, SimpleElasticsearchRouteConstant.CONTENT_TYPE_APPLICATION_JSON);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            OutputStream out = connection.getOutputStream();
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
        }
        try {
            int status = connection.getResponseCode();
            InputStream input = status >= SimpleElasticsearchRouteConstant.HTTP_STATUS_BAD_REQUEST ? connection.getErrorStream() : connection.getInputStream();
            return new HttpResult(status, input == null ? "" : readAll(input));
        } finally {
            connection.disconnect();
        }
    }

    private String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[SimpleElasticsearchRouteConstant.HTTP_READ_BUFFER_SIZE];
        int len;
        while ((len = input.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static class HttpResult {
        private final int status;
        private final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private Object getIndexQueryObject(Object query) {
        try {
            Method getObject = query.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_GET_OBJECT);
            return getObject.invoke(query);
        } catch (Exception e) {
            throw routeException(ErrorMessage.ROUTE_INDEX_QUERY_OBJECT_FAILED, e);
        }
    }

    private String getIndexQueryId(Object query) {
        try {
            Method getId = query.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_GET_ID);
            return (String) getId.invoke(query);
        } catch (Exception e) {
            throw routeException(ErrorMessage.ROUTE_INDEX_QUERY_ID_FAILED, e);
        }
    }

    private String extractIndexFromDocument(Class<?> clazz) {
        org.springframework.data.elasticsearch.annotations.Document document =
                clazz.getAnnotation(org.springframework.data.elasticsearch.annotations.Document.class);
        if (document == null || !StringUtils.hasText(document.indexName())) {
            throw routeException(String.format(ErrorMessage.ROUTE_DOCUMENT_INDEX_NAME_NOT_FOUND, clazz.getName()));
        }
        return document.indexName();
    }

    private String writeJson(Object doc) throws Exception {
        Object mapper = createObjectMapper();
        Method writeValueAsString = mapper.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_WRITE_VALUE_AS_STRING, Object.class);
        return (String) writeValueAsString.invoke(mapper, doc);
    }

    private String buildIdsQueryJson(String id) throws Exception {
        Map<String, Object> ids = new LinkedHashMap<>();
        ids.put(SimpleElasticsearchRouteConstant.JSON_FIELD_VALUES, Collections.singletonList(id));
        Map<String, Object> query = new LinkedHashMap<>();
        query.put(SimpleElasticsearchRouteConstant.JSON_FIELD_IDS, ids);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put(SimpleElasticsearchRouteConstant.JSON_FIELD_QUERY, query);
        return writeJson(root);
    }

    private Object createObjectMapper() throws Exception {
        Object mapper = Class.forName(SimpleElasticsearchRouteConstant.CLASS_JACKSON_OBJECT_MAPPER)
                .getConstructor().newInstance();
        Class<?> featureClass = Class.forName(SimpleElasticsearchRouteConstant.CLASS_JACKSON_DESERIALIZATION_FEATURE);
        Object failOnUnknown = featureClass.getField(SimpleElasticsearchRouteConstant.FIELD_FAIL_ON_UNKNOWN_PROPERTIES)
                .get(null);
        Method configure = mapper.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_CONFIGURE,
                featureClass, boolean.class);
        configure.invoke(mapper, failOnUnknown, false);
        return mapper;
    }

    private Object readSourceJson(String responseBody, Class<?> clazz) throws Exception {
        Object mapper = createObjectMapper();
        Method readTree = mapper.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_READ_TREE, String.class);
        Object rootNode = readTree.invoke(mapper, responseBody);
        Method get = rootNode.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_GET, String.class);
        Object sourceNode = get.invoke(rootNode, SimpleElasticsearchRouteConstant.JSON_FIELD_SOURCE);
        return treeToValue(mapper, sourceNode, clazz);
    }

    private Object readFirstHitSourceJson(String responseBody, Class<?> clazz) throws Exception {
        Object mapper = createObjectMapper();
        Method readTree = mapper.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_READ_TREE, String.class);
        Object rootNode = readTree.invoke(mapper, responseBody);
        Method get = rootNode.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_GET, String.class);
        Object hitsNode = get.invoke(rootNode, SimpleElasticsearchRouteConstant.JSON_FIELD_HITS);
        if (hitsNode == null) {
            return null;
        }
        Object innerHitsNode = get.invoke(hitsNode, SimpleElasticsearchRouteConstant.JSON_FIELD_HITS);
        if (innerHitsNode == null) {
            return null;
        }
        Method size = innerHitsNode.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_SIZE);
        if ((Integer) size.invoke(innerHitsNode) == 0) {
            return null;
        }
        Method getIndex = innerHitsNode.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_GET, int.class);
        Object firstHitNode = getIndex.invoke(innerHitsNode, 0);
        Object sourceNode = get.invoke(firstHitNode, SimpleElasticsearchRouteConstant.JSON_FIELD_SOURCE);
        return treeToValue(mapper, sourceNode, clazz);
    }

    private Object treeToValue(Object mapper, Object sourceNode, Class<?> clazz) throws Exception {
        if (sourceNode == null) {
            return null;
        }
        Method treeToValue = mapper.getClass().getMethod(SimpleElasticsearchRouteConstant.METHOD_TREE_TO_VALUE,
                Class.forName(SimpleElasticsearchRouteConstant.CLASS_JACKSON_TREE_NODE), Class.class);
        return treeToValue.invoke(mapper, sourceNode, clazz);
    }

    private RouteException routeException(String message) {
        return new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE, message);
    }

    private RouteException routeException(String message, Throwable cause) {
        if (cause instanceof RouteException) {
            return (RouteException) cause;
        }
        return new RouteException(ErrorCode.ROUTE_TEMPLATE_UNAVAILABLE, message, cause);
    }

    /**
     * 反射创建 IndexCoordinates 实例（Spring Data Elasticsearch 4.x API）。
     */
    private Object createIndexCoordinates(String indexName) {
        try {
            return INDEX_COORDINATES_OF.invoke(null, (Object) new String[]{indexName});
        } catch (Exception e) {
            throw routeException(String.format(ErrorMessage.ROUTE_INDEX_COORDINATES_CREATE_FAILED, indexName), e);
        }
    }

    /**
     * 反射调用，解包 InvocationTargetException
     */
    private Object invokeReflect(Method targetMethod, Object target, Object[] args) {
        try {
            if (!targetMethod.isAccessible()) {
                targetMethod.setAccessible(true);
            }
            return targetMethod.invoke(target, args);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getTargetException();
            if (cause instanceof SimpleElasticsearchRouteException) {
                throw (SimpleElasticsearchRouteException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw routeException(cause.getMessage(), cause);
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw routeException(ErrorMessage.ROUTE_REFLECTION_INVOKE_FAILED, cause);
        } catch (Exception e) {
            log.error("反射调用 ElasticsearchRestTemplate 方法失败，method=[{}]", targetMethod.getName(), e);
            throw routeException(ErrorMessage.ROUTE_REFLECTION_INVOKE_FAILED, e);
        }
    }

    /**
     * 执行原始调用（无路由替换）
     */
    Object invokeOriginal(ElasticsearchRestTemplate template, Method method, Object[] args) {
        Method targetMethod = resolveTargetMethod(template, method);
        return invokeReflect(targetMethod, template, args);
    }

    private Method resolveTargetMethod(ElasticsearchRestTemplate template, Method method) {
        try {
            return template.getClass().getMethod(method.getName(), method.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return method;
        }
    }
}

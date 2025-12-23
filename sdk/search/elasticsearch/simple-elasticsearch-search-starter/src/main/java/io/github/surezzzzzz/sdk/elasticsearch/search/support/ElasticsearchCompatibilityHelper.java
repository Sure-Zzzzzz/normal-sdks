package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ElasticsearchApiConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;

/**
 * Elasticsearch 版本兼容性工具类
 *
 * <p>用于处理不同 Elasticsearch 版本之间的兼容性问题，包括：
 * <ul>
 *   <li>ES 6.x vs ES 7.x+ 的 API 差异</li>
 *   <li>XContent API 的包路径差异（org.elasticsearch.xcontent vs org.elasticsearch.common.xcontent）</li>
 *   <li>自动检测版本并选择合适的 API（高级 API vs 低级 API）</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
public class ElasticsearchCompatibilityHelper {

    /**
     * ES 6.x 聚合响应异常
     * <p>包含原始 JSON，供调用方手动解析
     */
    public static class Es6xAggregationResponseException extends IOException {
        private final String responseJson;

        public Es6xAggregationResponseException(String responseJson) {
            super("ES 6.x aggregation response cannot be parsed by ES 7.x client, raw JSON included");
            this.responseJson = responseJson;
        }

        public String getResponseJson() {
            return responseJson;
        }
    }

    /**
     * 当前环境的 XContent 包路径（延迟检测）
     */
    private static volatile String detectedXContentPackage = null;

    /**
     * NamedXContentRegistry 缓存（包含聚合解析器）
     */
    private static volatile Object namedXContentRegistry = null;

    /**
     * 检测当前环境的 XContent API 包路径
     */
    private static String detectXContentPackage() {
        if (detectedXContentPackage != null) {
            return detectedXContentPackage;
        }

        synchronized (ElasticsearchCompatibilityHelper.class) {
            if (detectedXContentPackage != null) {
                return detectedXContentPackage;
            }

            // 优先尝试 ES 7.x+ 包路径
            try {
                Class.forName(SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES7 + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_TYPE);
                detectedXContentPackage = SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES7;
                log.info("Detected Elasticsearch client XContent API: {} (ES 7.x+)", SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES7);
                return detectedXContentPackage;
            } catch (ClassNotFoundException e) {
                // 降级到 ES 6.x 包路径
                try {
                    Class.forName(SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES6 + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_TYPE);
                    detectedXContentPackage = SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES6;
                    log.info("Detected Elasticsearch client XContent API: {} (ES 6.x)", SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES6);
                    return detectedXContentPackage;
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException("Cannot find compatible XContent API classes. " +
                            "Please check your Elasticsearch client version.", ex);
                }
            }
        }
    }

    /**
     * 获取包含聚合解析器的 NamedXContentRegistry
     * 尝试从 SearchModule 获取，失败则降级到默认方式
     */
    private static Object getNamedXContentRegistry() {
        if (namedXContentRegistry != null) {
            return namedXContentRegistry;
        }

        synchronized (ElasticsearchCompatibilityHelper.class) {
            if (namedXContentRegistry != null) {
                return namedXContentRegistry;
            }

            try {
                String xContentPackage = detectXContentPackage();
                Class<?> namedXContentRegistryClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_REGISTRY);

                // 方法1：尝试使用 SearchModule 创建（ES 7.x 推荐）
                try {
                    Class<?> searchModuleClass = Class.forName(SimpleElasticsearchSearchConstant.ES_CLASS_SEARCH_MODULE);
                    Class<?> settingsClass = Class.forName(SimpleElasticsearchSearchConstant.ES_CLASS_SETTINGS);

                    // 获取 Settings.EMPTY
                    Object emptySettings = settingsClass.getField(SimpleElasticsearchSearchConstant.FIELD_EMPTY).get(null);

                    // 尝试多种构造函数签名
                    Object searchModule = null;

                    // 尝试1: SearchModule(Settings, List<SearchPlugin>)
                    try {
                        java.lang.reflect.Constructor<?> constructor = searchModuleClass.getConstructor(
                                settingsClass, java.util.List.class);
                        searchModule = constructor.newInstance(emptySettings, java.util.Collections.emptyList());
                        log.debug("Created SearchModule with (Settings, List) constructor");
                    } catch (NoSuchMethodException e) {
                        log.debug("SearchModule(Settings, List) constructor not found");
                    }

                    // 尝试2: SearchModule(Settings, boolean, List<SearchPlugin>)
                    if (searchModule == null) {
                        try {
                            java.lang.reflect.Constructor<?> constructor = searchModuleClass.getConstructor(
                                    settingsClass, boolean.class, java.util.List.class);
                            searchModule = constructor.newInstance(emptySettings, false, java.util.Collections.emptyList());
                            log.debug("Created SearchModule with (Settings, boolean, List) constructor");
                        } catch (NoSuchMethodException e) {
                            log.debug("SearchModule(Settings, boolean, List) constructor not found");
                        }
                    }

                    if (searchModule == null) {
                        throw new Exception("No compatible SearchModule constructor found");
                    }

                    // 调用 searchModule.getNamedXContents()
                    java.lang.reflect.Method getNamedXContentsMethod = searchModuleClass.getMethod(SimpleElasticsearchSearchConstant.METHOD_GET_NAMED_XCONTENTS);
                    java.util.List<?> namedXContents = (java.util.List<?>) getNamedXContentsMethod.invoke(searchModule);

                    // 创建 NamedXContentRegistry
                    java.lang.reflect.Constructor<?> registryConstructor = namedXContentRegistryClass.getConstructor(java.util.List.class);
                    namedXContentRegistry = registryConstructor.newInstance(namedXContents);

                    log.info("✓ Created NamedXContentRegistry with SearchModule ({} named XContent entries)",
                            namedXContents != null ? namedXContents.size() : 0);
                    return namedXContentRegistry;

                } catch (Exception e) {
                    log.warn("Failed to create NamedXContentRegistry with SearchModule: {}. " +
                            "Will use EMPTY registry - aggregation parsing may fail.", e.getMessage());
                    log.debug("SearchModule creation exception details", e);
                }

                // 方法2：使用默认的 EMPTY（兼容性最强，但不支持聚合解析）
                log.warn("⚠ Using NamedXContentRegistry.EMPTY - aggregation parsing will NOT work correctly. " +
                        "This is a known limitation when using low-level API with older ES versions.");
                namedXContentRegistry = namedXContentRegistryClass.getField(SimpleElasticsearchSearchConstant.FIELD_EMPTY).get(null);
                return namedXContentRegistry;

            } catch (Exception e) {
                throw new IllegalStateException("Cannot create NamedXContentRegistry", e);
            }
        }
    }


    /**
     * 执行查询（版本兼容）
     * 对于 ES 6.x，使用低级 API 绕过 ignore_throttled 等参数兼容性问题
     *
     * @param client        RestHighLevelClient
     * @param datasourceKey 数据源标识
     * @param searchRequest 搜索请求
     * @param registry      数据源注册中心
     * @return SearchResponse
     * @throws IOException IO异常
     */
    public static SearchResponse executeSearch(
            RestHighLevelClient client,
            String datasourceKey,
            SearchRequest searchRequest,
            SimpleElasticsearchRouteRegistry registry) throws IOException {

        // 获取集群信息，检查版本
        ClusterInfo clusterInfo = registry.getClusterInfo(datasourceKey);
        boolean isEs6x = false;
        boolean versionUnknown = false;

        if (clusterInfo != null && clusterInfo.getEffectiveVersion() != null) {
            int majorVersion = clusterInfo.getEffectiveVersion().getMajor();
            isEs6x = (majorVersion == 6);
            log.debug("Detected ES major version: {} for datasource [{}]", majorVersion, datasourceKey);
        } else {
            versionUnknown = true;
            log.debug("Datasource [{}] version not yet detected, will try high-level API first", datasourceKey);
        }

        if (isEs6x) {
            // ES 6.x：使用低级 API，避免 ignore_throttled 等参数问题
            log.debug("Using low-level API for ES 6.x compatibility");
            return executeSearchViaLowLevelApi(client, searchRequest);
        } else {
            // ES 7.x+ 或版本未知：尝试使用高级 API
            try {
                return client.search(searchRequest, RequestOptions.DEFAULT);
            } catch (org.elasticsearch.ElasticsearchStatusException e) {
                // 如果是 ES 6.x 参数不兼容错误，fallback 到低级 API
                if (versionUnknown && e.getMessage() != null &&
                        e.getMessage().contains(SimpleElasticsearchSearchConstant.ES_ERROR_UNRECOGNIZED_PARAMETER)) {
                    log.warn("High-level API failed with ES 6.x compatibility issue, falling back to low-level API for datasource [{}]", datasourceKey);
                    return executeSearchViaLowLevelApi(client, searchRequest);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * 使用低级 API 执行查询（ES 6.x 兼容）
     * 绕过高级 API 自动添加的 ignore_throttled、master_timeout 等参数
     *
     * @param highLevelClient RestHighLevelClient
     * @param searchRequest   搜索请求
     * @return SearchResponse
     * @throws IOException IO异常
     */
    private static SearchResponse executeSearchViaLowLevelApi(
            RestHighLevelClient highLevelClient,
            SearchRequest searchRequest) throws IOException {

        org.elasticsearch.client.RestClient lowLevelClient = highLevelClient.getLowLevelClient();

        // 构造请求：POST /<index>/_search
        String indices = String.join(",", searchRequest.indices());
        String endpoint = ElasticsearchApiConstant.ENDPOINT_ROOT + indices + ElasticsearchApiConstant.ENDPOINT_SEARCH;

        org.elasticsearch.client.Request request = new org.elasticsearch.client.Request(
                ElasticsearchApiConstant.HTTP_METHOD_POST,
                endpoint
        );

        // 设置请求体（SearchSourceBuilder 的 JSON）
        if (searchRequest.source() != null) {
            request.setJsonEntity(searchRequest.source().toString());
        }

        // 执行请求
        org.elasticsearch.client.Response response = lowLevelClient.performRequest(request);

        byte[] responseBytes;
        try (java.io.InputStream inputStream = response.getEntity().getContent();
             java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = inputStream.read(data)) != -1) {
                buffer.write(data, 0, nRead);
            }
            responseBytes = buffer.toByteArray();
        }

        // DEBUG: 输出原始响应，帮助诊断解析问题
        if (log.isDebugEnabled()) {
            try {
                String responseJson = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
                log.debug("ES response JSON (first 500 chars): {}",
                        responseJson.length() > 500 ? responseJson.substring(0, 500) + "..." : responseJson);
            } catch (Exception e) {
                log.debug("Failed to log response JSON", e);
            }
        }

        // **修改**：检查是否包含聚合
        // ES 7.x 客户端无法正确解析 ES 6.x 的聚合响应，且高级 API 会添加 ignore_throttled 参数导致请求失败
        // 解决方案：抛出特殊异常，让调用方手动解析 JSON
        String responseJson = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
        if (responseJson.contains("\"" + SimpleElasticsearchSearchConstant.ES_JSON_AGGREGATIONS + "\"")) {
            log.debug("Detected aggregations in ES 6.x response, will use manual JSON parsing");
            throw new Es6xAggregationResponseException(responseJson);
        }

        // 使用当前环境检测到的 XContent 包路径解析响应
        String xContentPackage = detectXContentPackage();
        return parseSearchResponseWithXContent(responseBytes, xContentPackage);
    }

    /**
     * 创建 XContentParser（公共逻辑抽取）
     *
     * @param inputStream     输入流
     * @param xContentPackage XContent API 包路径
     * @return XContentParser 对象
     * @throws ReflectiveOperationException 反射异常
     * @throws java.io.IOException          IO异常
     */
    private static Object createXContentParser(
            java.io.InputStream inputStream,
            String xContentPackage) throws ReflectiveOperationException, java.io.IOException {

        // 动态加载类（版本兼容）
        Class<?> xContentTypeClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_TYPE);
        Class<?> xContentFactoryClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_FACTORY);
        Class<?> namedXContentRegistryClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_REGISTRY);
        Class<?> deprecationHandlerClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_DEPRECATION_HANDLER);

        // 获取 JSON 类型常量
        Object jsonType = xContentTypeClass.getField(SimpleElasticsearchSearchConstant.FIELD_JSON).get(null);

        // 调用 XContentFactory.xContent(XContentType.JSON)
        java.lang.reflect.Method xContentMethod = xContentFactoryClass.getMethod(SimpleElasticsearchSearchConstant.METHOD_XCONTENT, xContentTypeClass);
        Object xContent = xContentMethod.invoke(null, jsonType);

        // 获取包含聚合解析器的 NamedXContentRegistry（而不是 EMPTY）
        Object registry = getNamedXContentRegistry();

        // 获取 DeprecationHandler.THROW_UNSUPPORTED_OPERATION
        Object throwUnsupportedOperation = deprecationHandlerClass.getField(SimpleElasticsearchSearchConstant.FIELD_THROW_UNSUPPORTED_OPERATION).get(null);

        // 调用 xContent.createParser(registry, handler, inputStream)
        java.lang.reflect.Method createParserMethod = xContent.getClass().getMethod(
                SimpleElasticsearchSearchConstant.METHOD_CREATE_PARSER,
                namedXContentRegistryClass,
                deprecationHandlerClass,
                java.io.InputStream.class
        );
        return createParserMethod.invoke(
                xContent,
                registry,
                throwUnsupportedOperation,
                inputStream
        );
    }

    /**
     * 安全关闭 XContentParser
     *
     * @param parser XContentParser 对象
     */
    private static void closeParser(Object parser) {
        if (parser instanceof AutoCloseable) {
            try {
                ((AutoCloseable) parser).close();
            } catch (Exception e) {
                log.warn("Failed to close XContentParser", e);
            }
        }
    }

    /**
     * 使用指定包路径的 XContent API 解析 SearchResponse
     *
     * @param responseBytes   HTTP响应体字节数组
     * @param xContentPackage XContent API 包路径
     * @return SearchResponse
     * @throws IOException IO异常
     */
    private static SearchResponse parseSearchResponseWithXContent(
            byte[] responseBytes,
            String xContentPackage) throws IOException {

        Object parser = null;
        try {
            // 从字节数组创建新的 InputStream
            java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(responseBytes);

            // 创建 parser
            parser = createXContentParser(inputStream, xContentPackage);

            // 获取 XContentParser 类
            Class<?> xContentParserClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_PARSER);

            // 调用 SearchResponse.fromXContent(parser)
            java.lang.reflect.Method fromXContentMethod = SearchResponse.class.getMethod(
                    SimpleElasticsearchSearchConstant.METHOD_FROM_XCONTENT,
                    xContentParserClass
            );
            return (SearchResponse) fromXContentMethod.invoke(null, parser);

        } catch (ClassNotFoundException e) {
            throw new IOException("XContent API class not found: " + xContentPackage, e);
        } catch (ReflectiveOperationException e) {
            // 获取真正的异常原因（可能在 InvocationTargetException 的 cause 中）
            Throwable rootCause = e.getCause() != null ? e.getCause() : e;
            throw new IOException("Failed to parse search response using XContent API: " + xContentPackage +
                    ". Exception: " + e.getClass().getSimpleName() +
                    ", Root cause: " + rootCause.getClass().getSimpleName() +
                    ", Message: " + rootCause.getMessage(), e);
        } finally {
            closeParser(parser);
        }
    }

    /**
     * 通用 XContent 响应解析方法（支持多种响应类型）
     * 适用于 GetMappingsResponse, SearchResponse 等
     *
     * @param response      HTTP 响应
     * @param responseClass 目标响应类型
     * @param <T>           响应类型泛型
     * @return 解析后的响应对象
     * @throws IOException IO异常
     */
    public static <T> T parseResponse(
            org.elasticsearch.client.Response response,
            Class<T> responseClass) throws IOException {

        // 使用当前环境检测到的 XContent 包路径
        String xContentPackage = detectXContentPackage();

        Object parser = null;
        try {
            // 创建 parser
            parser = createXContentParser(response.getEntity().getContent(), xContentPackage);

            // 获取 XContentParser 类
            Class<?> xContentParserClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_PARSER);

            // 调用 ResponseClass.fromXContent(parser)
            java.lang.reflect.Method fromXContentMethod = responseClass.getMethod(
                    SimpleElasticsearchSearchConstant.METHOD_FROM_XCONTENT,
                    xContentParserClass
            );
            @SuppressWarnings("unchecked")
            T result = (T) fromXContentMethod.invoke(null, parser);

            return result;

        } catch (ClassNotFoundException e) {
            throw new IOException("XContent API class not found: " + xContentPackage, e);
        } catch (ReflectiveOperationException e) {
            // 获取真正的异常原因
            Throwable rootCause = e.getCause() != null ? e.getCause() : e;
            throw new IOException("Failed to parse " + responseClass.getSimpleName() + " using XContent API: " + xContentPackage +
                    ". Exception: " + e.getClass().getSimpleName() +
                    ", Root cause: " + rootCause.getClass().getSimpleName() +
                    ", Message: " + rootCause.getMessage(), e);
        } finally {
            closeParser(parser);
        }
    }
}

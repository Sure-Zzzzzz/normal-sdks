package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchResponse;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * XContent API 反射工具
 * 隔离所有反射操作，处理 ES 6.x/7.x XContent 包路径差异
 * 所有方法均为 static，内部使用 volatile + double-checked locking 缓存
 *
 * @author surezzzzzz
 */
@Slf4j
public final class XContentReflectionHelper {

    private static volatile String detectedXContentPackage = null;
    private static volatile Object namedXContentRegistry = null;

    private XContentReflectionHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 检测当前环境的 XContent API 包路径
     *
     * @return 包路径（org.elasticsearch.xcontent 或 org.elasticsearch.common.xcontent）
     */
    public static String detectXContentPackage() {
        if (detectedXContentPackage != null) {
            return detectedXContentPackage;
        }
        synchronized (XContentReflectionHelper.class) {
            if (detectedXContentPackage != null) {
                return detectedXContentPackage;
            }
            try {
                Class.forName(SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES7 + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_TYPE);
                detectedXContentPackage = SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES7;
                log.info("Detected Elasticsearch client XContent API: {} (ES 7.x+)", SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES7);
            } catch (ClassNotFoundException e) {
                try {
                    Class.forName(SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES6 + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_TYPE);
                    detectedXContentPackage = SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES6;
                    log.info("Detected Elasticsearch client XContent API: {} (ES 6.x)", SimpleElasticsearchSearchConstant.XCONTENT_PACKAGE_ES6);
                } catch (ClassNotFoundException ex) {
                    throw new IllegalStateException("Cannot find compatible XContent API classes. " +
                            "Please check your Elasticsearch client version.", ex);
                }
            }
            return detectedXContentPackage;
        }
    }

    /**
     * 获取包含聚合解析器的 NamedXContentRegistry
     *
     * @return NamedXContentRegistry 对象
     */
    public static Object getNamedXContentRegistry() {
        if (namedXContentRegistry != null) {
            return namedXContentRegistry;
        }
        synchronized (XContentReflectionHelper.class) {
            if (namedXContentRegistry != null) {
                return namedXContentRegistry;
            }
            try {
                String xContentPackage = detectXContentPackage();
                Class<?> namedXContentRegistryClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_REGISTRY);

                try {
                    Class<?> searchModuleClass = Class.forName(SimpleElasticsearchSearchConstant.ES_CLASS_SEARCH_MODULE);
                    Class<?> settingsClass = Class.forName(SimpleElasticsearchSearchConstant.ES_CLASS_SETTINGS);
                    Object emptySettings = settingsClass.getField(SimpleElasticsearchSearchConstant.FIELD_EMPTY).get(null);

                    Object searchModule = null;
                    try {
                        Constructor<?> ctor = searchModuleClass.getConstructor(settingsClass, List.class);
                        searchModule = ctor.newInstance(emptySettings, Collections.emptyList());
                        log.debug("Created SearchModule with (Settings, List) constructor");
                    } catch (NoSuchMethodException e) {
                        log.debug("SearchModule(Settings, List) constructor not found");
                    }
                    if (searchModule == null) {
                        try {
                            Constructor<?> ctor = searchModuleClass.getConstructor(settingsClass, boolean.class, List.class);
                            searchModule = ctor.newInstance(emptySettings, false, Collections.emptyList());
                            log.debug("Created SearchModule with (Settings, boolean, List) constructor");
                        } catch (NoSuchMethodException e) {
                            log.debug("SearchModule(Settings, boolean, List) constructor not found");
                        }
                    }
                    if (searchModule == null) {
                        throw new Exception("No compatible SearchModule constructor found");
                    }

                    Method getNamedXContentsMethod = searchModuleClass.getMethod(SimpleElasticsearchSearchConstant.METHOD_GET_NAMED_XCONTENTS);
                    List<?> namedXContents = (List<?>) getNamedXContentsMethod.invoke(searchModule);

                    Constructor<?> registryConstructor = namedXContentRegistryClass.getConstructor(List.class);
                    namedXContentRegistry = registryConstructor.newInstance(namedXContents);

                    log.info("Created NamedXContentRegistry with SearchModule ({} named XContent entries)",
                            namedXContents != null ? namedXContents.size() : 0);
                } catch (Exception e) {
                    log.warn("Failed to create NamedXContentRegistry with SearchModule: {}. " +
                            "Will use EMPTY registry - aggregation parsing may fail.", e.getMessage());
                    namedXContentRegistry = namedXContentRegistryClass.getField(SimpleElasticsearchSearchConstant.FIELD_EMPTY).get(null);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot create NamedXContentRegistry", e);
            }
            return namedXContentRegistry;
        }
    }

    /**
     * 创建 XContentParser
     *
     * @param inputStream     输入流
     * @param xContentPackage XContent API 包路径
     * @return XContentParser 对象
     * @throws ReflectiveOperationException 反射异常
     * @throws IOException                  IO异常
     */
    public static Object createParser(InputStream inputStream, String xContentPackage)
            throws ReflectiveOperationException, IOException {
        Class<?> xContentTypeClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_TYPE);
        Class<?> xContentFactoryClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_FACTORY);
        Class<?> namedXContentRegistryClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_REGISTRY);
        Class<?> deprecationHandlerClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_DEPRECATION_HANDLER);

        Object jsonType = xContentTypeClass.getField(SimpleElasticsearchSearchConstant.FIELD_JSON).get(null);
        Method xContentMethod = xContentFactoryClass.getMethod(SimpleElasticsearchSearchConstant.METHOD_XCONTENT, xContentTypeClass);
        Object xContent = xContentMethod.invoke(null, jsonType);

        Object registry = getNamedXContentRegistry();
        Object throwUnsupportedOperation = deprecationHandlerClass.getField(SimpleElasticsearchSearchConstant.FIELD_THROW_UNSUPPORTED_OPERATION).get(null);

        Method createParserMethod = xContent.getClass().getMethod(
                SimpleElasticsearchSearchConstant.METHOD_CREATE_PARSER,
                namedXContentRegistryClass,
                deprecationHandlerClass,
                InputStream.class
        );
        return createParserMethod.invoke(xContent, registry, throwUnsupportedOperation, inputStream);
    }

    /**
     * 安全关闭 XContentParser
     *
     * @param parser XContentParser 对象
     */
    public static void closeParser(Object parser) {
        if (parser instanceof AutoCloseable) {
            try {
                ((AutoCloseable) parser).close();
            } catch (Exception e) {
                log.warn("Failed to close XContentParser", e);
            }
        }
    }

    /**
     * 解析响应（通用，支持 SearchResponse/GetMappingsResponse 等）
     *
     * @param inputStream     输入流
     * @param responseClass   目标响应类型
     * @param xContentPackage XContent API 包路径
     * @param <T>             响应类型泛型
     * @return 解析后的响应对象
     * @throws IOException IO异常
     */
    public static <T> T parseResponse(InputStream inputStream, Class<T> responseClass,
                                      String xContentPackage) throws IOException {
        Object parser = null;
        try {
            parser = createParser(inputStream, xContentPackage);
            Class<?> xContentParserClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_PARSER);
            Method fromXContentMethod = responseClass.getMethod(SimpleElasticsearchSearchConstant.METHOD_FROM_XCONTENT, xContentParserClass);
            @SuppressWarnings("unchecked")
            T result = (T) fromXContentMethod.invoke(null, parser);
            return result;
        } catch (ClassNotFoundException e) {
            throw new IOException("XContent API class not found: " + xContentPackage, e);
        } catch (ReflectiveOperationException e) {
            Throwable rootCause = e.getCause() != null ? e.getCause() : e;
            throw new IOException("Failed to parse " + responseClass.getSimpleName() + " using XContent API: " + xContentPackage +
                    ". Root cause: " + rootCause.getClass().getSimpleName() + ", Message: " + rootCause.getMessage(), e);
        } finally {
            closeParser(parser);
        }
    }

    /**
     * 使用 XContent API 解析 SearchResponse（字节数组版本）
     *
     * @param responseBytes   HTTP 响应体字节数组
     * @param xContentPackage XContent API 包路径
     * @return SearchResponse
     * @throws IOException IO异常
     */
    public static SearchResponse parseSearchResponse(byte[] responseBytes, String xContentPackage) throws IOException {
        return parseResponse(new java.io.ByteArrayInputStream(responseBytes), SearchResponse.class, xContentPackage);
    }

    /**
     * 解析 _count API 响应，取 count 字段值
     *
     * @param inputStream     HTTP 响应流
     * @param xContentPackage XContent API 包路径
     * @return 匹配文档数
     * @throws IOException IO异常
     * @since 1.6.6
     */
    public static long parseCountResponse(InputStream inputStream, String xContentPackage) throws IOException {
        Object parser = null;
        try {
            parser = createParser(inputStream, xContentPackage);
            // 使用 XContentParser 遍历 token，找到 "count" 字段
            Class<?> xContentParserClass = Class.forName(xContentPackage + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_PARSER);
            Class<?> tokenClass = Class.forName(xContentParserClass.getName() + SimpleElasticsearchSearchConstant.XCONTENT_CLASS_TOKEN);
            Method nextToken = xContentParserClass.getMethod(SimpleElasticsearchSearchConstant.METHOD_NEXT_TOKEN);
            Method currentName = xContentParserClass.getMethod(SimpleElasticsearchSearchConstant.METHOD_CURRENT_NAME);
            Method longValue = xContentParserClass.getMethod(SimpleElasticsearchSearchConstant.METHOD_GET_LONG_VALUE);
            Object fieldNameToken = tokenClass.getField(SimpleElasticsearchSearchConstant.FIELD_TOKEN_FIELD_NAME).get(null);

            Object token = nextToken.invoke(parser);
            while (token != null) {
                if (fieldNameToken.equals(token)) {
                    String fieldName = (String) currentName.invoke(parser);
                    if (SimpleElasticsearchSearchConstant.ES_JSON_COUNT.equals(fieldName)) {
                        nextToken.invoke(parser); // 移动到 count 的值
                        return (Long) longValue.invoke(parser);
                    }
                }
                token = nextToken.invoke(parser);
            }
            throw new IOException(ErrorMessage.COUNT_RESPONSE_FIELD_MISSING);
        } catch (ReflectiveOperationException e) {
            throw new IOException(String.format(ErrorMessage.COUNT_RESPONSE_PARSE_FAILED, e.getMessage()), e);
        } finally {
            closeParser(parser);
        }
    }
}

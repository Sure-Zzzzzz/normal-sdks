package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ElasticsearchXContentException;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * Elasticsearch XContent 兼容 helper
 *
 * @author surezzzzzz
 */
@Slf4j
public final class XContentCompatibilityHelper {

    private static final String XCONTENT_PACKAGE_7X = "org.elasticsearch.xcontent.";
    private static final String XCONTENT_PACKAGE_6X = "org.elasticsearch.common.xcontent.";
    private static final String XCONTENT_CLASS_TOKEN = "$Token";

    private static volatile String detectedXContentPackage;
    private static volatile Object namedXContentRegistry;

    private XContentCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String detectXContentPackage() {
        if (detectedXContentPackage != null) {
            return detectedXContentPackage;
        }
        synchronized (XContentCompatibilityHelper.class) {
            if (detectedXContentPackage != null) {
                return detectedXContentPackage;
            }
            if (ElasticsearchReflectionHelper.isClassPresent(SimpleElasticsearchRouteConstant.CLASS_XCONTENT_TYPE_7X)) {
                detectedXContentPackage = XCONTENT_PACKAGE_7X;
                return detectedXContentPackage;
            }
            if (ElasticsearchReflectionHelper.isClassPresent(SimpleElasticsearchRouteConstant.CLASS_XCONTENT_TYPE_6X)) {
                detectedXContentPackage = XCONTENT_PACKAGE_6X;
                return detectedXContentPackage;
            }
            throw new ElasticsearchXContentException(ErrorCode.ROUTE_COMPAT_XCONTENT_API_NOT_FOUND,
                    ErrorMessage.ROUTE_COMPAT_XCONTENT_API_NOT_FOUND);
        }
    }

    public static boolean useXContent7xPackage() {
        return XCONTENT_PACKAGE_7X.equals(detectXContentPackage());
    }

    public static Object getNamedXContentRegistry() {
        if (namedXContentRegistry != null) {
            return namedXContentRegistry;
        }
        synchronized (XContentCompatibilityHelper.class) {
            if (namedXContentRegistry != null) {
                return namedXContentRegistry;
            }
            String xContentPackage = detectXContentPackage();
            Class<?> namedXContentRegistryClass = ElasticsearchReflectionHelper.loadClass(
                    xContentPackage + "NamedXContentRegistry");
            try {
                namedXContentRegistry = createSearchModuleRegistry(namedXContentRegistryClass);
            } catch (Exception e) {
                log.warn("通过 SearchModule 创建 NamedXContentRegistry 失败，将使用 EMPTY registry：{}", e.getMessage());
                namedXContentRegistry = ElasticsearchReflectionHelper.getStaticField(namedXContentRegistryClass,
                        SimpleElasticsearchRouteConstant.FIELD_EMPTY);
            }
            return namedXContentRegistry;
        }
    }

    public static Object createParser(InputStream inputStream) {
        String xContentPackage = detectXContentPackage();
        try {
            Class<?> xContentTypeClass = ElasticsearchReflectionHelper.loadClass(xContentPackage + "XContentType");
            Class<?> xContentFactoryClass = ElasticsearchReflectionHelper.loadClass(xContentPackage + "XContentFactory");
            Class<?> namedXContentRegistryClass = ElasticsearchReflectionHelper.loadClass(xContentPackage + "NamedXContentRegistry");
            Class<?> deprecationHandlerClass = ElasticsearchReflectionHelper.loadClass(xContentPackage + "DeprecationHandler");

            Object jsonType = ElasticsearchReflectionHelper.getStaticField(xContentTypeClass,
                    SimpleElasticsearchRouteConstant.FIELD_JSON);
            Method xContentMethod = ElasticsearchReflectionHelper.loadMethod(xContentFactoryClass,
                    SimpleElasticsearchRouteConstant.METHOD_XCONTENT, xContentTypeClass);
            Object xContent = ElasticsearchReflectionHelper.invoke(xContentMethod, null, jsonType);

            Object registry = getNamedXContentRegistry();
            Object deprecationHandler = ElasticsearchReflectionHelper.getStaticField(deprecationHandlerClass,
                    SimpleElasticsearchRouteConstant.FIELD_THROW_UNSUPPORTED_OPERATION);
            Method createParserMethod = ElasticsearchReflectionHelper.loadMethod(xContent.getClass(),
                    SimpleElasticsearchRouteConstant.METHOD_CREATE_PARSER,
                    namedXContentRegistryClass, deprecationHandlerClass, InputStream.class);
            return ElasticsearchReflectionHelper.invoke(createParserMethod, xContent, registry, deprecationHandler, inputStream);
        } catch (ElasticsearchXContentException e) {
            throw e;
        } catch (Exception e) {
            throw new ElasticsearchXContentException(ErrorCode.ROUTE_COMPAT_XCONTENT_PARSE_FAILED,
                    String.format(ErrorMessage.ROUTE_COMPAT_XCONTENT_PARSE_FAILED, xContentPackage), e);
        }
    }

    public static Object createParser(byte[] responseBytes) {
        return createParser(new ByteArrayInputStream(responseBytes == null ? new byte[0] : responseBytes));
    }

    public static void closeParser(Object parser) {
        if (parser instanceof AutoCloseable) {
            try {
                ((AutoCloseable) parser).close();
            } catch (Exception e) {
                log.warn("关闭 XContentParser 失败", e);
            }
        }
    }

    public static <T> T parseResponse(InputStream inputStream, Class<T> responseClass) throws IOException {
        Object parser = null;
        String xContentPackage = detectXContentPackage();
        try {
            parser = createParser(inputStream);
            Class<?> xContentParserClass = ElasticsearchReflectionHelper.loadClass(xContentPackage + "XContentParser");
            Method fromXContentMethod = ElasticsearchReflectionHelper.loadMethod(responseClass,
                    SimpleElasticsearchRouteConstant.METHOD_FROM_XCONTENT, xContentParserClass);
            @SuppressWarnings("unchecked")
            T result = (T) ElasticsearchReflectionHelper.invoke(fromXContentMethod, null, parser);
            return result;
        } catch (ElasticsearchXContentException e) {
            throw e;
        } catch (Exception e) {
            throw new ElasticsearchXContentException(ErrorCode.ROUTE_COMPAT_XCONTENT_PARSE_FAILED,
                    String.format(ErrorMessage.ROUTE_COMPAT_XCONTENT_PARSE_FAILED, responseClass.getName()), e);
        } finally {
            closeParser(parser);
        }
    }

    public static <T> T parseResponse(Response response, Class<T> responseClass) throws IOException {
        return parseResponse(response.getEntity().getContent(), responseClass);
    }

    public static SearchResponse parseSearchResponse(byte[] responseBytes) throws IOException {
        return parseResponse(new ByteArrayInputStream(responseBytes), SearchResponse.class);
    }

    public static long parseCountResponse(InputStream inputStream) throws IOException {
        Object parser = null;
        String xContentPackage = detectXContentPackage();
        try {
            parser = createParser(inputStream);
            Class<?> xContentParserClass = ElasticsearchReflectionHelper.loadClass(xContentPackage + "XContentParser");
            Class<?> tokenClass = ElasticsearchReflectionHelper.loadClass(xContentParserClass.getName() + XCONTENT_CLASS_TOKEN);
            Method nextToken = ElasticsearchReflectionHelper.loadMethod(xContentParserClass,
                    SimpleElasticsearchRouteConstant.METHOD_NEXT_TOKEN);
            Method currentName = ElasticsearchReflectionHelper.loadMethod(xContentParserClass,
                    SimpleElasticsearchRouteConstant.METHOD_CURRENT_NAME);
            Method longValue = ElasticsearchReflectionHelper.loadMethod(xContentParserClass,
                    SimpleElasticsearchRouteConstant.METHOD_LONG_VALUE);
            Object fieldNameToken = ElasticsearchReflectionHelper.getStaticField(tokenClass,
                    SimpleElasticsearchRouteConstant.FIELD_TOKEN_FIELD_NAME);

            Object token = ElasticsearchReflectionHelper.invoke(nextToken, parser);
            while (token != null) {
                if (fieldNameToken.equals(token)) {
                    String fieldName = (String) ElasticsearchReflectionHelper.invoke(currentName, parser);
                    if (SimpleElasticsearchRouteConstant.JSON_FIELD_COUNT.equals(fieldName)) {
                        ElasticsearchReflectionHelper.invoke(nextToken, parser);
                        Object value = ElasticsearchReflectionHelper.invoke(longValue, parser);
                        return ((Number) value).longValue();
                    }
                }
                token = ElasticsearchReflectionHelper.invoke(nextToken, parser);
            }
            throw new ElasticsearchXContentException(ErrorCode.ROUTE_COMPAT_XCONTENT_PARSE_FAILED,
                    String.format(ErrorMessage.ROUTE_COMPAT_XCONTENT_PARSE_FAILED,
                            SimpleElasticsearchRouteConstant.JSON_FIELD_COUNT));
        } finally {
            closeParser(parser);
        }
    }

    public static long parseCountResponse(Response response) throws IOException {
        return parseCountResponse(response.getEntity().getContent());
    }

    private static Object createSearchModuleRegistry(Class<?> namedXContentRegistryClass) throws Exception {
        Class<?> searchModuleClass = ElasticsearchReflectionHelper.loadClass(SimpleElasticsearchRouteConstant.CLASS_SEARCH_MODULE);
        Class<?> settingsClass = ElasticsearchReflectionHelper.loadClass(SimpleElasticsearchRouteConstant.CLASS_SETTINGS);
        Object emptySettings = ElasticsearchReflectionHelper.getStaticField(settingsClass,
                SimpleElasticsearchRouteConstant.FIELD_EMPTY);

        Object searchModule = null;
        Constructor<?> constructor = ElasticsearchReflectionHelper.findConstructor(searchModuleClass, settingsClass, List.class);
        if (constructor != null) {
            searchModule = ElasticsearchReflectionHelper.newInstance(constructor, emptySettings, Collections.emptyList());
        }
        if (searchModule == null) {
            constructor = ElasticsearchReflectionHelper.findConstructor(searchModuleClass, settingsClass, boolean.class, List.class);
            if (constructor != null) {
                searchModule = ElasticsearchReflectionHelper.newInstance(constructor, emptySettings, false, Collections.emptyList());
            }
        }
        if (searchModule == null) {
            throw new ElasticsearchXContentException(ErrorCode.ROUTE_COMPAT_XCONTENT_REGISTRY_CREATE_FAILED,
                    ErrorMessage.ROUTE_COMPAT_XCONTENT_REGISTRY_CREATE_FAILED);
        }

        Method getNamedXContentsMethod = ElasticsearchReflectionHelper.loadMethod(searchModuleClass,
                SimpleElasticsearchRouteConstant.METHOD_GET_NAMED_XCONTENTS);
        @SuppressWarnings("unchecked")
        List<?> namedXContents = (List<?>) ElasticsearchReflectionHelper.invoke(getNamedXContentsMethod, searchModule);
        Constructor<?> registryConstructor = ElasticsearchReflectionHelper.loadConstructor(namedXContentRegistryClass, List.class);
        return ElasticsearchReflectionHelper.newInstance(registryConstructor, namedXContents);
    }
}

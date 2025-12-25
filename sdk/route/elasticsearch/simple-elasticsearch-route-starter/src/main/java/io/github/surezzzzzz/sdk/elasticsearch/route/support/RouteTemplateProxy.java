package io.github.surezzzzzz.sdk.elasticsearch.route.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.VersionCompatibilityErrorPattern;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ConfigurationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * ElasticsearchRestTemplate 路由代理（使用 CGLIB）
 * 支持 SpEL 表达式解析
 *
 * <p><b>版本兼容性说明：</b></p>
 * <ul>
 *   <li>本代理仅负责根据索引名称将请求路由到不同的 Elasticsearch 数据源</li>
 *   <li>不负责屏蔽 Spring Data Elasticsearch 与不同 ES 版本之间的 API 兼容性问题</li>
 *   <li>某些 Spring Data API（如 IndexOperations.getSettings()）可能在特定 ES 版本下不兼容</li>
 *   <li>建议：对于版本敏感的操作，使用 {@link io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry#getHighLevelClient(String)}
 *       获取原生 RestHighLevelClient 进行操作</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class RouteTemplateProxy implements MethodInterceptor {

    private final Map<String, ElasticsearchRestTemplate> templates;
    private final ElasticsearchRestTemplate defaultTemplate;
    private final RouteResolver routeResolver;

    /**
     * 创建代理实例
     */
    public static ElasticsearchRestTemplate createProxy(
            Map<String, ElasticsearchRestTemplate> templates,
            ElasticsearchRestTemplate defaultTemplate,
            RouteResolver routeResolver) {
        RestHighLevelClient client = extractClient(defaultTemplate);
        return createProxy(templates, defaultTemplate, routeResolver, client);
    }

    /**
     * 创建代理实例（显式指定 client，避免反射提取）
     */
    public static ElasticsearchRestTemplate createProxy(
            Map<String, ElasticsearchRestTemplate> templates,
            ElasticsearchRestTemplate defaultTemplate,
            RouteResolver routeResolver,
            RestHighLevelClient client) {

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(ElasticsearchRestTemplate.class);
        enhancer.setCallback(new RouteTemplateProxy(templates, defaultTemplate, routeResolver));

        return (ElasticsearchRestTemplate) enhancer.create(
                new Class<?>[]{RestHighLevelClient.class},
                new Object[]{client}
        );
    }

    /**
     * 通过反射从 ElasticsearchRestTemplate 中提取 RestHighLevelClient
     */
    private static RestHighLevelClient extractClient(ElasticsearchRestTemplate template) {
        try {
            Field clientField = ElasticsearchRestTemplate.class.getDeclaredField("client");
            clientField.setAccessible(true);
            return (RestHighLevelClient) clientField.get(template);
        } catch (Exception e) {
            throw new ConfigurationException(ErrorCode.OTHER_CLIENT_EXTRACT_FAILED,
                    ErrorMessage.OTHER_CLIENT_EXTRACT_FAILED, e);
        }
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        String indexName = extractIndexName(method, args);
        ElasticsearchRestTemplate template = determineTemplate(indexName);

        try {
            return method.invoke(template, args);
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();

            // 检测版本兼容性问题并记录详细信息
            if (isVersionCompatibilityIssue(targetException)) {
                VersionCompatibilityErrorPattern pattern =
                        VersionCompatibilityErrorPattern.findMatch(getRootCauseMessage(targetException));

                log.warn("检测到 Elasticsearch 版本兼容性问题: " +
                                "method=[{}], index=[{}], pattern=[{}]. " +
                                "这不是 simple-elasticsearch-route-starter 的问题，" +
                                "而是 Spring Data Elasticsearch API 与特定 ES 版本不兼容导致的. " +
                                "建议使用 SimpleElasticsearchRouteRegistry.getHighLevelClient() " +
                                "获取原生客户端进行版本敏感的操作. " +
                                "原始错误: {}",
                        method.getName(),
                        indexName,
                        pattern != null ? pattern.getDescription() : "unknown",
                        getRootCauseMessage(targetException));
            } else {
                log.error("Error invoking method [{}] on template for index [{}]",
                        method.getName(), indexName, targetException);
            }

            throw targetException;
        } catch (IllegalAccessException | IllegalArgumentException e) {
            log.error("Reflection error invoking method [{}] on template for index [{}]",
                    method.getName(), indexName, e);
            throw new RuntimeException("Failed to invoke method via reflection", e);
        }
    }

    /**
     * 判断异常是否是 Elasticsearch 版本兼容性问题
     */
    private boolean isVersionCompatibilityIssue(Throwable e) {
        String message = getRootCauseMessage(e);
        return VersionCompatibilityErrorPattern.isAnyMatch(message);
    }

    /**
     * 获取异常的根本原因消息
     */
    private String getRootCauseMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    /**
     * 获取异常的根本原因消息
     */
    private String getRootCauseMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    /**
     * 从方法参数中提取索引名称
     */
    private String extractIndexName(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        for (Object arg : args) {
            if (arg == null) {
                continue;
            }

            // 优先从 IndexCoordinates 获取（已解析，最准确）
            if (arg instanceof IndexCoordinates) {
                String indexName = ((IndexCoordinates) arg).getIndexName();
                log.trace("Extracted index name [{}] from IndexCoordinates", indexName);
                return indexName;
            }

            // 从 Class 类型的 @Document 注解获取
            if (arg instanceof Class) {
                String indexName = extractIndexFromClass((Class<?>) arg);
                if (indexName != null) {
                    log.trace("Extracted index name [{}] from Class annotation", indexName);
                    return indexName;
                }
            }
        }

        log.trace("No index name extracted from method [{}] arguments", method.getName());
        return null;
    }

    /**
     * 从 Class 的 @Document 注解中提取索引名称
     * 支持 SpEL 表达式解析
     */
    private String extractIndexFromClass(Class<?> clazz) {
        Document doc = clazz.getAnnotation(Document.class);
        if (doc != null) {
            String indexName = doc.indexName();

            // ========== 解析 SpEL 表达式 ==========
            if (SpELResolver.isSpEL(indexName)) {
                String resolved = SpELResolver.resolve(indexName);
                log.trace("Resolved SpEL index name from [{}] to [{}]", indexName, resolved);
                return resolved;
            }

            return indexName;
        }
        return null;
    }

    /**
     * 根据索引名称确定使用哪个 template
     */
    private ElasticsearchRestTemplate determineTemplate(String indexName) {
        String dataSourceKey = routeResolver.resolveDataSource(indexName);

        ElasticsearchRestTemplate template = templates.get(dataSourceKey);
        if (template == null) {
            log.warn("Datasource [{}] not found for index [{}], using default template",
                    dataSourceKey, indexName);
            return defaultTemplate;
        }

        log.trace("Routing index [{}] to datasource [{}]", indexName, dataSourceKey);
        return template;
    }
}

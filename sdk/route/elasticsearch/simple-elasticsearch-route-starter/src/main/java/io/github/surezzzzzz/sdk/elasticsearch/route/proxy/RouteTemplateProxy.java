package io.github.surezzzzzz.sdk.elasticsearch.route.proxy;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.VersionCompatibilityErrorPattern;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexNameExtractor;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * ElasticsearchRestTemplate 路由代理(使用 CGLIB)
 * 支持 SpEL 表达式解析
 *
 * <p><b>版本兼容性说明:</b></p>
 * <ul>
 *   <li>本代理仅负责根据索引名称将请求路由到不同的 Elasticsearch 数据源</li>
 *   <li>不负责屏蔽 Spring Data Elasticsearch 与不同 ES 版本之间的 API 兼容性问题</li>
 *   <li>某些 Spring Data API(如 IndexOperations.getSettings())可能在特定 ES 版本下不兼容</li>
 *   <li>建议: 对于版本敏感的操作,使用 {@link io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry#getHighLevelClient(String)}
 *       获取原生 RestHighLevelClient 进行操作</li>
 * </ul>
 *
 * <p><b>索引名称提取策略(动态加载,责任链模式):</b></p>
 * <p>系统会自动发现所有实现了 {@link IndexNameExtractor} 接口并标注了
 * {@link io.github.surezzzzzz.sdk.elasticsearch.route.annotation.SimpleElasticsearchRouteComponent} 的 Bean,
 * 按照 @Order 注解定义的优先级顺序执行提取。</p>
 *
 * <p>内置提取器:</p>
 * <ol>
 *   <li>{@link io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexCoordinatesExtractor} - Order(1) - 从 IndexCoordinates 参数提取(优先级最高)</li>
 *   <li>{@link io.github.surezzzzzz.sdk.elasticsearch.route.extractor.EntityObjectExtractor} - Order(2) - 从实体对象提取(修复 save 方法 bug)</li>
 *   <li>{@link io.github.surezzzzzz.sdk.elasticsearch.route.extractor.ClassTypeExtractor} - Order(3) - 从 Class 类型参数提取</li>
 *   <li>{@link io.github.surezzzzzz.sdk.elasticsearch.route.extractor.IndexQueryExtractor} - Order(4) - 从 IndexQuery 参数提取(批量索引场景)</li>
 * </ol>
 *
 * <p><b>如何添加自定义提取器:</b></p>
 * <pre>
 * &#64;SimpleElasticsearchRouteComponent
 * &#64;Order(10)  // 设置优先级,数字越小优先级越高 (内置提取器已占用 1-4)
 * public class MyCustomExtractor implements IndexNameExtractor {
 *     &#64;Override
 *     public String extract(Method method, Object[] args) {
 *         // 遍历方法参数,查找目标参数
 *         for (Object arg : args) {
 *             if (supports(arg)) {
 *                 // 从参数中提取索引名
 *                 String indexName = extractIndexNameFrom(arg);
 *                 if (indexName != null &amp;&amp; !indexName.isEmpty()) {
 *                     return indexName;
 *                 }
 *             }
 *         }
 *         return null;  // 未找到索引名,责任链继续
 *     }
 *
 *     &#64;Override
 *     public boolean supports(Object arg) {
 *         // 判断是否支持此参数类型
 *         return arg instanceof YourCustomType;
 *     }
 * }
 * </pre>
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
     * 索引名称提取器责任链(按优先级排序,动态注入)
     * <p>通过 Spring 自动收集所有标注了 @SimpleElasticsearchRouteComponent 的 IndexNameExtractor 实现</p>
     */
    private final List<IndexNameExtractor> indexNameExtractors;

    /**
     * 创建代理实例
     */
    public static ElasticsearchRestTemplate createProxy(
            Map<String, ElasticsearchRestTemplate> templates,
            ElasticsearchRestTemplate defaultTemplate,
            RouteResolver routeResolver,
            List<IndexNameExtractor> extractors) {
        RestHighLevelClient client = extractClient(defaultTemplate);
        return createProxy(templates, defaultTemplate, routeResolver, extractors, client);
    }

    /**
     * 创建代理实例(显式指定 client,避免反射提取)
     */
    public static ElasticsearchRestTemplate createProxy(
            Map<String, ElasticsearchRestTemplate> templates,
            ElasticsearchRestTemplate defaultTemplate,
            RouteResolver routeResolver,
            List<IndexNameExtractor> extractors,
            RestHighLevelClient client) {

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(ElasticsearchRestTemplate.class);
        enhancer.setCallback(new RouteTemplateProxy(templates, defaultTemplate, routeResolver, extractors));

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
     * 从方法参数中提取索引名称(使用责任链模式,动态注入的提取器)
     */
    private String extractIndexName(Method method, Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        // 责任链模式: 按优先级遍历提取器
        for (IndexNameExtractor extractor : indexNameExtractors) {
            String indexName = extractor.extract(method, args);
            if (indexName != null) {
                return indexName;
            }
        }

        log.trace("No index name extracted from method [{}] arguments", method.getName());
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

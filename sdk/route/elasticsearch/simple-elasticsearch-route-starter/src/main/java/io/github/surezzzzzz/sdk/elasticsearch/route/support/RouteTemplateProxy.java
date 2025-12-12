package io.github.surezzzzzz.sdk.elasticsearch.route.support;

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
import java.lang.reflect.Method;
import java.util.Map;

/**
 * ElasticsearchRestTemplate 路由代理（使用 CGLIB）
 * 支持 SpEL 表达式解析
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

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(ElasticsearchRestTemplate.class);
        enhancer.setCallback(new RouteTemplateProxy(templates, defaultTemplate, routeResolver));

        // 通过反射获取 defaultTemplate 的 client
        RestHighLevelClient client = extractClient(defaultTemplate);

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
            throw new RuntimeException("Failed to extract RestHighLevelClient from ElasticsearchRestTemplate", e);
        }
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        // 提取索引名称
        String indexName = extractIndexName(method, args);

        // 根据索引名称选择对应的 template
        ElasticsearchRestTemplate template = determineTemplate(indexName);

        // 调用真实方法
        try {
            return method.invoke(template, args);
        } catch (Exception e) {
            log.error("Error invoking method [{}] on template for index [{}]",
                    method.getName(), indexName, e);
            throw e;
        }
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

        log.debug("Routing index [{}] to datasource [{}]", indexName, dataSourceKey);
        return template;
    }
}

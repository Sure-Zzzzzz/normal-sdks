package io.github.surezzzzzz.sdk.elasticsearch.route.proxy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * ElasticsearchRestTemplate 路由代理（CGLIB）
 *
 * <p>使用 {@link RouteRoutingInterceptor} 处理所有路由逻辑，
 * 支持日期分片索引、读操作通配符、异步写等 v1.1.0 新功能。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class RouteTemplateProxy implements MethodInterceptor {

    private final RouteRoutingInterceptor routingInterceptor;

    /**
     * 创建代理实例
     */
    public static ElasticsearchRestTemplate createProxy(
            RouteRoutingInterceptor routingInterceptor,
            RestHighLevelClient client) {

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(ElasticsearchRestTemplate.class);
        enhancer.setInterfaces(new Class<?>[]{SaveAndGetInterface.class});
        enhancer.setCallback(new RouteTemplateProxy(routingInterceptor));

        return (ElasticsearchRestTemplate) enhancer.create(
                new Class<?>[]{RestHighLevelClient.class},
                new Object[]{client}
        );
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
        // Non-public methods (protected/package-private) are internal to ElasticsearchRestTemplate
        // and must not be intercepted — invokeSuper to let CGLIB call the real superclass impl.
        if (!Modifier.isPublic(method.getModifiers())) {
            return proxy.invokeSuper(obj, args);
        }
        String indexName = routingInterceptor.extractIndexName(method, args);
        ElasticsearchRestTemplate template = routingInterceptor.determineTemplate(indexName);
        return routingInterceptor.route(method, args, template);
    }
}

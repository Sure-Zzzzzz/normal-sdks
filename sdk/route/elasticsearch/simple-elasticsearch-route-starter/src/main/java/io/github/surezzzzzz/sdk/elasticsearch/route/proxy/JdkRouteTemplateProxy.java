package io.github.surezzzzzz.sdk.elasticsearch.route.proxy;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ConfigurationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * JDK 动态代理实现
 *
 * <p>当 CGLIB 不可用时，使用 JDK 动态代理替代。
 * 向父类链递归收集所有接口，确保 SB 2.4.x 下 {@code ElasticsearchRestTemplate}
 * 通过父类实现的接口也能被代理覆盖。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class JdkRouteTemplateProxy implements InvocationHandler {

    private final RouteRoutingInterceptor routingInterceptor;
    private final ElasticsearchRestTemplate defaultTemplate;

    /**
     * 创建 JDK 代理实例
     *
     * @param routingInterceptor 路由拦截器
     * @param defaultTemplate    默认 template
     * @return 代理后的 ElasticsearchRestTemplate
     */
    public static ElasticsearchRestTemplate createProxy(
            RouteRoutingInterceptor routingInterceptor,
            ElasticsearchRestTemplate defaultTemplate) {

        Class<?> templateClass = defaultTemplate.getClass();
        Class<?>[] interfaces = collectInterfaces(templateClass);
        if (interfaces.length == 0) {
            throw new ConfigurationException(ErrorCode.OTHER_DATASOURCE_INIT_FAILED,
                    String.format(ErrorMessage.PROXY_JDK_INTERFACE_NOT_FOUND, templateClass.getName()));
        }

        log.debug("JDK 代理接口列表: {}", (Object) interfaces);
        JdkRouteTemplateProxy handler = new JdkRouteTemplateProxy(routingInterceptor, defaultTemplate);
        return (ElasticsearchRestTemplate) Proxy.newProxyInstance(
                templateClass.getClassLoader(),
                interfaces,
                handler
        );
    }

    /**
     * 递归收集类及其所有父类实现的接口（去重，保持顺序）
     */
    private static Class<?>[] collectInterfaces(Class<?> clazz) {
        Set<Class<?>> result = new LinkedHashSet<>();
        // Spring Data Elasticsearch 3.x 接口未声明 save(Object) / get(String, Class)。
        result.add(SaveAndGetInterface.class);
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Class<?> iface : current.getInterfaces()) {
                result.add(iface);
            }
            current = current.getSuperclass();
        }
        return result.toArray(new Class<?>[0]);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }

        String indexName = routingInterceptor.extractIndexName(method, args);
        ElasticsearchRestTemplate template = routingInterceptor.determineTemplate(indexName);
        return routingInterceptor.route(method, args, template);
    }
}

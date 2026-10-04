package io.github.surezzzzzz.sdk.elasticsearch.route.proxy;

import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Spring Data Elasticsearch 5 接口路由代理。
 *
 * <p>ELC 模板没有可安全继承的稳定构造器；路由只代理公开的
 * {@link ElasticsearchOperations} 接口，避免 CGLIB 复制底层客户端状态。对于
 * {@code withRouting}/{@code withRefreshPolicy}，代理会把 fluent 状态重放到最终选中的
 * 数据源模板，防止链式调用在 secondary 等非默认源上丢失配置。</p>
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class JdkRouteTemplateProxy implements InvocationHandler {

    private final RouteRoutingInterceptor routingInterceptor;
    private final ElasticsearchOperations defaultTemplate;
    private final List<TemplateConfigurationCall> templateConfigurationCalls;

    public static ElasticsearchOperations createProxy(
            RouteRoutingInterceptor routingInterceptor,
            ElasticsearchOperations defaultTemplate) {
        return createProxy(routingInterceptor, defaultTemplate, Collections.emptyList());
    }

    private static ElasticsearchOperations createProxy(
            RouteRoutingInterceptor routingInterceptor,
            ElasticsearchOperations defaultTemplate,
            List<TemplateConfigurationCall> templateConfigurationCalls) {
        return (ElasticsearchOperations) Proxy.newProxyInstance(
                defaultTemplate.getClass().getClassLoader(),
                new Class<?>[]{ElasticsearchOperations.class},
                new JdkRouteTemplateProxy(routingInterceptor, defaultTemplate, templateConfigurationCalls));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(method, args);
        }

        if (routingInterceptor.isFluentConfigurationMethod(method)) {
            return createFluentProxy(method, args);
        }

        String[] indexNames = routingInterceptor.extractIndexNames(method, args);
        RouteRoutingInterceptor.RouteTarget target = routingInterceptor.isClosePointInTime(method)
                ? routingInterceptor.determinePointInTimeTarget((String) args[0])
                : routingInterceptor.determineTarget(indexNames);
        ElasticsearchOperations configuredTemplate = applyTemplateConfiguration(target.getTemplate());
        RouteRoutingInterceptor.RouteTarget configuredTarget = new RouteRoutingInterceptor.RouteTarget(
                target.getDatasourceKey(), configuredTemplate);
        return routingInterceptor.route(method, args, configuredTarget, indexNames);
    }

    /**
     * 对 default 模板执行一次 fluent 调用以保留 Spring Data 的参数校验，再记录调用用于
     * 将来在被路由到的模板上重放。返回值始终是新的 Route proxy，而不是原生模板。
     */
    private ElasticsearchOperations createFluentProxy(Method method, Object[] args) {
        ElasticsearchOperations currentTemplate = applyTemplateConfiguration(defaultTemplate);
        Object result = routingInterceptor.invokeOriginal(currentTemplate, method, args);
        if (!(result instanceof ElasticsearchOperations)) {
            throw new IllegalStateException("ElasticsearchOperations fluent 调用未返回 ElasticsearchOperations");
        }
        List<TemplateConfigurationCall> calls = new ArrayList<>(templateConfigurationCalls);
        calls.add(new TemplateConfigurationCall(method, args == null ? new Object[0] : Arrays.copyOf(args, args.length)));
        return createProxy(routingInterceptor, defaultTemplate, Collections.unmodifiableList(calls));
    }

    private ElasticsearchOperations applyTemplateConfiguration(ElasticsearchOperations template) {
        ElasticsearchOperations configured = template;
        for (TemplateConfigurationCall call : templateConfigurationCalls) {
            Object result = routingInterceptor.invokeOriginal(configured, call.getMethod(), call.getArgs());
            if (!(result instanceof ElasticsearchOperations)) {
                throw new IllegalStateException("ElasticsearchOperations fluent 配置未返回 ElasticsearchOperations");
            }
            configured = (ElasticsearchOperations) result;
        }
        return configured;
    }

    private Object invokeObjectMethod(Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(defaultTemplate, args);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }

    /**
     * 记录可重放的 fluent 调用。参数数组复制，避免调用方随后改写数组影响路由代理状态。
     */
    @lombok.Getter
    @RequiredArgsConstructor
    private static class TemplateConfigurationCall {
        private final Method method;
        private final Object[] args;
    }
}

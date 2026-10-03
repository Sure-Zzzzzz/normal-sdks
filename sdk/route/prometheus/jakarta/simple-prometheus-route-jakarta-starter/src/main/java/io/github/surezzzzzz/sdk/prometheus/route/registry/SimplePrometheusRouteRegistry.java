package io.github.surezzzzzz.sdk.prometheus.route.registry;

import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteRequest;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteResponse;
import io.github.surezzzzzz.sdk.prometheus.route.transport.PrometheusRouteTransport;
import io.github.surezzzzzz.sdk.prometheus.route.transport.PrometheusRouteTransportFactory;
import io.github.surezzzzzz.sdk.prometheus.route.validator.PrometheusRoutePropertiesValidator;
import org.springframework.beans.factory.DisposableBean;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prometheus Route target 资源注册表。
 *
 * @author surezzzzzz
 */
public class SimplePrometheusRouteRegistry implements DisposableBean {

    private final SimplePrometheusRouteProperties properties;
    private final Map<String, PrometheusRouteTransport> transports =
            new LinkedHashMap<String, PrometheusRouteTransport>();
    private final Object lifecycleMonitor = new Object();
    private int inFlight;
    private boolean destroyed;

    /**
     * 创建并初始化 target 私有 transport。
     *
     * @param properties Route 配置
     * @param validator  配置校验器
     * @param factory    transport 创建器
     */
    public SimplePrometheusRouteRegistry(SimplePrometheusRouteProperties properties,
                                         PrometheusRoutePropertiesValidator validator,
                                         PrometheusRouteTransportFactory factory) {
        this.properties = properties;
        validator.validate(properties);
        initialize(factory);
    }

    /**
     * 判断 target 是否已登记。
     *
     * @param targetKey target key
     * @return 是否已登记
     */
    public boolean contains(String targetKey) {
        synchronized (lifecycleMonitor) {
            return transports.containsKey(targetKey);
        }
    }

    /**
     * 确认 Route 尚未销毁。
     *
     * @throws PrometheusRouteException Route 已关闭时抛出
     */
    public void assertOpen() {
        synchronized (lifecycleMonitor) {
            if (destroyed) {
                throw closedException();
            }
        }
    }

    /**
     * 在 registry 控制的 in-flight 生命周期内执行请求，不向外暴露 transport 或 HTTP client。
     *
     * @param targetKey 已解析的 target key
     * @param request   Route 请求
     * @return 响应快照
     */
    public PrometheusRouteResponse exchange(String targetKey, PrometheusRouteRequest request) {
        PrometheusRouteTransport transport = acquire(targetKey);
        try {
            return transport.exchange(request);
        } finally {
            release();
        }
    }

    private PrometheusRouteTransport acquire(String targetKey) {
        synchronized (lifecycleMonitor) {
            if (destroyed) {
                throw closedException();
            }
            PrometheusRouteTransport transport = transports.get(targetKey);
            if (transport == null) {
                throw new PrometheusRouteException(ErrorCode.TARGET_NOT_REGISTERED,
                        ErrorMessage.TARGET_NOT_REGISTERED);
            }
            inFlight++;
            return transport;
        }
    }

    private void release() {
        synchronized (lifecycleMonitor) {
            inFlight--;
            lifecycleMonitor.notifyAll();
        }
    }

    @Override
    public void destroy() {
        synchronized (lifecycleMonitor) {
            if (destroyed) {
                return;
            }
            destroyed = true;
            long deadline = System.currentTimeMillis() + properties.getShutdownTimeoutMs();
            while (inFlight > 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                try {
                    lifecycleMonitor.wait(remaining);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        closeTransports();
    }

    private void initialize(PrometheusRouteTransportFactory factory) {
        for (Map.Entry<String, SimplePrometheusRouteProperties.TargetConfig> entry : properties.getTargets().entrySet()) {
            PrometheusRouteTransport transport = null;
            try {
                transport = factory.create(entry.getKey(), entry.getValue());
                if (transport == null) {
                    throw new PrometheusRouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                            ErrorMessage.TARGET_CONFIGURATION_ILLEGAL);
                }
                synchronized (lifecycleMonitor) {
                    transports.put(entry.getKey(), transport);
                }
            } catch (RuntimeException exception) {
                closeOne(transport);
                closeTransports();
                if (exception instanceof PrometheusRouteException) {
                    throw exception;
                }
                throw new PrometheusRouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                        ErrorMessage.TARGET_CONFIGURATION_ILLEGAL);
            }
        }
    }

    private void closeTransports() {
        Map<String, PrometheusRouteTransport> closing;
        synchronized (lifecycleMonitor) {
            closing = new LinkedHashMap<String, PrometheusRouteTransport>(transports);
            transports.clear();
            lifecycleMonitor.notifyAll();
        }
        for (PrometheusRouteTransport transport : closing.values()) {
            closeOne(transport);
        }
    }

    private void closeOne(PrometheusRouteTransport transport) {
        if (transport == null) {
            return;
        }
        try {
            transport.close();
        } catch (IOException ignored) {
        }
    }

    private PrometheusRouteException closedException() {
        return new PrometheusRouteException(ErrorCode.ROUTE_CLOSED, ErrorMessage.ROUTE_CLOSED);
    }
}

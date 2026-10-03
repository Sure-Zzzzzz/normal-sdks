package io.github.surezzzzzz.sdk.s3.route.registry;

import com.amazonaws.services.s3.AmazonS3;
import io.github.surezzzzzz.sdk.s3.route.annotation.SimpleS3RouteComponent;
import io.github.surezzzzzz.sdk.s3.route.client.S3RouteClientFactory;
import io.github.surezzzzzz.sdk.s3.route.configuration.SimpleS3RouteProperties;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.s3.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.s3.route.exception.S3RouteException;
import io.github.surezzzzzz.sdk.s3.route.validator.S3RoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * S3 Route target 资源注册表。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleS3RouteComponent
public class SimpleS3RouteRegistry implements DisposableBean {

    private final SimpleS3RouteProperties properties;
    private final Map<String, AmazonS3> clients =
            new LinkedHashMap<String, AmazonS3>();
    private final Object lifecycleMonitor = new Object();
    private int inFlight;
    private boolean destroyed;

    /**
     * 创建并初始化 target 私有 AmazonS3 客户端。
     *
     * @param properties Route 配置
     * @param validator  配置校验器
     * @param factory    客户端创建器
     */
    public SimpleS3RouteRegistry(SimpleS3RouteProperties properties,
                                 S3RoutePropertiesValidator validator,
                                 S3RouteClientFactory factory) {
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
            return clients.containsKey(targetKey);
        }
    }

    /**
     * 确认 Route 尚未销毁。
     *
     * @throws S3RouteException Route 已关闭时抛出
     */
    public void assertOpen() {
        synchronized (lifecycleMonitor) {
            if (destroyed) {
                throw closedException();
            }
        }
    }

    /**
     * 获取 target 客户端引用。引用的生命周期归 Route 管理，调用方不得自行关闭；
     * 返回的引用不参与 in-flight 记账，长耗时操作建议改用 {@link #execute}。
     *
     * @param targetKey 已解析的 target key
     * @return target 客户端
     */
    public AmazonS3 getAmazonS3(String targetKey) {
        AmazonS3 client = acquire(targetKey);
        release();
        return client;
    }

    /**
     * 在 registry 控制的 in-flight 生命周期内执行回调，不向外暴露客户端所有权。
     *
     * @param targetKey 已解析的 target key
     * @param callback  以客户端执行的回调
     * @param <T>       回调返回类型
     * @return 回调返回值
     */
    public <T> T execute(String targetKey, Function<AmazonS3, T> callback) {
        AmazonS3 client = acquire(targetKey);
        try {
            return callback.apply(client);
        } finally {
            release();
        }
    }

    private AmazonS3 acquire(String targetKey) {
        synchronized (lifecycleMonitor) {
            if (destroyed) {
                throw closedException();
            }
            AmazonS3 client = clients.get(targetKey);
            if (client == null) {
                throw new S3RouteException(ErrorCode.TARGET_NOT_REGISTERED,
                        ErrorMessage.TARGET_NOT_REGISTERED);
            }
            inFlight++;
            return client;
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
        boolean drained;
        int remainingInFlight;
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
            drained = inFlight <= 0;
            remainingInFlight = inFlight;
        }
        log.debug("S3 Route 关闭, in-flight 排空: {}, 剩余 in-flight: {}", drained, remainingInFlight);
        closeClients();
    }

    private void initialize(S3RouteClientFactory factory) {
        for (Map.Entry<String, SimpleS3RouteProperties.TargetConfig> entry : properties.getTargets().entrySet()) {
            AmazonS3 client = null;
            try {
                client = factory.create(entry.getKey(), entry.getValue());
                if (client == null) {
                    throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                            ErrorMessage.TARGET_CONFIGURATION_ILLEGAL);
                }
                synchronized (lifecycleMonitor) {
                    clients.put(entry.getKey(), client);
                }
            } catch (RuntimeException exception) {
                log.debug("S3 Route target 初始化失败, 回滚已创建客户端, targetKey: {}, 异常类型: {}",
                        entry.getKey(), exception.getClass().getName());
                closeOne(client);
                closeClients();
                if (exception instanceof S3RouteException) {
                    throw exception;
                }
                throw new S3RouteException(ErrorCode.TARGET_CONFIGURATION_ILLEGAL,
                        ErrorMessage.TARGET_CONFIGURATION_ILLEGAL);
            }
        }
        log.debug("S3 Route 初始化完成, target 数量: {}", clients.size());
    }

    private void closeClients() {
        Map<String, AmazonS3> closing;
        synchronized (lifecycleMonitor) {
            closing = new LinkedHashMap<String, AmazonS3>(clients);
            clients.clear();
            lifecycleMonitor.notifyAll();
        }
        for (AmazonS3 client : closing.values()) {
            closeOne(client);
        }
    }

    private void closeOne(AmazonS3 client) {
        if (client == null) {
            return;
        }
        try {
            client.shutdown();
        } catch (RuntimeException ignored) {
        }
    }

    private S3RouteException closedException() {
        return new S3RouteException(ErrorCode.ROUTE_CLOSED, ErrorMessage.ROUTE_CLOSED);
    }
}

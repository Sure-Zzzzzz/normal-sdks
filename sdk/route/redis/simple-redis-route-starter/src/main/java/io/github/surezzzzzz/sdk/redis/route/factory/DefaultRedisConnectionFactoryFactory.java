package io.github.surezzzzzz.sdk.redis.route.factory;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteProperties;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.constant.RedisSourceMode;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.support.RedisConfigurationCompatibilityHelper;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 默认 RedisConnectionFactory 工厂
 *
 * @author surezzzzzz
 */
public class DefaultRedisConnectionFactoryFactory implements RedisConnectionFactoryFactory {

    @Override
    public RedisConnectionFactory create(String datasourceKey, SimpleRedisRouteProperties.DataSourceConfig config) {
        try {
            RedisSourceMode mode = RedisSourceMode.fromCode(config.getMode());
            LettuceClientConfiguration clientConfiguration = createClientConfiguration(config, mode);
            LettuceConnectionFactory connectionFactory;
            if (mode == RedisSourceMode.CLUSTER) {
                connectionFactory = new LettuceConnectionFactory(createClusterConfiguration(config), clientConfiguration);
            } else {
                connectionFactory = new LettuceConnectionFactory(createStandaloneConfiguration(config), clientConfiguration);
            }
            connectionFactory.afterPropertiesSet();
            return connectionFactory;
        } catch (Exception e) {
            throw new ConfigurationException(ErrorCode.REDIS_ROUTE_006,
                    String.format(ErrorMessage.DATASOURCE_CREATE_FAILED, datasourceKey), e);
        }
    }

    private RedisStandaloneConfiguration createStandaloneConfiguration(SimpleRedisRouteProperties.DataSourceConfig config) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(config.getHost());
        configuration.setPort(config.getPort());
        configuration.setDatabase(config.getDatabase());
        RedisConfigurationCompatibilityHelper.applyUsername(configuration, config.getUsername());
        RedisConfigurationCompatibilityHelper.applyPassword(configuration, config.getPassword());
        return configuration;
    }

    private RedisClusterConfiguration createClusterConfiguration(SimpleRedisRouteProperties.DataSourceConfig config) {
        RedisClusterConfiguration configuration = new RedisClusterConfiguration(config.getNodes());
        if (config.getMaxRedirects() != null) {
            configuration.setMaxRedirects(config.getMaxRedirects());
        }
        RedisConfigurationCompatibilityHelper.applyUsername(configuration, config.getUsername());
        RedisConfigurationCompatibilityHelper.applyPassword(configuration, config.getPassword());
        return configuration;
    }

    private LettuceClientConfiguration createClientConfiguration(SimpleRedisRouteProperties.DataSourceConfig config,
                                                                 RedisSourceMode mode) {
        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .shutdownTimeout(Duration.ofMillis(config.getLettuce().getShutdownTimeoutMs()));
        if (config.isSsl()) {
            builder.useSsl();
        }
        RedisConfigurationCompatibilityHelper.applyClientName(builder, config.getClientName());
        applyClientOptions(builder, config, mode);
        return builder.build();
    }

    private void applyClientOptions(LettuceClientConfiguration.LettuceClientConfigurationBuilder builder,
                                    SimpleRedisRouteProperties.DataSourceConfig config,
                                    RedisSourceMode mode) {
        try {
            Object socketOptions = createSocketOptions(config.getConnectTimeoutMs());
            Object clientOptions = mode == RedisSourceMode.CLUSTER
                    ? createClusterClientOptions(config, socketOptions)
                    : createClientOptions(config, socketOptions);
            applyClientOptions(builder, clientOptions);
        } catch (Exception ignored) {
            // 低版本 Lettuce API 差异时保留 command timeout，由连接阶段暴露真实问题。
        }
    }

    private Object createSocketOptions(long connectTimeoutMs) throws Exception {
        Class<?> socketOptionsClass = Class.forName("io.lettuce.core.SocketOptions");
        Object socketBuilder = invokeStatic(socketOptionsClass, "builder");
        invoke(socketBuilder, "connectTimeout", Duration.ofMillis(connectTimeoutMs));
        return invoke(socketBuilder, "build");
    }

    private Object createClientOptions(SimpleRedisRouteProperties.DataSourceConfig config,
                                       Object socketOptions) throws Exception {
        Class<?> clientOptionsClass = Class.forName("io.lettuce.core.ClientOptions");
        Object clientBuilder = invokeStatic(clientOptionsClass, "builder");
        applyBaseClientOptions(clientBuilder, config, socketOptions);
        return invoke(clientBuilder, "build");
    }

    private Object createClusterClientOptions(SimpleRedisRouteProperties.DataSourceConfig config,
                                              Object socketOptions) throws Exception {
        Class<?> clusterClientOptionsClass = Class.forName("io.lettuce.core.cluster.ClusterClientOptions");
        Object clusterBuilder = invokeStatic(clusterClientOptionsClass, "builder");
        applyBaseClientOptions(clusterBuilder, config, socketOptions);
        Object topologyRefreshOptions = createClusterTopologyRefreshOptions(config);
        invoke(clusterBuilder, "topologyRefreshOptions", topologyRefreshOptions);
        return invoke(clusterBuilder, "build");
    }

    private void applyBaseClientOptions(Object clientBuilder,
                                        SimpleRedisRouteProperties.DataSourceConfig config,
                                        Object socketOptions) throws Exception {
        invoke(clientBuilder, "socketOptions", socketOptions);
        invokeIfPresent(clientBuilder, "autoReconnect", config.getLettuce().isAutoReconnect());
        invokeIfPresent(clientBuilder, "requestQueueSize", config.getLettuce().getRequestQueueSize());
        applyDisconnectedBehavior(clientBuilder, config.getLettuce().isRejectCommandsWhenDisconnected());
    }

    private Object createClusterTopologyRefreshOptions(SimpleRedisRouteProperties.DataSourceConfig config) throws Exception {
        Class<?> refreshOptionsClass = Class.forName("io.lettuce.core.cluster.ClusterTopologyRefreshOptions");
        Object refreshBuilder = invokeStatic(refreshOptionsClass, "builder");
        if (config.getLettuce().isClusterAdaptiveRefresh()) {
            invokeIfPresent(refreshBuilder, "enableAllAdaptiveRefreshTriggers");
        }
        if (config.getLettuce().isClusterPeriodicRefresh()) {
            Duration refreshPeriod = Duration.ofMillis(config.getLettuce().getClusterRefreshPeriodMs());
            if (!invokeIfPresent(refreshBuilder, "enablePeriodicRefresh", refreshPeriod)) {
                invokeIfPresent(refreshBuilder, "enablePeriodicRefresh", true);
                invokeIfPresent(refreshBuilder, "refreshPeriod", refreshPeriod);
            }
        }
        return invoke(refreshBuilder, "build");
    }

    private void applyDisconnectedBehavior(Object clientBuilder, boolean rejectCommandsWhenDisconnected) throws Exception {
        Class<?> behaviorClass = Class.forName("io.lettuce.core.ClientOptions$DisconnectedBehavior");
        String behaviorName = rejectCommandsWhenDisconnected ? "REJECT_COMMANDS" : "DEFAULT";
        Object behavior = Enum.valueOf((Class<Enum>) behaviorClass.asSubclass(Enum.class), behaviorName);
        invokeIfPresent(clientBuilder, "disconnectedBehavior", behavior);
    }

    private void applyClientOptions(LettuceClientConfiguration.LettuceClientConfigurationBuilder builder,
                                    Object clientOptions) {
        Method method = findCompatibleMethod(builder.getClass(), "clientOptions", new Object[]{clientOptions});
        if (method != null) {
            ReflectionUtils.makeAccessible(method);
            ReflectionUtils.invokeMethod(method, builder, clientOptions);
        }
    }

    private Object invokeStatic(Class<?> type, String methodName) throws Exception {
        Method method = type.getMethod(methodName);
        return method.invoke(null);
    }

    private Object invoke(Object target, String methodName, Object... args) throws Exception {
        Method method = findCompatibleMethod(target.getClass(), methodName, args);
        if (method == null) {
            throw new NoSuchMethodException(target.getClass().getName() + "#" + methodName);
        }
        return method.invoke(target, args);
    }

    private boolean invokeIfPresent(Object target, String methodName, Object... args) throws Exception {
        Method method = findCompatibleMethod(target.getClass(), methodName, args);
        if (method == null) {
            return false;
        }
        method.invoke(target, args);
        return true;
    }

    private Method findCompatibleMethod(Class<?> type, String methodName, Object[] args) {
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            if (!method.getName().equals(methodName) || method.getParameterTypes().length != args.length) {
                continue;
            }
            boolean matched = true;
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (args[i] != null && !isAssignable(parameterTypes[i], args[i].getClass())) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return method;
            }
        }
        return null;
    }

    private boolean isAssignable(Class<?> parameterType, Class<?> argType) {
        if (!parameterType.isPrimitive()) {
            return parameterType.isAssignableFrom(argType);
        }
        if (parameterType == boolean.class) {
            return argType == Boolean.class;
        }
        if (parameterType == int.class) {
            return argType == Integer.class;
        }
        if (parameterType == long.class) {
            return argType == Long.class;
        }
        if (parameterType == double.class) {
            return argType == Double.class;
        }
        if (parameterType == float.class) {
            return argType == Float.class;
        }
        if (parameterType == short.class) {
            return argType == Short.class;
        }
        if (parameterType == byte.class) {
            return argType == Byte.class;
        }
        if (parameterType == char.class) {
            return argType == Character.class;
        }
        return false;
    }
}

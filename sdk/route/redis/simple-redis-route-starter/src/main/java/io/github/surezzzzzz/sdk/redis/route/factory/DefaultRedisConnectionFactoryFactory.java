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

    private static final String LETTUCE_SOCKET_OPTIONS_CLASS = "io.lettuce.core.SocketOptions";
    private static final String LETTUCE_CLIENT_OPTIONS_CLASS = "io.lettuce.core.ClientOptions";
    private static final String LETTUCE_CLUSTER_CLIENT_OPTIONS_CLASS = "io.lettuce.core.cluster.ClusterClientOptions";
    private static final String LETTUCE_CLUSTER_TOPOLOGY_REFRESH_OPTIONS_CLASS = "io.lettuce.core.cluster.ClusterTopologyRefreshOptions";
    private static final String LETTUCE_DISCONNECTED_BEHAVIOR_CLASS = "io.lettuce.core.ClientOptions$DisconnectedBehavior";

    private static final String METHOD_BUILDER = "builder";
    private static final String METHOD_BUILD = "build";
    private static final String METHOD_CONNECT_TIMEOUT = "connectTimeout";
    private static final String METHOD_SOCKET_OPTIONS = "socketOptions";
    private static final String METHOD_AUTO_RECONNECT = "autoReconnect";
    private static final String METHOD_REQUEST_QUEUE_SIZE = "requestQueueSize";
    private static final String METHOD_TOPOLOGY_REFRESH_OPTIONS = "topologyRefreshOptions";
    private static final String METHOD_ENABLE_ALL_ADAPTIVE_REFRESH_TRIGGERS = "enableAllAdaptiveRefreshTriggers";
    private static final String METHOD_ENABLE_PERIODIC_REFRESH = "enablePeriodicRefresh";
    private static final String METHOD_REFRESH_PERIOD = "refreshPeriod";
    private static final String METHOD_DISCONNECTED_BEHAVIOR = "disconnectedBehavior";
    private static final String METHOD_CLIENT_OPTIONS = "clientOptions";

    private static final String DISCONNECTED_BEHAVIOR_REJECT_COMMANDS = "REJECT_COMMANDS";
    private static final String DISCONNECTED_BEHAVIOR_DEFAULT = "DEFAULT";

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
        // Lettuce 5.x / 6.x 的 builder API 有差异，生产安全默认值通过反射按可用方法设置。
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
        Class<?> socketOptionsClass = Class.forName(LETTUCE_SOCKET_OPTIONS_CLASS);
        Object socketBuilder = invokeStatic(socketOptionsClass, METHOD_BUILDER);
        invoke(socketBuilder, METHOD_CONNECT_TIMEOUT, Duration.ofMillis(connectTimeoutMs));
        return invoke(socketBuilder, METHOD_BUILD);
    }

    private Object createClientOptions(SimpleRedisRouteProperties.DataSourceConfig config,
                                       Object socketOptions) throws Exception {
        Class<?> clientOptionsClass = Class.forName(LETTUCE_CLIENT_OPTIONS_CLASS);
        Object clientBuilder = invokeStatic(clientOptionsClass, METHOD_BUILDER);
        applyBaseClientOptions(clientBuilder, config, socketOptions);
        return invoke(clientBuilder, METHOD_BUILD);
    }

    private Object createClusterClientOptions(SimpleRedisRouteProperties.DataSourceConfig config,
                                              Object socketOptions) throws Exception {
        Class<?> clusterClientOptionsClass = Class.forName(LETTUCE_CLUSTER_CLIENT_OPTIONS_CLASS);
        Object clusterBuilder = invokeStatic(clusterClientOptionsClass, METHOD_BUILDER);
        applyBaseClientOptions(clusterBuilder, config, socketOptions);
        Object topologyRefreshOptions = createClusterTopologyRefreshOptions(config);
        invoke(clusterBuilder, METHOD_TOPOLOGY_REFRESH_OPTIONS, topologyRefreshOptions);
        return invoke(clusterBuilder, METHOD_BUILD);
    }

    private void applyBaseClientOptions(Object clientBuilder,
                                        SimpleRedisRouteProperties.DataSourceConfig config,
                                        Object socketOptions) throws Exception {
        invoke(clientBuilder, METHOD_SOCKET_OPTIONS, socketOptions);
        invokeIfPresent(clientBuilder, METHOD_AUTO_RECONNECT, config.getLettuce().isAutoReconnect());
        invokeIfPresent(clientBuilder, METHOD_REQUEST_QUEUE_SIZE, config.getLettuce().getRequestQueueSize());
        applyDisconnectedBehavior(clientBuilder, config.getLettuce().isRejectCommandsWhenDisconnected());
    }

    private Object createClusterTopologyRefreshOptions(SimpleRedisRouteProperties.DataSourceConfig config) throws Exception {
        Class<?> refreshOptionsClass = Class.forName(LETTUCE_CLUSTER_TOPOLOGY_REFRESH_OPTIONS_CLASS);
        Object refreshBuilder = invokeStatic(refreshOptionsClass, METHOD_BUILDER);
        if (config.getLettuce().isClusterAdaptiveRefresh()) {
            invokeIfPresent(refreshBuilder, METHOD_ENABLE_ALL_ADAPTIVE_REFRESH_TRIGGERS);
        }
        if (config.getLettuce().isClusterPeriodicRefresh()) {
            Duration refreshPeriod = Duration.ofMillis(config.getLettuce().getClusterRefreshPeriodMs());
            if (!invokeIfPresent(refreshBuilder, METHOD_ENABLE_PERIODIC_REFRESH, refreshPeriod)) {
                invokeIfPresent(refreshBuilder, METHOD_ENABLE_PERIODIC_REFRESH, true);
                invokeIfPresent(refreshBuilder, METHOD_REFRESH_PERIOD, refreshPeriod);
            }
        }
        return invoke(refreshBuilder, METHOD_BUILD);
    }

    private void applyDisconnectedBehavior(Object clientBuilder, boolean rejectCommandsWhenDisconnected) throws Exception {
        Class<?> behaviorClass = Class.forName(LETTUCE_DISCONNECTED_BEHAVIOR_CLASS);
        String behaviorName = rejectCommandsWhenDisconnected ? DISCONNECTED_BEHAVIOR_REJECT_COMMANDS : DISCONNECTED_BEHAVIOR_DEFAULT;
        Object behavior = Enum.valueOf((Class<Enum>) behaviorClass.asSubclass(Enum.class), behaviorName);
        invokeIfPresent(clientBuilder, METHOD_DISCONNECTED_BEHAVIOR, behavior);
    }

    private void applyClientOptions(LettuceClientConfiguration.LettuceClientConfigurationBuilder builder,
                                    Object clientOptions) {
        Method method = findCompatibleMethod(builder.getClass(), METHOD_CLIENT_OPTIONS, new Object[]{clientOptions});
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

package io.github.surezzzzzz.sdk.kafka.route.support;

import io.github.surezzzzzz.sdk.kafka.route.constant.SimpleKafkaRouteConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Kafka AdminClient 兼容 Helper
 *
 * @author surezzzzzz
 */
@Slf4j
public final class KafkaAdminCompatibilityHelper {

    public static final String CLUSTER_ID = "clusterId";
    public static final String NODE_COUNT = "nodeCount";
    public static final String CONTROLLER_VISIBLE = "controllerVisible";

    private KafkaAdminCompatibilityHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 创建短生命周期 AdminClient
     *
     * @param config admin client 配置
     * @return AdminClient
     */
    public static Object createAdminClient(Map<String, Object> config) {
        return AdminClient.create(config);
    }

    /**
     * 关闭 AdminClient
     *
     * @param adminClient AdminClient
     */
    public static void closeAdminClient(Object adminClient) {
        if (adminClient == null) {
            return;
        }
        try {
            KafkaReflectionHelper.invokeIfPresent(adminClient, SimpleKafkaRouteConstant.REFLECT_METHOD_CLOSE);
        } catch (RuntimeException e) {
            log.warn("Kafka route 诊断 AdminClient 关闭失败", e);
        }
    }

    /**
     * 探测 Kafka cluster 基础信息
     *
     * @param adminClient AdminClient
     * @param timeoutMs 超时时间
     * @return cluster 描述信息
     */
    public static Map<String, Object> describeCluster(Object adminClient, long timeoutMs) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (adminClient == null) {
            return result;
        }
        try {
            Object describeResult = KafkaReflectionHelper.invokeIfPresent(adminClient, SimpleKafkaRouteConstant.REFLECT_METHOD_DESCRIBE_CLUSTER);
            if (describeResult == null) {
                return result;
            }
            Object clusterId = getKafkaFutureValue(describeResult, SimpleKafkaRouteConstant.REFLECT_METHOD_CLUSTER_ID, timeoutMs);
            Object nodes = getKafkaFutureValue(describeResult, SimpleKafkaRouteConstant.REFLECT_METHOD_NODES, timeoutMs);
            Object controller = getKafkaFutureValue(describeResult, SimpleKafkaRouteConstant.REFLECT_METHOD_CONTROLLER, timeoutMs);
            result.put(CLUSTER_ID, clusterId);
            result.put(NODE_COUNT, nodes instanceof Collection ? ((Collection<?>) nodes).size() : -1);
            result.put(CONTROLLER_VISIBLE, controller != null);
            return result;
        } catch (RuntimeException e) {
            log.warn("Kafka route describeCluster 探测失败，已降级为空结果，exception=[{}]", e.getClass().getSimpleName());
            return new LinkedHashMap<>();
        }
    }

    /**
     * 尝试探测 Kafka features
     *
     * @param adminClient AdminClient
     * @param timeoutMs 超时时间
     * @return feature 元数据，无法探测时返回 null
     */
    public static Object describeFeaturesIfAvailable(Object adminClient, long timeoutMs) {
        if (adminClient == null) {
            return null;
        }
        Method method = KafkaReflectionHelper.findMethod(adminClient.getClass(), SimpleKafkaRouteConstant.REFLECT_METHOD_DESCRIBE_FEATURES);
        if (method == null) {
            return null;
        }
        try {
            Object describeResult = KafkaReflectionHelper.invoke(method, adminClient);
            Object features = getKafkaFutureValue(describeResult, SimpleKafkaRouteConstant.REFLECT_METHOD_FEATURE_METADATA, timeoutMs);
            return hasFeatureMetadata(features) ? features : null;
        } catch (RuntimeException e) {
            log.warn("Kafka route describeFeatures 探测失败，已降级为 UNKNOWN，exception=[{}]", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 提取 cluster id
     *
     * @param clusterDesc cluster 描述
     * @return cluster id
     */
    public static String extractClusterId(Map<String, Object> clusterDesc) {
        if (clusterDesc == null) {
            return null;
        }
        Object clusterId = clusterDesc.get(CLUSTER_ID);
        return clusterId == null ? null : String.valueOf(clusterId);
    }

    /**
     * 提取 broker 节点数
     *
     * @param clusterDesc cluster 描述
     * @return 节点数
     */
    public static int extractNodeCount(Map<String, Object> clusterDesc) {
        if (clusterDesc == null) {
            return -1;
        }
        Object nodeCount = clusterDesc.get(NODE_COUNT);
        return nodeCount instanceof Number ? ((Number) nodeCount).intValue() : -1;
    }

    /**
     * 判断 controller 是否可见
     *
     * @param clusterDesc cluster 描述
     * @return true 表示可见
     */
    public static boolean extractControllerVisible(Map<String, Object> clusterDesc) {
        if (clusterDesc == null) {
            return false;
        }
        return Boolean.TRUE.equals(clusterDesc.get(CONTROLLER_VISIBLE));
    }

    private static boolean hasFeatureMetadata(Object features) {
        if (features == null) {
            return false;
        }
        Object finalizedFeatures = KafkaReflectionHelper.invokeIfPresent(features,
                SimpleKafkaRouteConstant.REFLECT_METHOD_FINALIZED_FEATURES);
        return isNonEmptyMap(finalizedFeatures);
    }

    private static boolean isNonEmptyMap(Object value) {
        return value instanceof Map && !((Map<?, ?>) value).isEmpty();
    }

    private static Object getKafkaFutureValue(Object target, String methodName, long timeoutMs) {
        Object future = KafkaReflectionHelper.invokeIfPresent(target, methodName);
        if (future == null) {
            return null;
        }
        Method getMethod = KafkaReflectionHelper.findMethod(future.getClass(), SimpleKafkaRouteConstant.REFLECT_METHOD_GET, long.class, TimeUnit.class);
        if (getMethod == null) {
            return KafkaReflectionHelper.invokeIfPresent(future, SimpleKafkaRouteConstant.REFLECT_METHOD_GET);
        }
        return KafkaReflectionHelper.invoke(getMethod, future, timeoutMs, TimeUnit.MILLISECONDS);
    }
}

package io.github.surezzzzzz.sdk.redis.route.configuration;

import io.github.surezzzzzz.sdk.redis.route.constant.RedisSourceMode;
import io.github.surezzzzzz.sdk.redis.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.redis.route.constant.SimpleRedisRouteConstant;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple Redis Route 配置
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleRedisRouteConstant.CONFIG_PREFIX)
public class SimpleRedisRouteProperties {

    /**
     * 是否启用路由
     */
    private boolean enable = false;

    /**
     * 默认数据源 key
     */
    private String defaultSource = SimpleRedisRouteConstant.DEFAULT_DATASOURCE_KEY;

    /**
     * 数据源配置
     */
    private Map<String, DataSourceConfig> sources = new HashMap<>();

    /**
     * 路由规则
     */
    private List<RouteRule> rules = new ArrayList<>();

    /**
     * 探测配置
     */
    private ProbeConfig probe = new ProbeConfig();

    /**
     * Redis 数据源配置
     */
    @Data
    @ToString(exclude = "password")
    public static class DataSourceConfig {

        /**
         * Redis 部署模式
         */
        private String mode = RedisSourceMode.STANDALONE.getCode();

        /**
         * Redis 主机，standalone 模式使用
         */
        private String host = SimpleRedisRouteConstant.DEFAULT_HOST;

        /**
         * Redis 端口，standalone 模式使用
         */
        private int port = SimpleRedisRouteConstant.DEFAULT_PORT;

        /**
         * Redis Cluster 节点，cluster 模式使用
         */
        private List<String> nodes = new ArrayList<>();

        /**
         * Redis Cluster 最大重定向次数
         */
        private Integer maxRedirects = SimpleRedisRouteConstant.DEFAULT_CLUSTER_MAX_REDIRECTS;

        /**
         * Redis database，cluster 模式固定使用 0
         */
        private int database = SimpleRedisRouteConstant.DEFAULT_DATABASE;

        /**
         * Redis data node 用户名
         */
        private String username;

        /**
         * Redis data node 密码
         */
        private String password;

        /**
         * 是否启用 SSL
         */
        private boolean ssl = false;

        /**
         * 命令超时时间，毫秒
         */
        private long timeoutMs = SimpleRedisRouteConstant.DEFAULT_TIMEOUT_MS;

        /**
         * 连接超时时间，毫秒
         */
        private long connectTimeoutMs = SimpleRedisRouteConstant.DEFAULT_CONNECT_TIMEOUT_MS;

        /**
         * 客户端名称
         */
        private String clientName;

        /**
         * Lettuce 配置
         */
        private LettuceConfig lettuce = new LettuceConfig();
    }

    /**
     * Lettuce 配置
     */
    @Data
    public static class LettuceConfig {

        /**
         * Lettuce 连接工厂关闭超时，毫秒
         */
        private long shutdownTimeoutMs = SimpleRedisRouteConstant.DEFAULT_LETTUCE_SHUTDOWN_TIMEOUT_MS;

        /**
         * 是否启用自动重连
         */
        private boolean autoReconnect = true;

        /**
         * 连接断开时是否拒绝命令入队
         */
        private boolean rejectCommandsWhenDisconnected = true;

        /**
         * Lettuce 请求队列上限
         */
        private int requestQueueSize = SimpleRedisRouteConstant.DEFAULT_LETTUCE_REQUEST_QUEUE_SIZE;

        /**
         * 是否启用 Redis Cluster 自适应拓扑刷新
         */
        private boolean clusterAdaptiveRefresh = true;

        /**
         * 是否启用 Redis Cluster 周期性拓扑刷新
         */
        private boolean clusterPeriodicRefresh = true;

        /**
         * Redis Cluster 周期性拓扑刷新间隔，毫秒
         */
        private long clusterRefreshPeriodMs = SimpleRedisRouteConstant.DEFAULT_LETTUCE_CLUSTER_REFRESH_PERIOD_MS;
    }

    /**
     * 探测配置
     */
    @Data
    public static class ProbeConfig {

        /**
         * 是否在启动时探测 Redis Server 信息（版本号、部署模式）
         */
        private boolean serverInfo = SimpleRedisRouteConstant.DEFAULT_PROBE_SERVER_INFO;
    }

    /**
     * 路由规则配置
     */
    @Data
    public static class RouteRule {

        /**
         * 匹配表达式
         */
        private String pattern;

        /**
         * 匹配类型
         */
        private String type = RouteMatchType.EXACT.getCode();

        /**
         * 目标数据源
         */
        private String datasource;

        /**
         * 优先级，数字越小越优先
         */
        private int priority = SimpleRedisRouteConstant.DEFAULT_RULE_PRIORITY;

        /**
         * 是否启用
         */
        private boolean enable = true;
    }
}

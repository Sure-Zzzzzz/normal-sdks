package io.github.surezzzzzz.sdk.cache.pubsub;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.cache.L1Cache;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cache Invalidation Listener
 * <p>
 * 缓存失效监听器（Pub/Sub）
 * </p>
 *
 * @author Sure
 */
@Slf4j
@SmartCacheComponent
@ConditionalOnClass(RedisMessageListenerContainer.class)
public class CacheInvalidationListener implements MessageListener {

    @Autowired(required = false)
    private L1Cache l1Cache;

    @Autowired
    private SmartCacheProperties properties;

    @Autowired
    @Qualifier("smartCacheRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private RedisMessageListenerContainer listenerContainer;

    /**
     * 线程计数器，用于生成唯一的线程名称
     */
    private final AtomicInteger threadCounter = new AtomicInteger(0);

    /**
     * 异步消息处理线程池
     * 核心线程数 2，最大线程数 4，队列容量 1000
     */
    private final ExecutorService messageExecutor = new ThreadPoolExecutor(
            2, 4,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r, "cache-invalidation-" + threadCounter.incrementAndGet());
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 缓存序列化器实例，避免每次反序列化时都获取
     */
    private RedisSerializer<?> serializer;

    @PostConstruct
    public void init() {
        // 缓存序列化器实例
        if (redisTemplate != null) {
            serializer = redisTemplate.getValueSerializer();
        }

        // 只有强一致性模式才启动 Pub/Sub 订阅
        if (properties == null || !SmartCacheConstant.CONSISTENCY_MODE_STRONG.equals(properties.getConsistency().getMode())) {
            log.info("Cache invalidation listener skipped (consistency mode is not strong)");
            return;
        }

        if (listenerContainer != null) {
            try {
                String channelPrefix = properties.getPubsubChannelPrefix();
                String me = properties.getMe();
                String channel = channelPrefix + SmartCacheConstant.KEY_SEPARATOR + me + SmartCacheConstant.KEY_SEPARATOR + "*";
                listenerContainer.addMessageListener(this, new PatternTopic(channel));

                // 手动启动容器，并捕获可能的连接异常
                try {
                    listenerContainer.start();
                    log.info("Cache invalidation listener initialized successfully, channel: {}", channel);
                } catch (Exception startEx) {
                    log.warn("Failed to start Redis Pub/Sub listener, strong consistency will not work. " +
                            "This is expected if Redis is not available. Error: {}", startEx.getMessage());
                    // 不抛出异常，允许容器继续启动
                }
            } catch (Exception e) {
                log.warn("Failed to subscribe to Redis Pub/Sub channel, strong consistency will not work. " +
                        "This is expected if Redis is not available. Error: {}", e.getMessage());
                // 不抛出异常，允许容器继续启动
            }
        } else {
            log.info("RedisMessageListenerContainer is not available, Pub/Sub will not work");
        }
    }

    @PreDestroy
    public void destroy() {
        if (messageExecutor != null) {
            messageExecutor.shutdown();
            try {
                if (!messageExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    messageExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                messageExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        // 异步处理消息，避免阻塞 Redis 监听线程
        messageExecutor.submit(() -> processMessage(message));
    }

    /**
     * 处理缓存失效消息
     */
    private void processMessage(Message message) {
        try {
            // 使用缓存的序列化器实例
            CacheInvalidationMessage msg = (CacheInvalidationMessage) serializer.deserialize(message.getBody());

            if (msg == null) {
                return;
            }

            // 忽略自己发送的消息
            String me = properties != null ? properties.getMe() : null;
            String sender = msg.getSender();
            if (me != null && me.equals(sender)) {
                return;
            }

            // 处理失效消息
            if (SmartCacheConstant.OPERATION_EVICT.equals(msg.getOperation())) {
                if (l1Cache != null) {
                    l1Cache.evict(msg.getCacheName(), msg.getKey());
                    log.trace("Received cache evict message: cacheName={}, key={}",
                            msg.getCacheName(), msg.getKey());
                }
            } else if (SmartCacheConstant.OPERATION_CLEAR.equals(msg.getOperation())) {
                if (l1Cache != null) {
                    l1Cache.clear(msg.getCacheName());
                    log.trace("Received cache clear message: cacheName={}", msg.getCacheName());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process cache invalidation message", e);
        }
    }

    /**
     * 发布缓存失效消息
     *
     * @param cacheName 缓存名称
     * @param key       缓存键
     * @param operation 操作类型（evict/clear）
     */
    public void publishInvalidation(String cacheName, String key, String operation) {
        if (properties == null || !SmartCacheConstant.CONSISTENCY_MODE_STRONG.equals(properties.getConsistency().getMode())) {
            return;
        }
        try {
            String channelPrefix = properties.getPubsubChannelPrefix();
            String me = properties.getMe();
            String channel = KeyHelper.buildPubSubChannel(channelPrefix, me, cacheName);
            CacheInvalidationMessage msg = new CacheInvalidationMessage(
                    cacheName, key, operation, me);
            redisTemplate.convertAndSend(channel, msg);
            log.trace("Published cache invalidation message: cacheName={}, key={}, operation={}",
                    cacheName, key, operation);
        } catch (Exception e) {
            log.error("Failed to publish cache invalidation message", e);
        }
    }
}

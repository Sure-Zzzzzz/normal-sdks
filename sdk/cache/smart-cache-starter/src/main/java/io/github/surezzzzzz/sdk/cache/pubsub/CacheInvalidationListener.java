package io.github.surezzzzzz.sdk.cache.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheConfigurationException;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存失效监听器
 *
 * @author surezzzzzz
 */
@Slf4j
public class CacheInvalidationListener implements MessageListener, InitializingBean {

    @Autowired(required = false)
    private L1Cache l1Cache;

    @Autowired
    private SmartCacheProperties properties;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier(SmartCacheConstant.SMART_CACHE_OBJECT_MAPPER_BEAN_NAME)
    private ObjectMapper smartCacheObjectMapper;

    @Getter
    private final String instanceId = UUID.randomUUID().toString();

    private RedisMessageListenerContainer listenerContainer;

    private final AtomicInteger threadCounter = new AtomicInteger(0);

    private final ExecutorService messageExecutor = new ThreadPoolExecutor(
            SmartCacheConstant.INVALIDATION_EXECUTOR_CORE_THREADS,
            SmartCacheConstant.INVALIDATION_EXECUTOR_MAX_THREADS,
            SmartCacheConstant.INVALIDATION_EXECUTOR_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(SmartCacheConstant.INVALIDATION_EXECUTOR_QUEUE_CAPACITY),
            r -> {
                Thread thread = new Thread(r, SmartCacheConstant.INVALIDATION_THREAD_NAME_PREFIX
                        + threadCounter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Override
    public void afterPropertiesSet() {
        if (properties == null || !SmartCacheConstant.CONSISTENCY_MODE_STRONG.equals(properties.getConsistency().getMode())) {
            log.info("缓存强一致性未开启，跳过 Pub/Sub 订阅初始化");
            return;
        }
        if (SmartCacheConstant.PUBSUB_MODE_DISABLED.equals(properties.getPubsub().getMode())) {
            throw new CacheConfigurationException(
                    ErrorCode.SMART_CACHE_CONFIG_ERROR,
                    String.format(ErrorMessage.SMART_CACHE_CONFIG_ERROR, "强一致性模式不能关闭 Pub/Sub")
            );
        }
        try {
            String routeProbeChannel = KeyHelper.buildPubSubChannel(properties.getPubsubChannelPrefix(), properties.getMe(),
                    SmartCacheConstant.PUBSUB_ROUTE_PROBE_KEY);
            String channelPattern = KeyHelper.buildPubSubChannel(properties.getPubsubChannelPrefix(), properties.getMe(), "*");
            listenerContainer = new RedisMessageListenerContainer();
            listenerContainer.setConnectionFactory(redisRouteTemplate.connectionFactoryByKey(routeProbeChannel));
            listenerContainer.addMessageListener(this, new PatternTopic(channelPattern));
            listenerContainer.afterPropertiesSet();
            listenerContainer.start();
            log.info("缓存失效 Pub/Sub 订阅初始化完成，channelPattern：{}", channelPattern);
        } catch (Exception e) {
            throw new CacheConfigurationException(
                    ErrorCode.SMART_CACHE_PUBSUB_INIT_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_PUBSUB_INIT_FAILED, e.getMessage()),
                    e
            );
        }
    }

    @PreDestroy
    public void destroy() {
        if (listenerContainer != null) {
            try {
                listenerContainer.stop();
                listenerContainer.destroy();
            } catch (Exception e) {
                log.warn("销毁缓存失效 Pub/Sub 监听容器失败：{}", e.getMessage());
            }
        }
        messageExecutor.shutdown();
        try {
            if (!messageExecutor.awaitTermination(SmartCacheConstant.EXECUTOR_SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                messageExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            messageExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (messageExecutor.isShutdown()) {
            log.warn("缓存失效监听器正在关闭，跳过新消息处理");
            return;
        }
        try {
            messageExecutor.submit(() -> processMessage(message));
        } catch (RejectedExecutionException e) {
            log.warn("缓存失效监听器无法提交消息处理任务，原因：{}", e.getMessage());
        }
    }

    private void processMessage(Message message) {
        try {
            CacheInvalidationMessage msg = smartCacheObjectMapper.readValue(message.getBody(), CacheInvalidationMessage.class);
            if (msg == null || instanceId.equals(msg.getSender())) {
                return;
            }
            if (SmartCacheConstant.OPERATION_EVICT.equals(msg.getOperation())) {
                if (l1Cache != null) {
                    l1Cache.evict(msg.getCacheName(), msg.getKey());
                    log.debug("收到缓存删除消息，cacheName：{}，key：{}", msg.getCacheName(), msg.getKey());
                }
            } else if (SmartCacheConstant.OPERATION_CLEAR.equals(msg.getOperation())) {
                if (l1Cache != null) {
                    l1Cache.clear(msg.getCacheName());
                    log.debug("收到缓存清空消息，cacheName：{}", msg.getCacheName());
                }
            }
        } catch (Exception e) {
            log.error("处理缓存失效消息失败", e);
        }
    }

    public void publishInvalidation(String cacheName, String key, String operation) {
        if (properties == null || !SmartCacheConstant.CONSISTENCY_MODE_STRONG.equals(properties.getConsistency().getMode())) {
            return;
        }
        if (SmartCacheConstant.PUBSUB_MODE_DISABLED.equals(properties.getPubsub().getMode())) {
            return;
        }
        try {
            String channel = KeyHelper.buildPubSubChannel(properties.getPubsubChannelPrefix(), properties.getMe(), cacheName);
            CacheInvalidationMessage msg = new CacheInvalidationMessage(cacheName, key, operation, instanceId);
            String payload = smartCacheObjectMapper.writeValueAsString(msg);
            String routeProbeChannel = KeyHelper.buildPubSubChannel(properties.getPubsubChannelPrefix(), properties.getMe(),
                    SmartCacheConstant.PUBSUB_ROUTE_PROBE_KEY);
            redisRouteTemplate.execute(routeProbeChannel, template -> {
                template.convertAndSend(channel, payload);
                return null;
            });
            log.debug("发布缓存失效消息，cacheName：{}，key：{}，operation：{}", cacheName, key, operation);
        } catch (Exception e) {
            log.error("发布缓存失效消息失败", e);
        }
    }
}

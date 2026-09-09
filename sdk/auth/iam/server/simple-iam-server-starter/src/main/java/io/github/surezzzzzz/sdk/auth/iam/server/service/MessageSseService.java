package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamRedisJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 站内信 SSE 推送服务（多实例分布式 + 多端语义）
 *
 * <p>本地维护每个收件人用户与一个或多个 SSE 连接的映射（多端并存追加不覆盖，
 * 每个连接记录其 Servlet 会话哈希）；未读数变更、用户级登出清理与单端登出
 * 按哈希精确踢均经 Redis Pub/Sub 广播到同 me 的全部实例——登出端连接可能挂
 * 在任意实例，须即时断流。其余撤销路径由推送懒校验兜底断流。</p>
 *
 * <p>连接活性三道防线：30s 心跳帧维持反向代理空闲链路；业务推送前按哈希懒校验
 * 会话活性（只读不续期——推送不是用户活跃信号）；30 分钟连接超时封顶泄漏窗口。</p>
 *
 * <p>广播为 best-effort：Redis 故障时降级为单机行为（仅本地 emitter 可达），
 * 未读数由页面刷新补偿，登出清理由懒校验与连接超时兜底，不影响业务事务。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class MessageSseService implements InitializingBean, DisposableBean, MessageListener {

    /**
     * SSE 连接超时时间：30 分钟，连接生命周期封顶与泄漏窗口上限
     * （连接活性由推送懒校验守，不与会话有效期对齐）
     */
    public static final long SSE_TIMEOUT_MS = 30L * 60 * 1000;

    /**
     * 心跳间隔：30 秒，防止反向代理掐断空闲长连接（心跳不触发懒校验）
     */
    public static final long SSE_HEARTBEAT_MS = 30L * 1000;

    /**
     * 广播事件类型：未读数推送
     */
    public static final String BROADCAST_TYPE_UNREAD_COUNT = "UNREAD_COUNT";

    /**
     * 广播事件类型：登出清理 emitter
     */
    public static final String BROADCAST_TYPE_EVICT = "EVICT";

    /**
     * 广播事件类型：单端登出按 Servlet 会话哈希精确踢（跨实例即时断流）
     */
    public static final String BROADCAST_TYPE_EVICT_HASH = "EVICT_HASH";

    /**
     * 事件名：未读数
     */
    public static final String EVENT_UNREAD_COUNT = "unread-count";

    /**
     * 事件名：连接就绪（首帧之前的心跳）
     */
    public static final String EVENT_READY = "ready";

    private final RedisKeyHelper redisKeyHelper;
    private final RedisRouteTemplate redisRouteTemplate;
    private final SessionService sessionService;
    private final IamRedisJsonCodec jsonCodec = new IamRedisJsonCodec();
    private final String instanceId = UUID.randomUUID().toString();
    private final Map<Long, CopyOnWriteArrayList<EmitterBinding>> userEmitters = new ConcurrentHashMap<>();

    private RedisMessageListenerContainer listenerContainer;
    private ScheduledExecutorService heartbeatExecutor;
    private String broadcastChannel;

    private static String shortId(String id) {
        return id == null ? "null" : id.substring(0, 8);
    }

    /**
     * 订阅 Redis 广播频道并启动 SSE 心跳线程
     */
    @Override
    public void afterPropertiesSet() {
        broadcastChannel = redisKeyHelper.buildPubSubChannel(SimpleIamServerConstant.BUSINESS_MESSAGE_SSE);
        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(redisRouteTemplate.connectionFactoryByKey(broadcastChannel));
        listenerContainer.addMessageListener(this, new ChannelTopic(broadcastChannel));
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "iam-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeatExecutor.scheduleWithFixedDelay(this::sendHeartbeatToAll,
                SSE_HEARTBEAT_MS, SSE_HEARTBEAT_MS, TimeUnit.MILLISECONDS);
        log.info("站内信 SSE 广播订阅就绪：channel={}, instanceId={}", broadcastChannel, instanceId);
    }

    /**
     * 停止心跳线程并销毁广播订阅容器
     */
    @Override
    public void destroy() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
        if (listenerContainer != null) {
            try {
                listenerContainer.destroy();
            } catch (Exception exception) {
                log.debug("SSE 广播订阅容器销毁异常，已忽略：{}", exception.getMessage());
            }
        }
    }

    /**
     * 收到他实例广播：按类型分发到本实例在线 SSE 流
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            BroadcastPayload payload = jsonCodec.read(
                    new String(message.getBody(), StandardCharsets.UTF_8), BroadcastPayload.class);
            if (payload == null || instanceId.equals(payload.instanceId)) {
                return;
            }
            if (BROADCAST_TYPE_EVICT_HASH.equals(payload.type)) {
                if (payload.servletSessionIdHash != null) {
                    evictLocalByServletSessionIdHash(payload.servletSessionIdHash);
                    log.info("跨实例单端登出精确清理 SSE 连接：hash={}, from={}",
                            payload.servletSessionIdHash, shortId(payload.instanceId));
                }
                return;
            }
            if (payload.userId == null) {
                return;
            }
            log.debug("收到他实例 SSE 广播：type={}, userId={}, from={}",
                    payload.type, payload.userId, shortId(payload.instanceId));
            if (BROADCAST_TYPE_UNREAD_COUNT.equals(payload.type) && payload.unreadCount != null) {
                pushToLocalEmitters(payload.userId, payload.unreadCount);
            } else if (BROADCAST_TYPE_EVICT.equals(payload.type)) {
                evictLocalUserEmitters(payload.userId);
                log.info("跨实例清理登出用户的 SSE 连接：userId={}, from={}",
                        payload.userId, shortId(payload.instanceId));
            }
        } catch (Exception exception) {
            log.warn("站内信 SSE 广播消息处理失败，已忽略", exception);
        }
    }

    /**
     * 为用户注册一个 SSE 连接，并记录连接所属的 Servlet 会话哈希（单端登出精确踢的依据）
     *
     * @param userId               收件人用户ID
     * @param servletSessionIdHash 注册连接的 Servlet 会话 ID 哈希，取不到时传 null（防御桶，登出不碰、懒校验兜底）
     * @return 已注册的 emitter
     */
    public SseEmitter register(Long userId, String servletSessionIdHash) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        EmitterBinding binding = new EmitterBinding(emitter, servletSessionIdHash);
        userEmitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(binding);

        emitter.onCompletion(() -> removeEmitter(userId, binding));
        emitter.onTimeout(() -> {
            emitter.complete();
            removeEmitter(userId, binding);
        });
        emitter.onError(throwable -> {
            emitter.complete();
            removeEmitter(userId, binding);
        });

        sendSafely(userId, binding, SseEmitter.event().name(EVENT_READY).data("connected"));
        return emitter;
    }

    /**
     * 向指定用户推送最新未读数（本地投递 + 跨实例广播）
     *
     * @param userId      收件人用户ID
     * @param unreadCount 最新未读数
     */
    public void pushUnreadCount(Long userId, long unreadCount) {
        pushToLocalEmitters(userId, unreadCount);
        broadcast(BROADCAST_TYPE_UNREAD_COUNT, userId, unreadCount);
    }

    /**
     * 只向指定 emitter 推送未读数，用于新连接首帧，避免影响其他已连接标签页
     */
    public void pushUnreadCount(Long userId, SseEmitter emitter, long unreadCount) {
        EmitterBinding binding = new EmitterBinding(emitter, null);
        sendSafely(userId, binding, SseEmitter.event().name(EVENT_UNREAD_COUNT).data(unreadCount));
    }

    /**
     * 单端登出精确清理：只踢该 Servlet 会话哈希对应的连接，同用户其他端不受影响
     * （本地清理 + 跨实例 EVICT_HASH 广播——登出端连接可能挂在任意实例，须即时断流）
     *
     * @param servletSessionIdHash 登出端的 Servlet 会话 ID 哈希
     */
    public void evictByServletSessionIdHash(String servletSessionIdHash) {
        if (servletSessionIdHash == null) {
            return;
        }
        evictLocalByServletSessionIdHash(servletSessionIdHash);
        broadcastEvictHash(servletSessionIdHash);
    }

    private void evictLocalByServletSessionIdHash(String servletSessionIdHash) {
        for (Map.Entry<Long, CopyOnWriteArrayList<EmitterBinding>> entry : userEmitters.entrySet()) {
            for (EmitterBinding binding : entry.getValue()) {
                if (servletSessionIdHash.equals(binding.servletSessionIdHash)) {
                    binding.emitter.complete();
                    removeEmitter(entry.getKey(), binding);
                    log.debug("单端登出精确清理 SSE 连接：userId={}", entry.getKey());
                }
            }
        }
    }

    /**
     * 用户级吊销清理全部端（本地清理 + 跨实例广播）：禁用 / 删除 / 重置密码路径调用
     */
    public void evictUserEmitters(Long userId) {
        evictLocalUserEmitters(userId);
        broadcast(BROADCAST_TYPE_EVICT, userId, null);
        log.debug("用户级吊销，清除全部 SSE 连接：userId={}", userId);
    }

    /**
     * 向全部连接发送心跳帧（comment 帧，前端 EventSource 无感知）。
     * 心跳是链路保活不是业务推送：不触发懒校验，不断流挂机连接。
     * public 供测试直接驱动单轮心跳（调度周期本身不单测）
     */
    public void sendHeartbeatToAll() {
        for (Map.Entry<Long, CopyOnWriteArrayList<EmitterBinding>> entry : userEmitters.entrySet()) {
            for (EmitterBinding binding : entry.getValue()) {
                sendSafely(entry.getKey(), binding, SseEmitter.event().comment("keepalive"));
            }
        }
    }

    private void pushToLocalEmitters(Long userId, long unreadCount) {
        CopyOnWriteArrayList<EmitterBinding> bindings = userEmitters.get(userId);
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        for (EmitterBinding binding : bindings) {
            // 懒校验（只读不续期）：会话已死的连接断流，不投递；
            // 无哈希的防御桶连接跳过校验（SSE 推送不是用户活跃信号，绝不续期）
            if (binding.servletSessionIdHash != null
                    && sessionService.findActiveByServletSessionIdHash(binding.servletSessionIdHash) == null) {
                binding.emitter.complete();
                removeEmitter(userId, binding);
                log.debug("懒校验判定会话已死，断开 SSE 连接：userId={}", userId);
                continue;
            }
            sendSafely(userId, binding, SseEmitter.event().name(EVENT_UNREAD_COUNT).data(unreadCount));
        }
    }

    private void evictLocalUserEmitters(Long userId) {
        CopyOnWriteArrayList<EmitterBinding> bindings = userEmitters.remove(userId);
        if (bindings != null) {
            for (EmitterBinding binding : bindings) {
                binding.emitter.complete();
            }
        }
    }

    private void broadcast(String type, Long userId, Long unreadCount) {
        try {
            BroadcastPayload payload = new BroadcastPayload();
            payload.type = type;
            payload.userId = userId;
            payload.unreadCount = unreadCount;
            payload.instanceId = instanceId;
            redisRouteTemplate.stringTemplateByKey(broadcastChannel)
                    .convertAndSend(broadcastChannel, jsonCodec.write(payload));
            log.debug("SSE 广播已发布：type={}, userId={}, instance={}", type, userId, shortId(instanceId));
        } catch (Exception exception) {
            log.warn("站内信 SSE 广播发布失败，降级为单机推送：userId={}, type={}", userId, type, exception);
        }
    }

    private void broadcastEvictHash(String servletSessionIdHash) {
        try {
            BroadcastPayload payload = new BroadcastPayload();
            payload.type = BROADCAST_TYPE_EVICT_HASH;
            payload.servletSessionIdHash = servletSessionIdHash;
            payload.instanceId = instanceId;
            redisRouteTemplate.stringTemplateByKey(broadcastChannel)
                    .convertAndSend(broadcastChannel, jsonCodec.write(payload));
            log.debug("SSE 广播已发布：type={}, instance={}", BROADCAST_TYPE_EVICT_HASH, shortId(instanceId));
        } catch (Exception exception) {
            log.warn("站内信 SSE 单端踢广播发布失败，降级为单机清理：type={}", BROADCAST_TYPE_EVICT_HASH, exception);
        }
    }

    /**
     * emitter 级同步发送：SseEmitter 非线程安全，心跳与业务推送并发时串行化；
     * 发送失败（连接已断 / 已 complete）即清理该连接
     */
    private void sendSafely(Long userId, EmitterBinding binding, SseEmitter.SseEventBuilder event) {
        try {
            synchronized (binding.emitter) {
                binding.emitter.send(event);
            }
        } catch (Exception exception) {
            binding.emitter.complete();
            removeEmitter(userId, binding);
            log.debug("SSE 发送失败，移除连接：userId={}", userId, exception);
        }
    }

    /**
     * 移除用户连接（logout / 关闭浏览器时由 emitter 回调触发）
     */
    private void removeEmitter(Long userId, EmitterBinding binding) {
        userEmitters.computeIfPresent(userId, (key, bindings) -> {
            bindings.removeIf(item -> item.emitter == binding.emitter);
            if (bindings.isEmpty()) {
                return null;
            }
            return bindings;
        });
    }

    /**
     * SSE 连接绑定：emitter 与其所属 Servlet 会话哈希（多端区分依据）
     */
    private static class EmitterBinding {

        final SseEmitter emitter;
        final String servletSessionIdHash;

        EmitterBinding(SseEmitter emitter, String servletSessionIdHash) {
            this.emitter = emitter;
            this.servletSessionIdHash = servletSessionIdHash;
        }
    }

    /**
     * 跨实例广播协议载荷（public 字段供 Jackson 直接读写）
     */
    private static class BroadcastPayload {

        public String type;
        public Long userId;
        public Long unreadCount;
        public String servletSessionIdHash;
        public String instanceId;
    }
}

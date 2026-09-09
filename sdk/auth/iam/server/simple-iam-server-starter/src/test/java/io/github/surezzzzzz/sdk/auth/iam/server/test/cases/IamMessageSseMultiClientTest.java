package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.MessageSseService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.SessionService;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 站内信 SSE 多端语义测试（纯单测，无 Spring 上下文）
 *
 * <p>多端并存 / 单端登出精确踢 / 推送懒校验断死保活 / 心跳不触发懒校验。
 * 帧内容投递由 IamMessageSseBroadcastTest 与双实例手工验收覆盖；
 * 断流以 emitter 的 handler 收到 complete 调用为准（同步语义，见 attach）。</p>
 *
 * @author surezzzzzz
 */
class IamMessageSseMultiClientTest {

    private static final long USER_ID = 4321L;
    private static final String HASH_A = "hash-browser-a";
    private static final String HASH_B = "hash-browser-b";

    /**
     * ResponseBodyEmitter.Handler 是包私有接口，测试包无法 implements；
     * 以动态代理绕开可见性、反射写入 handler 字段接管 complete/send 观察。
     * 纯单测无 async 上下文，complete() 在 handler 为空时只置标志不执行任何
     * 回调，onCompletion 链路不通（javap 字节码实核），必须装 handler 才可观察。
     */
    private static final Field HANDLER_FIELD = handlerField();
    private SessionService sessionService;
    private MessageSseService sseService;
    private StringRedisTemplate redisTemplate;

    private static Field handlerField() {
        try {
            Field field = ResponseBodyEmitter.class.getDeclaredField("handler");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Message remoteMessage(String json) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private static Class<?> handlerInterface() {
        try {
            return Class.forName(
                    "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter$Handler");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("ResponseBodyEmitter$Handler 不在 classpath", exception);
        }
    }

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        RedisKeyHelper redisKeyHelper = mock(RedisKeyHelper.class);
        RedisRouteTemplate redisRouteTemplate = mock(RedisRouteTemplate.class);
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisRouteTemplate.stringTemplateByKey(any())).thenReturn(redisTemplate);
        sseService = new MessageSseService(redisKeyHelper, redisRouteTemplate, sessionService);
    }

    @Test
    @DisplayName("单端登出精确踢：只断本端连接，同用户另一端存活")
    void evictByHashOnlyKicksMatchingEnd() throws Exception {
        RecordingHandler handlerA = attach(register(USER_ID, HASH_A));
        RecordingHandler handlerB = attach(register(USER_ID, HASH_B));

        sseService.evictByServletSessionIdHash(HASH_A);

        assertTrue(handlerA.completed, "登出端连接应被踢");
        assertFalse(handlerB.completed, "另一端不应被误杀");
        // 精确踢不做会话活性查询，也不触碰用户级全端清理
        verify(sessionService, never()).findActiveByServletSessionIdHash(any());
    }

    @Test
    @DisplayName("单端精确踢应发布 EVICT_HASH 跨实例广播（登出端连接可能挂在任意实例）")
    void evictByHashBroadcastsCrossInstanceKick() {
        attach(register(USER_ID, HASH_A));

        sseService.evictByServletSessionIdHash(HASH_A);

        // 纯单测不驱动 afterPropertiesSet，broadcastChannel 为 null；
        // 生产环境由 afterPropertiesSet 从 redisKeyHelper.buildPubSubChannel 初始化
        verify(redisTemplate).convertAndSend(isNull(), argThat(json ->
                String.valueOf(json).contains(MessageSseService.BROADCAST_TYPE_EVICT_HASH)
                        && String.valueOf(json).contains(HASH_A)));
    }

    @Test
    @DisplayName("收到他实例 EVICT_HASH 广播应踢本地同哈希连接，其他端不受影响")
    void crossInstanceEvictHashKicksLocalBinding() throws Exception {
        RecordingHandler handlerA = attach(register(USER_ID, HASH_A));
        RecordingHandler handlerB = attach(register(USER_ID, HASH_B));

        sseService.onMessage(remoteMessage("{\"type\":\"EVICT_HASH\",\"servletSessionIdHash\":\""
                + HASH_A + "\",\"instanceId\":\"remote-instance-x\"}"), null);

        assertTrue(handlerA.completed, "他实例登出端连接应被踢");
        assertFalse(handlerB.completed, "本地其他端不应被误杀");
    }

    @Test
    @DisplayName("懒校验：会话已死的连接被断流，活跃连接保持")
    void lazyValidationDropsDeadSessionConnection() throws Exception {
        RecordingHandler deadHandler = attach(register(USER_ID, "hash-dead"));
        when(sessionService.findActiveByServletSessionIdHash("hash-dead")).thenReturn(null);
        when(sessionService.findActiveByServletSessionIdHash(HASH_B)).thenReturn(new IamSessionEntity());
        RecordingHandler aliveHandler = attach(register(USER_ID, HASH_B));

        sseService.pushUnreadCount(USER_ID, 5L);

        assertTrue(deadHandler.completed, "死会话连接应被断流");
        assertFalse(aliveHandler.completed, "活跃连接不应被断流");
        assertTrue(deadHandler.sent.stream().noneMatch(data -> String.valueOf(data).contains("5")),
                "死会话连接不应再收到未读数帧");
        assertTrue(aliveHandler.sent.stream().anyMatch(data -> String.valueOf(data).contains("5")),
                "活跃连接应收到未读数帧");
        verify(sessionService).findActiveByServletSessionIdHash("hash-dead");
        verify(sessionService).findActiveByServletSessionIdHash(HASH_B);
    }

    @Test
    @DisplayName("懒校验绝不续期：推送路径不触发 touchIfNeeded")
    void lazyValidationNeverRenews() {
        when(sessionService.findActiveByServletSessionIdHash(any())).thenReturn(new IamSessionEntity());
        register(USER_ID, HASH_A);

        sseService.pushUnreadCount(USER_ID, 5L);

        verify(sessionService, never()).touchIfNeeded(any());
    }

    @Test
    @DisplayName("心跳不触发懒校验、不断流挂机连接")
    void heartbeatKeepsIdleConnectionWithoutValidation() throws Exception {
        RecordingHandler handler = attach(register(USER_ID, HASH_A));

        sseService.sendHeartbeatToAll();

        assertFalse(handler.completed, "心跳不应断流连接");
        assertTrue(handler.sent.stream().anyMatch(data -> String.valueOf(data).contains("keepalive")),
                "心跳 comment 帧应送达连接");
        verify(sessionService, never()).findActiveByServletSessionIdHash(any());
    }

    @Test
    @DisplayName("用户级吊销全端踢：该用户全部连接断流")
    void userLevelEvictKicksAllEnds() throws Exception {
        RecordingHandler handlerA = attach(register(USER_ID, HASH_A));
        RecordingHandler handlerB = attach(register(USER_ID, HASH_B));

        sseService.evictUserEmitters(USER_ID);

        assertTrue(handlerA.completed);
        assertTrue(handlerB.completed);
    }

    private SseEmitter register(Long userId, String hash) {
        return sseService.register(userId, hash);
    }

    private RecordingHandler attach(SseEmitter emitter) {
        RecordingHandler recorder = new RecordingHandler();
        Object proxy = Proxy.newProxyInstance(
                ResponseBodyEmitter.class.getClassLoader(),
                new Class<?>[]{handlerInterface()},
                (proxyInstance, method, args) -> {
                    if ("send".equals(method.getName())) {
                        recorder.sent.add(args[0]);
                    } else if ("complete".equals(method.getName())) {
                        recorder.completed = true;
                    }
                    return null;
                });
        try {
            HANDLER_FIELD.set(emitter, proxy);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("写入 SSE handler 字段失败", exception);
        }
        return recorder;
    }

    private static class RecordingHandler {

        final List<Object> sent = new CopyOnWriteArrayList<>();
        volatile boolean completed;
    }
}

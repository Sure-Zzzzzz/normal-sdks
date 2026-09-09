package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.service.MessageSseService;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 站内信 SSE 跨实例广播真实 Redis 测试。
 *
 * <p>以临时订阅者挂上广播 channel，验证 pushUnreadCount / evictUserEmitters
 * 发布的 JSON 协议（type / userId / unreadCount / instanceId 字段完整、
 * instanceId 为发送方实例标识）真实落 Redis Pub/Sub——这是多实例互投的前提。</p>
 *
 * <p>本地 emitter 帧投递由 IamWebMessageSseTest（HTTP 端点）覆盖；
 * 跨实例端到端帧到达由 LOCAL_TEST_COMMANDS 双实例手工验收覆盖。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class MessageSseBroadcastTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private MessageSseService sseService;
    @Autowired
    private RedisKeyHelper redisKeyHelper;
    @Autowired
    private RedisRouteTemplate redisRouteTemplate;
    private RedisMessageListenerContainer probe;

    @AfterEach
    void stopProbe() {
        if (probe != null) {
            try {
                probe.destroy();
            } catch (Exception exception) {
                log.debug("临时订阅者销毁异常，已忽略：{}", exception.getMessage());
            }
            probe = null;
        }
    }

    @Test
    @DisplayName("pushUnreadCount 应向广播 channel 发布字段完整的 UNREAD_COUNT JSON")
    void pushUnreadCountPublishesProtocolJson() throws Exception {
        long userId = Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100000L + 100000L;
        BroadcastCollector collector = subscribe();

        sseService.pushUnreadCount(userId, 5L);

        JsonNode payload = collector.awaitNext(1).get(0);
        assertEquals(MessageSseService.BROADCAST_TYPE_UNREAD_COUNT, payload.get("type").asText());
        assertEquals(userId, payload.get("userId").asLong());
        assertEquals(5L, payload.get("unreadCount").asLong());
        assertNotNull(payload.get("instanceId"), "广播必须携带发送方 instanceId（自弃防重复帧）");
        log.info("UNREAD_COUNT 广播协议验证通过：userId={}", userId);
    }

    @Test
    @DisplayName("evictUserEmitters 应向广播 channel 发布 EVICT JSON")
    void evictUserEmittersPublishesProtocolJson() throws Exception {
        long userId = Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100000L + 100000L;
        BroadcastCollector collector = subscribe();

        sseService.evictUserEmitters(userId);

        JsonNode payload = collector.awaitNext(1).get(0);
        assertEquals(MessageSseService.BROADCAST_TYPE_EVICT, payload.get("type").asText());
        assertEquals(userId, payload.get("userId").asLong());
        assertNotNull(payload.get("instanceId"));
        log.info("EVICT 广播协议验证通过：userId={}", userId);
    }

    /**
     * 临时订阅广播 channel 并返回收集器（先订阅、后触发被测动作、再 awaitNext 断言）。
     *
     * <p>RedisMessageListenerContainer 的订阅建立是异步的，先自发自收一条探针消息
     * 确认订阅生效，避免发布早于订阅就绪的竞态。</p>
     */
    private BroadcastCollector subscribe() throws Exception {
        String channel = redisKeyHelper.buildPubSubChannel(SimpleIamServerConstant.BUSINESS_MESSAGE_SSE);
        List<JsonNode> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(1);
        MessageListener listener = (message, pattern) -> {
            try {
                JsonNode payload = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
                if ("PROBE".equals(payload.path("type").asText())) {
                    ready.countDown();
                    return;
                }
                received.add(payload);
                latch.countDown();
            } catch (Exception exception) {
                log.warn("广播消息解析失败：{}", exception.getMessage());
            }
        };
        probe = new RedisMessageListenerContainer();
        probe.setConnectionFactory(redisRouteTemplate.connectionFactoryByKey(channel));
        probe.addMessageListener(listener, new ChannelTopic(channel));
        probe.afterPropertiesSet();
        probe.start();
        redisRouteTemplate.stringTemplateByKey(channel).convertAndSend(channel, "{\"type\":\"PROBE\"}");
        assertTrue(ready.await(5, TimeUnit.SECONDS), "订阅必须在超时内生效（探针自发自收）");
        return new BroadcastCollector(received, latch);
    }

    private static final class BroadcastCollector {

        private final List<JsonNode> received;
        private final CountDownLatch latch;

        private BroadcastCollector(List<JsonNode> received, CountDownLatch latch) {
            this.received = received;
            this.latch = latch;
        }

        private List<JsonNode> awaitNext(int expected) throws InterruptedException {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "订阅者必须在超时内收到广播");
            assertEquals(expected, received.size(), "订阅者收到的广播条数");
            return received;
        }
    }
}

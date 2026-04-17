package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.cache.cache.L1Cache;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationMessage;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmartCache Pub/Sub 多实例一致性测试
 *
 * <p>方案B（消息接收）：直接发布 invalidation 消息，验证本实例 L1 被正确清除。
 * <p>方案A（双 Context）：模拟两个独立实例，验证 revoke 后 Pub/Sub 广播清除另一实例的 L1。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SmartCachePubSubTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private SmartCacheManager smartCacheManager;

    @Autowired
    private L1Cache l1Cache;

    @Autowired
    private SmartCacheProperties smartCacheProperties;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisKeyHelper redisKeyHelper;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me:test-app}")
    private String me;

    private String clientId;
    private String clientSecret;

    @BeforeEach
    void setUp() {
        ClientInfoResponse client = clientManagementService.createPlatformClient("PubSub Test Client");
        clientId = client.getClientId();
        clientSecret = client.getClientSecret();
    }

    @AfterEach
    void tearDown() {
        clientRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ==================== 方案B：消息接收测试 ====================

    /**
     * 方案B：直接发布 invalidation 消息，验证本实例 L1 被清除
     */
    @Test
    void testPubSubMessageClearsL1() throws InterruptedException {
        log.info("========== 方案B：Pub/Sub 消息接收 -> L1 清除 ==========");

        String cacheName = RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN;
        String key = "test-token-key:null";
        String fakeValue = "fake-authorization-object";

        // 先往 L1 塞一个值
        l1Cache.put(cacheName, key, fakeValue);
        assertNotNull(l1Cache.get(cacheName, key), "L1 应该有值");
        log.info("L1 已写入测试值");

        // 模拟另一个实例发来的 Pub/Sub invalidation 消息
        CacheInvalidationMessage message = new CacheInvalidationMessage();
        message.setCacheName(cacheName);
        message.setKey(key);
        message.setOperation(SmartCacheConstant.OPERATION_EVICT);
        message.setSender("another-instance");

        // channel 格式与 CacheInvalidationListener.publishInvalidation() 保持一致
        String channel = KeyHelper.buildPubSubChannel(
                smartCacheProperties.getPubsubChannelPrefix(),
                smartCacheProperties.getMe(),
                cacheName
        );
        redisTemplate.convertAndSend(channel, message);
        log.info("已发布 invalidation 消息到 channel: {}", channel);

        // 等待 Pub/Sub 处理
        Thread.sleep(300);

        Object l1Value = l1Cache.get(cacheName, key);
        assertNull(l1Value, "收到 Pub/Sub 消息后 L1 应该被清除");
        log.info("✓ L1 已被 Pub/Sub 消息清除");
    }

    /**
     * 方案B：SmartCacheManager.evict 触发 Pub/Sub，验证本实例 L1 立即清除
     */
    @Test
    void testSmartCacheEvictClearsL1() {
        log.info("========== 方案B：SmartCacheManager.evict -> L1 立即清除 ==========");

        String cacheName = RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN;
        String key = "test-evict-key:null";
        String fakeValue = "fake-value";

        // 写入 L1
        l1Cache.put(cacheName, key, fakeValue);
        assertNotNull(l1Cache.get(cacheName, key), "L1 应该有值");

        // 通过 SmartCacheManager evict
        smartCacheManager.evict(cacheName, key);

        // L1 应立即清除（同步）
        assertNull(l1Cache.get(cacheName, key), "evict 后 L1 应立即清除");
        log.info("✓ evict 后 L1 立即清除");
    }

    // ==================== 方案A：双 Context 多实例测试 ====================

    /**
     * 方案A：启动两个独立 Spring Context，模拟两个实例
     * 实例1 revoke token，验证实例2 的 L1 通过 Pub/Sub 被清除
     */
    @Test
    @Tag("multi-instance")
    void testPubSubClearsL1OnAnotherInstance() throws Exception {
        log.info("========== 方案A：双 Context Pub/Sub 多实例测试 ==========");

        // 获取 token（通过当前实例）
        String token = fetchToken(clientId, clientSecret);
        assertNotNull(token, "应该能获取到 token");
        log.info("获取 token 成功");

        // 启动第二个 Context（模拟另一个实例）
        ConfigurableApplicationContext instance2 = null;
        try {
            instance2 = SpringApplication.run(
                    SimpleAkskServerTestApplication.class,
                    "--server.port=0",
                    "--spring.profiles.active=local",
                    "--io.github.surezzzzzz.sdk.cache.key-prefix=sure-auth-aksk",
                    "--io.github.surezzzzzz.sdk.cache.me=test-app",
                    "--io.github.surezzzzzz.sdk.auth.aksk.server.redis.token.me=test-app",
                    "--io.github.surezzzzzz.sdk.cache.l1.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.l1.expire-seconds=10",
                    "--io.github.surezzzzzz.sdk.cache.l2.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.l2.key-format={keyPrefix}:{me}:{cacheName}::{key}",
                    "--io.github.surezzzzzz.sdk.cache.consistency.mode=strong"
            );
            log.info("实例2 启动成功");

            // 等待 Pub/Sub 监听器初始化完成
            Thread.sleep(3000);

            L1Cache instance2L1 = instance2.getBean(L1Cache.class);
            SmartCacheProperties instance2Props = instance2.getBean(SmartCacheProperties.class);

            // 打印实例1和实例2的 me 值，确认一致
            log.info("实例1 me: {}, 实例2 me: {}", smartCacheProperties.getMe(), instance2Props.getMe());
            assertEquals(smartCacheProperties.getMe(), instance2Props.getMe(), "两个实例的 me 值应该相同");

            // 验证 CacheInvalidationListener 已订阅频道
            try {
                io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener listener =
                        instance2.getBean(io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener.class);
                assertNotNull(listener, "实例2 的 CacheInvalidationListener 应该存在");
                log.info("实例2 CacheInvalidationListener 已初始化");
            } catch (Exception e) {
                log.warn("实例2 CacheInvalidationListener 可能未初始化: {}", e.getMessage());
            }

            // 先让实例2 的 L1 缓存这个 token（模拟实例2 曾经 introspect 过）
            String cacheKey = redisKeyHelper.buildCacheKeyByToken(token, null);
            instance2L1.put(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN, cacheKey, "fake-cached-value");
            assertNotNull(
                    instance2L1.get(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN, cacheKey),
                    "实例2 L1 应该有缓存值"
            );
            log.info("实例2 L1 已写入缓存值");

            // 实例1（当前实例）revoke token，触发 Pub/Sub 广播
            revokeToken(clientId, clientSecret, token);
            log.info("实例1 已撤销 token，Pub/Sub 广播中...");

            // 等待 Pub/Sub 传播（增加等待时间确保消息到达）
            Thread.sleep(3000);

            // 验证实例2 的 L1 已被清除
            Object instance2L1Value = instance2L1.get(RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN, cacheKey);
            assertNull(instance2L1Value, "Pub/Sub 广播后实例2 的 L1 应该被清除");
            log.info("✓ 实例2 L1 已通过 Pub/Sub 清除");

        } finally {
            if (instance2 != null) {
                instance2.close();
                log.info("实例2 已关闭");
            }
        }
    }

    // ==================== 工具方法 ====================

    private String fetchToken(String clientId, String clientSecret) {
        String url = "http://localhost:" + port + "/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("grant_type", "client_credentials");
        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<MultiValueMap<String, String>>(body, headers), Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return (String) response.getBody().get("access_token");
    }

    private void revokeToken(String clientId, String clientSecret, String token) {
        String url = "http://localhost:" + port + "/oauth2/revoke";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<String, String>();
        body.add("token", token);
        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<MultiValueMap<String, String>>(body, headers), Void.class);
    }
}

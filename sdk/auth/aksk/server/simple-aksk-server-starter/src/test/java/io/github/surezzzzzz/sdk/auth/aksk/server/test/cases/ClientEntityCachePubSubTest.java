package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.CachedOAuth2RegisteredClientEntityService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Client Entity 缓存 Pub/Sub 多实例一致性测试
 *
 * <p>验证：写操作（create / update / disable）后，Pub/Sub 广播清除其他实例的 L1 缓存。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ClientEntityCachePubSubTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private CachedOAuth2RegisteredClientEntityService cachedClientEntityService;

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

    private String clientId;
    private String clientSecret;

    @BeforeEach
    void setUp() {
        ClientInfoResponse client = clientManagementService.createPlatformClient("PubSub Client Test");
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

    /**
     * 验证 disableClient 触发 Pub/Sub 后，其他实例的 L1 缓存被清除
     */
    @Test
    @Tag("multi-instance")
    void testDisableClientClearsL1OnAnotherInstance() throws Exception {
        log.info("========== 测试 disableClient Pub/Sub L1 清除 ==========");

        // 实例1：触发一次 /oauth2/token，把 entity 写入 L1
        warmUpCache(clientId, clientSecret);

        // 启动实例2（模拟另一个实例）
        ConfigurableApplicationContext instance2 = null;
        try {
            instance2 = SpringApplication.run(
                    SimpleAkskServerTestApplication.class,
                    "--server.port=0",
                    "--spring.profiles.active=local",
                    "--io.github.surezzzzzz.sdk.cache.key-prefix=sure-auth-aksk",
                    "--io.github.surezzzzzz.sdk.cache.me=test-app",
                    "--io.github.surezzzzzz.sdk.cache.l1.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.l1.expire-seconds=10",
                    "--io.github.surezzzzzz.sdk.cache.l2.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.l2.key-format={keyPrefix}:{me}:{cacheName}::{key}",
                    "--io.github.surezzzzzz.sdk.cache.consistency.mode=strong"
            );
            log.info("实例2 启动成功");

            Thread.sleep(3000);

            // 实例2 的 L1 写入同 clientId 的假缓存（模拟实例2 曾经处理过该 client 的请求）
            String cacheName = RedisKeyHelper.CACHE_OAUTH2_CLIENT_ENTITY;
            String cacheKey = redisKeyHelper.buildCacheKeyById(clientId);
            L1Cache instance2L1 = instance2.getBean(L1Cache.class);
            instance2L1.put(cacheName, cacheKey, "fake-cached-entity");
            assertNotNull(instance2L1.get(cacheName, cacheKey), "实例2 L1 应该有缓存值");
            log.info("实例2 L1 已写入假缓存值");

            // 实例1 执行 disableClient，触发 Pub/Sub 广播
            clientManagementService.disableClient(clientId);
            log.info("实例1 已 disable client: {}，Pub/Sub 广播中...", clientId);

            Thread.sleep(3000);

            // 验证实例2 的 L1 已被清除
            Object instance2L1Value = instance2L1.get(cacheName, cacheKey);
            assertNull(instance2L1Value, "Pub/Sub 广播后实例2 的 L1 应该被清除");
            log.info("✓ 实例2 L1 已通过 Pub/Sub 清除");

        } finally {
            if (instance2 != null) {
                instance2.close();
                log.info("实例2 已关闭");
            }
        }
    }

    /**
     * 验证 SmartCacheManager.evict 直接清除本实例 L1（单实例场景）
     */
    @Test
    void testEvictClearsLocalL1() {
        log.info("========== 测试 evict 清除本实例 L1 ==========");

        // 先通过 cachedClientEntityService 把 entity 写入 L1
        cachedClientEntityService.findByClientId(clientId);

        String cacheName = RedisKeyHelper.CACHE_OAUTH2_CLIENT_ENTITY;
        String cacheKey = redisKeyHelper.buildCacheKeyById(clientId);
        assertNotNull(l1Cache.get(cacheName, cacheKey), "L1 应该有缓存值");
        log.info("L1 已写入缓存值");

        // evict
        cachedClientEntityService.evict(clientId);

        assertNull(l1Cache.get(cacheName, cacheKey), "evict 后 L1 应立即清除");
        log.info("✓ evict 后本实例 L1 立即清除");
    }

    /**
     * 验证 Pub/Sub 消息直接清除本实例 L1
     */
    @Test
    void testPubSubMessageClearsLocalL1() throws InterruptedException {
        log.info("========== 测试 Pub/Sub 消息清除本实例 L1 ==========");

        String cacheName = RedisKeyHelper.CACHE_OAUTH2_CLIENT_ENTITY;
        String cacheKey = redisKeyHelper.buildCacheKeyById(clientId);
        String fakeValue = "fake-client-entity";

        l1Cache.put(cacheName, cacheKey, fakeValue);
        assertNotNull(l1Cache.get(cacheName, cacheKey), "L1 应该有值");
        log.info("L1 已写入测试值");

        // 模拟另一个实例发来的 Pub/Sub invalidation 消息
        CacheInvalidationMessage message = new CacheInvalidationMessage();
        message.setCacheName(cacheName);
        message.setKey(cacheKey);
        message.setOperation(SmartCacheConstant.OPERATION_EVICT);
        message.setSender("another-instance");

        String channel = KeyHelper.buildPubSubChannel(
                smartCacheProperties.getPubsubChannelPrefix(),
                smartCacheProperties.getMe(),
                cacheName
        );
        redisTemplate.convertAndSend(channel, message);
        log.info("已发布 invalidation 消息到 channel: {}", channel);

        Thread.sleep(300);

        Object l1Value = l1Cache.get(cacheName, cacheKey);
        assertNull(l1Value, "收到 Pub/Sub 消息后 L1 应该被清除");
        log.info("✓ L1 已被 Pub/Sub 消息清除");
    }

    private void warmUpCache(String clientId, String clientSecret) {
        // 触发一次 token 获取，让缓存预热
        String tokenUrl = "http://localhost:" + port + "/oauth2/token";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("scope", "read write");
        restTemplate.postForEntity(tokenUrl, new HttpEntity<>(body, headers), String.class);
    }
}
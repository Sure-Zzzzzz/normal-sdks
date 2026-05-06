package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.SimpleAkskRedisTokenManagerTestApplication;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationMessage;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisTokenManager Pub/Sub 多实例 L1 一致性测试
 *
 * <p>方案B（消息接收）：直接发布 invalidation 消息，验证本实例 L1 被正确清除。
 * <p>方案A（双 Context）：模拟两个独立实例，验证 clearToken() 后 Pub/Sub 广播清除另一实例的 L1。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskRedisTokenManagerTestApplication.class)
class RedisTokenManagerPubSubTest {

    @Autowired
    private RedisTokenManager tokenManager;

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private L1Cache l1Cache;

    @Autowired
    private SmartCacheProperties smartCacheProperties;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${io.github.surezzzzzz.sdk.auth.aksk.client.redis.token.cache-name:aksk-client-token}")
    private String cacheName;

    @BeforeEach
    void setUp() {
        cleanupTestKeys();
    }

    @AfterEach
    void tearDown() {
        cleanupTestKeys();
    }

    private void cleanupTestKeys() {
        Set<String> keys = stringRedisTemplate.keys("sure-auth-aksk-client:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    /**
     * 方案B：直接发布 invalidation 消息，验证本实例 L1 被清除
     */
    @Test
    void testPubSubMessageClearsL1() throws InterruptedException {
        log.info("========== 方案B：Pub/Sub 消息接收 -> L1 清除 ==========");

        String cacheKey = "default";
        String fakeToken = "fake-token-for-pubsub-test";

        // 先往 L1 塞一个值
        l1Cache.put(cacheName, cacheKey, fakeToken);
        assertNotNull(l1Cache.get(cacheName, cacheKey), "L1 应该有值");
        log.info("L1 已写入测试值");

        // 模拟另一个实例发来的 Pub/Sub invalidation 消息
        CacheInvalidationMessage message = new CacheInvalidationMessage();
        message.setCacheName(cacheName);
        message.setKey(cacheKey);
        message.setOperation(SmartCacheConstant.OPERATION_EVICT);
        message.setSender("another-instance-uuid");

        String channel = KeyHelper.buildPubSubChannel(
                smartCacheProperties.getPubsubChannelPrefix(),
                smartCacheProperties.getMe(),
                cacheName
        );
        redisTemplate.convertAndSend(channel, message);
        log.info("已发布 invalidation 消息到 channel: {}", channel);

        // 等待 Pub/Sub 处理
        Thread.sleep(300);

        Object l1Value = l1Cache.get(cacheName, cacheKey);
        assertNull(l1Value, "收到 Pub/Sub 消息后 L1 应该被清除");
        log.info("✓ L1 已被 Pub/Sub 消息清除");
    }

    /**
     * 方案B：SmartCacheManager.evict 触发 Pub/Sub，验证本实例 L1 立即清除
     */
    @Test
    void testClearTokenEvictsCacheAndClearsL1() {
        log.info("========== 方案B：clearToken -> evict -> L1 立即清除 ==========");

        String cacheKey = "default";
        String fakeToken = "fake-token-evict-test";

        // 写入 L1
        l1Cache.put(cacheName, cacheKey, fakeToken);
        assertNotNull(l1Cache.get(cacheName, cacheKey), "L1 应该有值");

        // clearToken 内部调 cacheManager.evict，同步清除 L1
        tokenManager.clearToken();

        assertNull(l1Cache.get(cacheName, cacheKey), "clearToken 后 L1 应立即清除");
        log.info("✓ clearToken 后 L1 立即清除");
    }

    /**
     * 方案A：启动两个独立 Spring Context，模拟两个实例
     * 实例1 clearToken()，验证实例2 的 L1 通过 Pub/Sub 被清除
     */
    @Test
    void testPubSubClearsL1OnAnotherInstance() throws Exception {
        log.info("========== 方案A：双 Context Pub/Sub 多实例测试 ==========");

        // 先获取一个真实 token
        String token = tokenManager.getToken();
        assertNotNull(token, "应该能获取到 token");
        log.info("获取 token 成功");

        ConfigurableApplicationContext instance2 = null;
        try {
            // 启动第二个 Context（模拟另一个实例，me 相同）
            instance2 = SpringApplication.run(
                    SimpleAkskRedisTokenManagerTestApplication.class,
                    "--server.port=0",
                    "--spring.profiles.active=local",
                    "--io.github.surezzzzzz.sdk.cache.key-prefix=sure-auth-aksk-client",
                    "--io.github.surezzzzzz.sdk.cache.me=redis-token-manager-test",
                    "--io.github.surezzzzzz.sdk.cache.l1.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.l1.expire-seconds=10",
                    "--io.github.surezzzzzz.sdk.cache.l2.enabled=true",
                    "--io.github.surezzzzzz.sdk.cache.l2.expire-seconds=3600",
                    "--io.github.surezzzzzz.sdk.cache.consistency.mode=strong"
            );
            log.info("实例2 启动成功");

            // 等待 Pub/Sub 监听器初始化
            Thread.sleep(3000);

            L1Cache instance2L1 = instance2.getBean(L1Cache.class);

            // 验证 CacheInvalidationListener 已初始化
            try {
                io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener listener =
                        instance2.getBean(io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener.class);
                assertNotNull(listener, "实例2 的 CacheInvalidationListener 应该存在");
                log.info("实例2 CacheInvalidationListener 已初始化");
            } catch (Exception e) {
                fail("实例2 CacheInvalidationListener 未初始化，Pub/Sub 测试无效: " + e.getMessage());
            }

            // 让实例2 的 L1 缓存这个 token（模拟实例2 曾经获取过）
            String cacheKey = "default";
            instance2L1.put(cacheName, cacheKey, token);
            assertNotNull(instance2L1.get(cacheName, cacheKey), "实例2 L1 应该有缓存值");
            log.info("实例2 L1 已写入缓存值");

            // 实例1 clearToken，触发 Pub/Sub 广播
            tokenManager.clearToken();
            log.info("实例1 已 clearToken，Pub/Sub 广播中...");

            // 等待 Pub/Sub 传播
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
}

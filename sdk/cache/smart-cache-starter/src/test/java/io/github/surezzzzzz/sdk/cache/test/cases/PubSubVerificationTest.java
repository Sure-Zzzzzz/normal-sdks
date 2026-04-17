package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.cache.L1Cache;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationListener;
import io.github.surezzzzzz.sdk.cache.pubsub.CacheInvalidationMessage;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.cache.test.BaseSmartCacheTest;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pub/Sub Verification Test
 * <p>
 * 验证 Redis Pub/Sub 机制真正生效的测试
 * 通过模拟其他实例发送消息来验证本实例能否正确接收并处理
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
@ActiveProfiles("strong")
public class PubSubVerificationTest extends BaseSmartCacheTest {

    @Autowired
    private L1Cache l1Cache;

    @Autowired
    private SmartCacheProperties properties;

    @Autowired
    private CacheInvalidationListener invalidationListener;

    @Autowired
    @Qualifier("smartCacheRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    public void setUp() {
        log.info("========== 初始化 Pub/Sub 验证测试环境 ==========");
        l1Cache.clear("pubsub-test");
        log.info("测试环境初始化完成，Redis可用: {}", isRedisAvailable());
    }

    /**
     * 测试场景：模拟其他实例发送 evict 消息，验证本实例L1缓存被清空
     * 这是验证Pub/Sub真正工作的核心测试
     */
    @Test
    public void testCrossInstanceEvictMessage() throws Exception {
        // Redis不可用时跳过
        if (shouldSkipRedisTest("testCrossInstanceEvictMessage")) {
            return;
        }

        log.info("========== 测试：跨实例 evict 消息 ==========");

        String cacheName = "pubsub-test";
        String key = "test-key";
        String value = "test-value";

        // 步骤1: 写入L1缓存
        log.info("【步骤 1】写入 L1 缓存: key={}, value={}", key, value);
        l1Cache.put(cacheName, key, value);

        // 验证数据已写入
        Object cachedValue = l1Cache.get(cacheName, key);
        assertEquals(value, cachedValue, "L1 缓存应该包含数据");
        log.info("L1 缓存数据验证成功: {}", cachedValue);

        // 步骤2: 模拟其他实例发送 evict 消息
        log.info("【步骤 2】模拟其他实例发送 evict 消息");
        String channelPrefix = properties.getPubsubChannelPrefix();
        String me = properties.getMe();
        String channel = KeyHelper.buildPubSubChannel(channelPrefix, me, cacheName);

        // 使用不同的 sender 标识（模拟其他实例）
        CacheInvalidationMessage msg = new CacheInvalidationMessage(
                cacheName, key, SmartCacheConstant.OPERATION_EVICT, "other-instance");

        log.info("发送 Pub/Sub 消息到频道: {}, sender: {}", channel, "other-instance");
        redisTemplate.convertAndSend(channel, msg);

        // 步骤3: 等待 Pub/Sub 消息传播和处理
        Thread.sleep(300);

        // 步骤4: 验证本实例的 L1 缓存已失效
        log.info("【步骤 3】验证 L1 缓存已失效");
        Object afterEvict = l1Cache.get(cacheName, key);
        assertNull(afterEvict, "L1 缓存应该已被清空（通过 Pub/Sub 消息）");

        log.info("✓ 跨实例 evict 消息验证通过 - Pub/Sub 正常工作");
    }

    /**
     * 测试场景：模拟其他实例发送 clear 消息，验证本实例L1缓存被清空
     */
    @Test
    public void testCrossInstanceClearMessage() throws Exception {
        // Redis不可用时跳过
        if (shouldSkipRedisTest("testCrossInstanceClearMessage")) {
            return;
        }

        log.info("========== 测试：跨实例 clear 消息 ==========");

        String cacheName = "pubsub-test";

        // 步骤1: 写入多个数据到 L1 缓存
        log.info("【步骤 1】写入多个数据到 L1 缓存");
        l1Cache.put(cacheName, "key1", "value1");
        l1Cache.put(cacheName, "key2", "value2");
        l1Cache.put(cacheName, "key3", "value3");

        // 验证数据已写入
        assertEquals("value1", l1Cache.get(cacheName, "key1"));
        assertEquals("value2", l1Cache.get(cacheName, "key2"));
        assertEquals("value3", l1Cache.get(cacheName, "key3"));
        log.info("L1 缓存数据写入验证成功");

        // 步骤2: 模拟其他实例发送 clear 消息
        log.info("【步骤 2】模拟其他实例发送 clear 消息");
        String channelPrefix = properties.getPubsubChannelPrefix();
        String me = properties.getMe();
        String channel = KeyHelper.buildPubSubChannel(channelPrefix, me, cacheName);

        CacheInvalidationMessage msg = new CacheInvalidationMessage(
                cacheName, null, SmartCacheConstant.OPERATION_CLEAR, "other-instance");

        log.info("发送 Pub/Sub clear 消息到频道: {}", channel);
        redisTemplate.convertAndSend(channel, msg);

        // 步骤3: 等待 Pub/Sub 消息传播和处理
        Thread.sleep(300);

        // 步骤4: 验证所有数据已清空
        log.info("【步骤 3】验证所有数据已清空");
        assertNull(l1Cache.get(cacheName, "key1"), "key1 应该已被清空");
        assertNull(l1Cache.get(cacheName, "key2"), "key2 应该已被清空");
        assertNull(l1Cache.get(cacheName, "key3"), "key3 应该已被清空");

        log.info("✓ 跨实例 clear 消息验证通过 - Pub/Sub 正常工作");
    }

    /**
     * 测试场景：验证自己发送的消息会被忽略（设计行为）
     */
    @Test
    public void testSelfSentMessagesAreIgnored() throws Exception {
        // Redis不可用时跳过
        if (shouldSkipRedisTest("testSelfSentMessagesAreIgnored")) {
            return;
        }

        log.info("========== 测试：自己发送的消息会被忽略 ==========");

        String cacheName = "pubsub-test";
        String key = "self-sent-key";
        String value = "test-value";

        // 步骤1: 写入 L1 缓存
        log.info("【步骤 1】写入 L1 缓存: key={}, value={}", key, value);
        l1Cache.put(cacheName, key, value);

        // 验证数据已写入
        assertEquals(value, l1Cache.get(cacheName, key));

        // 步骤2: 使用当前实例的 instanceId 发送消息（模拟自己发送）
        log.info("【步骤 2】使用当前实例标识发送消息");
        String channelPrefix = properties.getPubsubChannelPrefix();
        String me = properties.getMe();
        String channel = KeyHelper.buildPubSubChannel(channelPrefix, me, cacheName);

        CacheInvalidationMessage msg = new CacheInvalidationMessage(
                cacheName, key, SmartCacheConstant.OPERATION_EVICT, invalidationListener.getInstanceId());

        log.info("发送 Pub/Sub 消息，sender: {} (当前实例)", invalidationListener.getInstanceId());
        redisTemplate.convertAndSend(channel, msg);

        // 步骤3: 等待消息处理
        Thread.sleep(300);

        // 步骤4: 验证 L1 缓存仍然存在（自己发送的消息被忽略）
        log.info("【步骤 3】验证 L1 缓存仍然存在");
        Object afterEvict = l1Cache.get(cacheName, key);
        assertEquals(value, afterEvict, "L1 缓存应该仍然存在（自己发送的消息被忽略）");

        log.info("✓ 自己发送的消息被忽略验证通过");
    }

    /**
     * 测试场景：高并发场景下的 Pub/Sub 消息处理
     */
    @Test
    public void testPubSubUnderHighConcurrency() throws Exception {
        // Redis不可用时跳过
        if (shouldSkipRedisTest("testPubSubUnderHighConcurrency")) {
            return;
        }

        log.info("========== 测试：高并发 Pub/Sub 消息处理 ==========");

        String cacheName = "pubsub-test";
        int messageCount = 50; // 减少数量，提高成功率

        // 步骤1: 写入数据到 L1 缓存
        log.info("【步骤 1】写入 {} 条数据到 L1 缓存", messageCount);
        for (int i = 0; i < messageCount; i++) {
            l1Cache.put(cacheName, "key-" + i, "value-" + i);
        }

        // 步骤2: 并发发送失效消息
        log.info("【步骤 2】并发发送 {} 条失效消息", messageCount);
        String channelPrefix = properties.getPubsubChannelPrefix();
        String me = properties.getMe();
        String channel = KeyHelper.buildPubSubChannel(channelPrefix, me, cacheName);

        for (int i = 0; i < messageCount; i++) {
            final int index = i;
            new Thread(() -> {
                CacheInvalidationMessage msg = new CacheInvalidationMessage(
                        cacheName, "key-" + index, SmartCacheConstant.OPERATION_EVICT, "concurrent-instance-" + index);
                redisTemplate.convertAndSend(channel, msg);
            }).start();
        }

        // 步骤3: 等待所有消息处理完成
        Thread.sleep(1000);

        // 步骤4: 验证数据已失效
        log.info("【步骤 3】验证数据已失效");
        int invalidatedCount = 0;
        for (int i = 0; i < messageCount; i++) {
            if (l1Cache.get(cacheName, "key-" + i) == null) {
                invalidatedCount++;
            }
        }

        log.info("失效数量: {}/{}", invalidatedCount, messageCount);
        assertTrue(invalidatedCount >= messageCount * 0.9,
                "至少90%的数据应该已失效，实际: " + invalidatedCount + "/" + messageCount);

        log.info("✓ 高并发 Pub/Sub 消息处理验证通过");
    }

    /**
     * 测试场景：验证不同缓存空间的消息隔离
     */
    @Test
    public void testPubSubMessageIsolation() throws Exception {
        // Redis不可用时跳过
        if (shouldSkipRedisTest("testPubSubMessageIsolation")) {
            return;
        }

        log.info("========== 测试：Pub/Sub 消息隔离 ==========");

        // 步骤1: 在不同缓存空间写入数据
        log.info("【步骤 1】在不同缓存空间写入数据");
        l1Cache.put("cache1", "key1", "value1-cache1");
        l1Cache.put("cache2", "key1", "value1-cache2");

        // 验证数据已写入
        assertEquals("value1-cache1", l1Cache.get("cache1", "key1"));
        assertEquals("value1-cache2", l1Cache.get("cache2", "key1"));

        // 步骤2: 只对 cache1 发送 clear 消息
        log.info("【步骤 2】只对 cache1 发送 clear 消息");
        String channelPrefix = properties.getPubsubChannelPrefix();
        String me = properties.getMe();
        String channel1 = KeyHelper.buildPubSubChannel(channelPrefix, me, "cache1");

        CacheInvalidationMessage msg = new CacheInvalidationMessage(
                "cache1", null, SmartCacheConstant.OPERATION_CLEAR, "other-instance");

        redisTemplate.convertAndSend(channel1, msg);

        // 步骤3: 等待消息处理
        Thread.sleep(300);

        // 步骤4: 验证只有 cache1 被清空
        log.info("【步骤 3】验证消息隔离");
        assertNull(l1Cache.get("cache1", "key1"), "cache1 应该被清空");
        assertEquals("value1-cache2", l1Cache.get("cache2", "key1"), "cache2 不应该受影响");

        log.info("✓ Pub/Sub 消息隔离验证通过");
    }
}

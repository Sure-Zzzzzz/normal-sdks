package io.github.surezzzzzz.sdk.limiter.redis.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.limiter.redis.LimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.SimpleRedisLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.configuration.RedisLimiterProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import redis.embedded.RedisServer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author: Sure.
 * @description Redis限流器测试类
 * @Date: 2025/2/10 18:13
 */
@Slf4j
@SpringBootTest(classes = LimiterApplication.class)
@ContextConfiguration(initializers = LimiterTest.RedisInitializer.class)
public class LimiterTest {

    /**
     * Redis 初始化器 - 在 Spring Context 加载前启动 Redis
     */
    public static class RedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        private static RedisServer redisServer;
        private static int redisPort;

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            try {
                // 1. 找到可用端口并启动 Redis
                redisPort = findAvailablePort();
                log.info("=== 启动 Embedded Redis，端口: {} ===", redisPort);

                redisServer = new RedisServer(redisPort);
                redisServer.start();
                log.info("Embedded Redis 启动成功");

                // 2. 设置 Redis 连接属性
                System.setProperty("spring.redis.host", "localhost");
                System.setProperty("spring.redis.port", String.valueOf(redisPort));

                // 3. 关键：现在 Redis 已经启动，可以安全地启用限流器配置
                System.setProperty("io.github.surezzzzzz.sdk.limiter.redis.enable", "true");

                // 4. 注册关闭钩子
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (redisServer != null) {
                        try {
                            log.info("=== 关闭 Embedded Redis ===");
                            redisServer.stop();
                            log.info("Embedded Redis 关闭成功");
                        } catch (Exception e) {
                            log.warn("Redis 关闭时出现异常（可忽略）", e);
                        }
                    }
                }));

            } catch (Exception e) {
                log.error("启动 Embedded Redis 失败", e);
                throw new RuntimeException("无法启动 Embedded Redis", e);
            }
        }

        private static int findAvailablePort() {
            try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
                return socket.getLocalPort();
            } catch (Exception e) {
                return 6380 + (int) (Math.random() * 1000);
            }
        }
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpleRedisLimiter simpleRedisLimiter;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisLimiterProperties redisLimiterProperties;

    @Test
    public void smokeTest() throws Exception {
        log.info("=== 开始限流器并发测试 ===");

        String tokenKey = redisLimiterProperties.getToken().getBucket() + ":" + redisLimiterProperties.getMe();
        String initialTokenBucketValue = stringRedisTemplate.opsForValue().get(tokenKey);
        int initialTokenCount = initialTokenBucketValue != null ? Integer.parseInt(initialTokenBucketValue) : 0;
        log.info("初始 Token 数量: {}", initialTokenCount);

        // 并发测试参数
        int numberOfThreads = 1000;
        int poolSize = Math.min(numberOfThreads, 100); // 限制线程池大小避免资源耗尽
        ExecutorService executorService = Executors.newFixedThreadPool(poolSize);

        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        List<Future<Boolean>> futures = new ArrayList<>();

        // 启动并发任务
        for (int i = 0; i < numberOfThreads; i++) {
            futures.add(executorService.submit(() -> {
                try {
                    return simpleRedisLimiter.getToken();
                } catch (Exception e) {
                    log.error("获取 Token 时发生异常", e);
                    return false;
                } finally {
                    latch.countDown();
                }
            }));
        }

        // 等待所有任务完成，设置超时
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        assertTrue(completed, "所有任务应该在 60 秒内完成");
        log.info("所有任务已完成");

        // 关闭线程池
        executorService.shutdown();
        boolean terminated = executorService.awaitTermination(30, TimeUnit.SECONDS);
        assertTrue(terminated, "线程池应该在 30 秒内关闭");

        // 验证所有任务结果
        int successCount = 0;
        for (Future<Boolean> future : futures) {
            Boolean result = future.get(5, TimeUnit.SECONDS);
            assertTrue(result, "Expected token to be true");
            if (result) {
                successCount++;
            }
        }
        log.info("成功获取 Token 的次数: {}", successCount);

        // 验证最终 Token 数量
        String finalTokenBucketValue = stringRedisTemplate.opsForValue().get(tokenKey);
        int finalTokenCount = finalTokenBucketValue != null ? Integer.parseInt(finalTokenBucketValue) : 0;
        log.info("最终 Token 数量: {}", finalTokenCount);

        // 检查 Token 消耗是否正确
        int tokenDifference = initialTokenCount - finalTokenCount;
        assertEquals(numberOfThreads, tokenDifference,
                "期望 Token 消耗数量为 " + numberOfThreads + "，实际为 " + tokenDifference);

        log.info("=== 限流器并发测试通过 ===");

        // 清理：重置 Token
        simpleRedisLimiter.resetToken();
        log.info("Token 已重置");
    }
}

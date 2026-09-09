package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM Server 启动冒烟测试
 *
 * <p>前置条件：本地 MySQL 已建库 {@code sure_auth_iam}，Redis 在 localhost:6379 可达（使用 database=2）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamServerBootTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RedisRouteTemplate redisRouteTemplate;

    @Autowired
    private SimpleRedisRouteRegistry redisRouteRegistry;

    @Autowired
    private SmartCacheManager smartCacheManager;

    @Autowired
    private SimpleRedisLock simpleRedisLock;

    @Test
    void contextLoads() {
        assertNotNull(applicationContext, "ApplicationContext 不应为空");
        assertNotNull(redisRouteTemplate, "RedisRouteTemplate 未注册");
        assertNotNull(redisRouteRegistry, "SimpleRedisRouteRegistry 未注册");
        assertEquals("default", redisRouteRegistry.getDefaultDatasourceKey(),
                "IAM Redis Route 默认数据源必须为 default");
        assertEquals(Collections.singleton("default"), redisRouteRegistry.getDatasourceKeys(),
                "IAM Redis Route 必须且只能注册 default 数据源");
        assertSame(redisRouteTemplate.stringTemplate("default"), redisRouteTemplate.stringTemplate(),
                "未指定数据源时必须使用 default Route 模板");
        assertNotNull(smartCacheManager, "SmartCacheManager 未注册");
        assertNotNull(simpleRedisLock, "SimpleRedisLock 未注册");
    }
}

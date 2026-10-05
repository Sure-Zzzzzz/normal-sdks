package io.github.surezzzzzz.sdk.limiter.redis.fixture;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 远程策略端到端测试的 Management 服务启动入口
 *
 * @author surezzzzzz
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = SmartRedisLimiterAutoConfiguration.class)
public class ManagementServerTestApplication {
}

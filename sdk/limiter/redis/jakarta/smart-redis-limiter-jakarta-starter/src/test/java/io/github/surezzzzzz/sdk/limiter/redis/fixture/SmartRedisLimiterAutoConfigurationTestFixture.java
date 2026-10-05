package io.github.surezzzzzz.sdk.limiter.redis.fixture;

import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutionResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutor;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.function.Function;

import static org.mockito.Mockito.mock;

public final class SmartRedisLimiterAutoConfigurationTestFixture {

    private SmartRedisLimiterAutoConfigurationTestFixture() {
        throw new UnsupportedOperationException("Utility class");
    }

    @TestConfiguration
    public static class UserExecutorConfiguration {

        @Bean
        public RedisRouteTemplate redisRouteTemplate() {
            return mock(RedisRouteTemplate.class);
        }

        @Bean
        public UserRedisExecutor userRedisExecutor() {
            return new UserRedisExecutor();
        }
    }

    public static class UserRedisExecutor implements SmartRedisLimiterRedisExecutor {

        @Override
        public <T> SmartRedisLimiterRedisExecutionResult<T> execute(
                String routeKey, Function<StringRedisTemplate, T> callback) {
            return SmartRedisLimiterRedisExecutionResult.<T>builder()
                    .routeKey(routeKey)
                    .routeRequired(true)
                    .build();
        }
    }
}

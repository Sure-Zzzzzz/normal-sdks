package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery.RedisKeyDiscoveryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.redis.key.discovery.RedisKeyDiscoveryRequestValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis key 发现请求边界测试。
 *
 * @author surezzzzzz
 */
class RedisKeyDiscoveryRequestValidatorTest {

    private final RedisKeyDiscoveryRequestValidator validator = new RedisKeyDiscoveryRequestValidator(16, 20);

    @Test
    void shouldAllowLiteralPrefixWithinBounds() {
        assertDoesNotThrow(() -> validator.validate(request("cache:local:", 20)));
    }

    @Test
    void shouldRejectEmptyPatternSyntaxAndInvalidSize() {
        assertRejected(null, 1, "Redis key 前缀不符合查询规范");
        assertRejected("", 1, "Redis key 前缀不符合查询规范");
        assertRejected("   ", 1, "Redis key 前缀不符合查询规范");
        assertRejected("cache:*", 1, "Redis key 前缀不符合查询规范");
        assertRejected("cache:?", 1, "Redis key 前缀不符合查询规范");
        assertRejected("cache:[a]", 1, "Redis key 前缀不符合查询规范");
        assertRejected("cache:\\", 1, "Redis key 前缀不符合查询规范");
        assertRejected("cache:\n", 1, "Redis key 前缀不符合查询规范");
        assertRejected("abcdefghijklmnopq", 1, "Redis key 前缀不符合查询规范");
        assertRejected("cache:", 0, "结果数量超出允许范围");
        assertRejected("cache:", 21, "结果数量超出允许范围");
    }

    private void assertRejected(String prefix, int size, String message) {
        MiddlewareOpsException exception = assertThrows(MiddlewareOpsException.class,
                () -> validator.validate(request(prefix, size)));
        assertEquals(400, exception.getStatus().value());
        assertEquals(message, exception.getMessage());
    }

    private RedisKeyDiscoveryRequest request(String prefix, int size) {
        return RedisKeyDiscoveryRequest.builder().datasourceKey("cache-primary").prefix(prefix).size(size).build();
    }
}

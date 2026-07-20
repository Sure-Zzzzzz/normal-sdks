package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Smart Cache Manager 类型上下文测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
class SmartCacheManagerTypeContextTest {

    @Test
    @DisplayName("测试编程式 get 会把 valueType 传给 L2")
    void shouldPassValueTypeToL2() {
        SmartCacheManager manager = new SmartCacheManager();
        L2Cache l2Cache = mock(L2Cache.class);
        CacheUser user = new CacheUser("u-001");
        ReflectionTestUtils.setField(manager, "l2Cache", l2Cache);
        when(l2Cache.get("user", "001", CacheUser.class)).thenReturn(user);

        CacheUser value = manager.get("user", "001", CacheUser.class);

        log.info("Manager 类型上下文读取结果: {}", value);
        assertEquals(user, value, "Manager 应返回 L2 按类型恢复的对象");
        verify(l2Cache).get("user", "001", CacheUser.class);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CacheUser {
        private String name;
    }
}

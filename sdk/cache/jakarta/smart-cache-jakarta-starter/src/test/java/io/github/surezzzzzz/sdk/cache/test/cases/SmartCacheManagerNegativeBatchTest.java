package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.layer.L1Cache;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.stats.CacheStatsCollector;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 批量负缓存边界测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
class SmartCacheManagerNegativeBatchTest {

    @Test
    @DisplayName("批量读取不透出 L1 空值占位且不重复查询 L2")
    void shouldHideNegativePlaceholderAndKeepItAsL1Hit() {
        SmartCacheManager manager = new SmartCacheManager();
        L1Cache l1Cache = mock(L1Cache.class);
        L2Cache l2Cache = mock(L2Cache.class);
        CacheStatsCollector statsCollector = mock(CacheStatsCollector.class);
        ReflectionTestUtils.setField(manager, "l1Cache", l1Cache);
        ReflectionTestUtils.setField(manager, "l2Cache", l2Cache);
        ReflectionTestUtils.setField(manager, "statsCollector", statsCollector);

        List<String> keys = Arrays.asList("l1-value", "l1-negative", "l2-value", "missing");
        Map<String, Object> l1Values = new HashMap<>();
        l1Values.put("l1-value", "from-l1");
        l1Values.put("l1-negative", SmartCacheConstant.NULL_PLACEHOLDER);
        when(l1Cache.getAll("cache", keys)).thenReturn(l1Values);
        Map<String, Object> l2Values = new HashMap<>();
        l2Values.put("l2-value", "from-l2");
        when(l2Cache.getAll("cache", Arrays.asList("l2-value", "missing"), String.class)).thenReturn(l2Values);

        Map<String, String> result = manager.getAll("cache", keys, String.class);

        log.info("批量负缓存边界结果：{}", result);
        assertEquals(2, result.size(), "业务结果只应包含真实缓存值");
        assertEquals("from-l1", result.get("l1-value"), "应保留 L1 实际值");
        assertEquals("from-l2", result.get("l2-value"), "应合并 L2 实际值");
        assertFalse(result.containsKey("l1-negative"), "内部空值占位不能透出到业务结果");
        assertFalse(result.containsKey("missing"), "两层未命中不应产生结果条目");
        verify(l2Cache).getAll("cache", Arrays.asList("l2-value", "missing"), String.class);
        verify(l1Cache).putAll(eq("cache"), anyMap());
        verify(statsCollector, times(2)).recordL1Hit("cache");
        verify(statsCollector).recordL2Hit("cache");
        verify(statsCollector).recordMiss("cache");
        log.info("批量负缓存边界验证通过，业务返回条目数：{}", result.size());
    }

    @Test
    @DisplayName("单条读取将 L1 空值占位计为命中且不查询 L2")
    void shouldRecordSingleNegativePlaceholderAsL1Hit() {
        SmartCacheManager manager = new SmartCacheManager();
        L1Cache l1Cache = mock(L1Cache.class);
        L2Cache l2Cache = mock(L2Cache.class);
        CacheStatsCollector statsCollector = mock(CacheStatsCollector.class);
        ReflectionTestUtils.setField(manager, "l1Cache", l1Cache);
        ReflectionTestUtils.setField(manager, "l2Cache", l2Cache);
        ReflectionTestUtils.setField(manager, "statsCollector", statsCollector);
        when(l1Cache.get("cache", "negative")).thenReturn(SmartCacheConstant.NULL_PLACEHOLDER);

        Object result = manager.get("cache", "negative");

        log.info("单条负缓存读取结果：{}", result);
        assertNull(result, "L1 空值占位应向调用方返回 null");
        verify(statsCollector).recordL1Hit("cache");
        verifyNoInteractions(l2Cache);
    }
}

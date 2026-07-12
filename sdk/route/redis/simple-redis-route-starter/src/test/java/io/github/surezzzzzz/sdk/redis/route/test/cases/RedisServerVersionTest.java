package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerVersion;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisServerVersion 解析和版本比较测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisServerVersionTest {

    @Test
    public void testParseThreeSegments() {
        RedisServerVersion v = RedisServerVersion.parse("7.2.6");
        log.info("解析结果: major={}, minor={}, patch={}, raw={}", v.getMajor(), v.getMinor(), v.getPatch(), v.getRaw());
        assertEquals(7, v.getMajor(), "major 应为 7");
        assertEquals(2, v.getMinor(), "minor 应为 2");
        assertEquals(6, v.getPatch(), "patch 应为 6");
        assertEquals("7.2.6", v.getRaw(), "raw 应保留原始版本字符串");
    }

    @Test
    public void testParseTwoSegments() {
        RedisServerVersion v = RedisServerVersion.parse("6.2");
        log.info("解析结果: major={}, minor={}, patch={}, raw={}", v.getMajor(), v.getMinor(), v.getPatch(), v.getRaw());
        assertEquals(6, v.getMajor(), "major 应为 6");
        assertEquals(2, v.getMinor(), "minor 应为 2");
        assertEquals(0, v.getPatch(), "patch 缺省应为 0");
        assertEquals("6.2", v.getRaw(), "raw 应为 6.2");
    }

    @Test
    public void testParseWithMetadataSuffix() {
        RedisServerVersion v = RedisServerVersion.parse("7.2.6-rc1");
        log.info("解析结果: major={}, minor={}, patch={}, raw={}", v.getMajor(), v.getMinor(), v.getPatch(), v.getRaw());
        assertEquals(7, v.getMajor(), "major 应为 7");
        assertEquals(2, v.getMinor(), "minor 应为 2");
        assertEquals(6, v.getPatch(), "patch 应为 6");
        assertEquals("7.2.6", v.getRaw(), "raw 应去掉 metadata 后缀");
    }

    @Test
    public void testParseWithPlusSuffix() {
        RedisServerVersion v = RedisServerVersion.parse("6.0.0+build1");
        log.info("解析结果: raw={}", v.getRaw());
        assertEquals(6, v.getMajor(), "major 应为 6");
        assertEquals("6.0.0", v.getRaw(), "raw 应去掉 + 后缀");
    }

    @Test
    public void testParseNullThrows() {
        log.info("测试 null 输入应抛出异常");
        assertThrows(ConfigurationException.class, () -> RedisServerVersion.parse(null), "null 应抛 ConfigurationException");
    }

    @Test
    public void testParseEmptyThrows() {
        log.info("测试空字符串输入应抛出异常");
        assertThrows(ConfigurationException.class, () -> RedisServerVersion.parse("  "), "空字符串应抛 ConfigurationException");
    }

    @Test
    public void testParseInvalidFormatThrows() {
        log.info("测试非法格式应抛出异常");
        assertThrows(ConfigurationException.class, () -> RedisServerVersion.parse("abc.def"), "非数字应抛 ConfigurationException");
        assertThrows(ConfigurationException.class, () -> RedisServerVersion.parse("1"), "单段版本号应抛 ConfigurationException");
        assertThrows(ConfigurationException.class, () -> RedisServerVersion.parse("1.2.3.4"), "四段版本号应抛 ConfigurationException");
    }

    @Test
    public void testIsAtLeastMajorMinor() {
        RedisServerVersion v = RedisServerVersion.parse("6.2.9");
        log.info("版本=[{}]，测试 isAtLeast/isBefore", v.getRaw());
        assertTrue(v.isAtLeast(6, 0), "6.2.9 应 >= 6.0");
        assertTrue(v.isAtLeast(6, 2), "6.2.9 应 >= 6.2");
        assertFalse(v.isAtLeast(7, 0), "6.2.9 不应 >= 7.0");
        assertFalse(v.isAtLeast(6, 3), "6.2.9 不应 >= 6.3");
    }

    @Test
    public void testIsAtLeastWithPatch() {
        RedisServerVersion v = RedisServerVersion.parse("6.2.5");
        log.info("版本=[{}]，测试带 patch 的 isAtLeast", v.getRaw());
        assertTrue(v.isAtLeast(6, 2, 5), "6.2.5 应 >= 6.2.5");
        assertTrue(v.isAtLeast(6, 2, 4), "6.2.5 应 >= 6.2.4");
        assertFalse(v.isAtLeast(6, 2, 6), "6.2.5 不应 >= 6.2.6");
    }

    @Test
    public void testIsBeforeMajorMinor() {
        RedisServerVersion v = RedisServerVersion.parse("5.0.14");
        log.info("版本=[{}]，测试 isBefore", v.getRaw());
        assertTrue(v.isBefore(6, 0), "5.0.14 应 < 6.0");
        assertFalse(v.isBefore(5, 0), "5.0.14 不应 < 5.0");
        assertFalse(v.isBefore(4, 0), "5.0.14 不应 < 4.0");
    }

    @Test
    public void testToString() {
        RedisServerVersion v = RedisServerVersion.parse("3.2.12");
        assertEquals("3.2.12", v.toString(), "toString 应返回 raw");
    }
}

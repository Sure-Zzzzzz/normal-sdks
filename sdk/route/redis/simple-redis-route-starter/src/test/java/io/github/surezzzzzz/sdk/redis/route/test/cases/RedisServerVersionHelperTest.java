package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerVersion;
import io.github.surezzzzzz.sdk.redis.route.support.RedisCommandCapabilityHelper;
import io.github.surezzzzzz.sdk.redis.route.support.RedisServerVersionHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis Server 版本/能力判断 Helper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisServerVersionHelperTest {

    @Test
    public void testIsKnownWithValidInfo() {
        RedisServerInfo info = knownInfo("7.2.6");
        log.info("info.known={}, version={}", info.isKnown(), info.getVersion());
        assertTrue(RedisServerVersionHelper.isKnown(info), "有效 info 应 isKnown=true");
        assertFalse(RedisServerVersionHelper.isUnknown(info), "有效 info 不应 isUnknown");
    }

    @Test
    public void testIsUnknownWithNullInfo() {
        log.info("测试 null info 应 isUnknown=true");
        assertTrue(RedisServerVersionHelper.isUnknown(null), "null 应 isUnknown=true");
        assertFalse(RedisServerVersionHelper.isKnown(null), "null 不应 isKnown");
    }

    @Test
    public void testIsUnknownWhenKnownFalse() {
        RedisServerInfo info = unknownInfo("ds1");
        log.info("info.known={}, errorMessage={}", info.isKnown(), info.getErrorMessage());
        assertTrue(RedisServerVersionHelper.isUnknown(info), "known=false 应 isUnknown=true");
    }

    @Test
    public void testIsAtLeastReturnsFalseWhenUnknown() {
        log.info("unknown info 下 isAtLeast 应保守返回 false");
        assertFalse(RedisServerVersionHelper.isAtLeast(null, 4, 0), "unknown 时 isAtLeast 应返回 false");
        assertFalse(RedisServerVersionHelper.isAtLeast(unknownInfo("ds1"), 4, 0), "unknown 时 isAtLeast 应返回 false");
    }

    @Test
    public void testIsAtLeastWithKnownVersion() {
        RedisServerInfo info = knownInfo("6.2.0");
        log.info("version={}, 测试 isAtLeast", info.getVersion());
        assertTrue(RedisServerVersionHelper.isAtLeast(info, 6, 0), "6.2.0 应 >= 6.0");
        assertTrue(RedisServerVersionHelper.isAtLeast(info, 6, 2), "6.2.0 应 >= 6.2");
        assertFalse(RedisServerVersionHelper.isAtLeast(info, 7, 0), "6.2.0 不应 >= 7.0");
    }

    @Test
    public void testCommandCapabilitiesForRedis7() {
        RedisServerInfo info = knownInfo("7.2.6");
        log.info("version={}, 验证 Redis 7.x 能力", info.getVersion());
        assertTrue(RedisCommandCapabilityHelper.supportsAcl(info), "7.x 应支持 ACL");
        assertTrue(RedisCommandCapabilityHelper.supportsUnlink(info), "7.x 应支持 UNLINK");
        assertTrue(RedisCommandCapabilityHelper.supportsGetEx(info), "7.x 应支持 GETEX");
        assertTrue(RedisCommandCapabilityHelper.supportsSetGet(info), "7.x 应支持 SET GET");
        assertTrue(RedisCommandCapabilityHelper.supportsKeepTtl(info), "7.x 应支持 KEEPTTL");
        assertTrue(RedisCommandCapabilityHelper.supportsZPop(info), "7.x 应支持 ZPOP");
        assertTrue(RedisCommandCapabilityHelper.supportsLMove(info), "7.x 应支持 LMOVE");
    }

    @Test
    public void testCommandCapabilitiesForRedis5() {
        RedisServerInfo info = knownInfo("5.0.14");
        log.info("version={}, 验证 Redis 5.x 能力", info.getVersion());
        assertFalse(RedisCommandCapabilityHelper.supportsAcl(info), "5.x 不应支持 ACL");
        assertTrue(RedisCommandCapabilityHelper.supportsUnlink(info), "5.x 应支持 UNLINK");
        assertFalse(RedisCommandCapabilityHelper.supportsGetEx(info), "5.x 不应支持 GETEX");
        assertFalse(RedisCommandCapabilityHelper.supportsSetGet(info), "5.x 不应支持 SET GET");
        assertFalse(RedisCommandCapabilityHelper.supportsKeepTtl(info), "5.x 不应支持 KEEPTTL");
        assertTrue(RedisCommandCapabilityHelper.supportsZPop(info), "5.x 应支持 ZPOP");
        assertFalse(RedisCommandCapabilityHelper.supportsLMove(info), "5.x 不应支持 LMOVE");
    }

    @Test
    public void testCommandCapabilitiesForRedis3() {
        RedisServerInfo info = knownInfo("3.2.12");
        log.info("version={}, 验证 Redis 3.x 能力", info.getVersion());
        assertFalse(RedisCommandCapabilityHelper.supportsAcl(info), "3.x 不应支持 ACL");
        assertFalse(RedisCommandCapabilityHelper.supportsUnlink(info), "3.x 不应支持 UNLINK");
        assertFalse(RedisCommandCapabilityHelper.supportsGetEx(info), "3.x 不应支持 GETEX");
        assertFalse(RedisCommandCapabilityHelper.supportsZPop(info), "3.x 不应支持 ZPOP");
        assertFalse(RedisCommandCapabilityHelper.supportsLMove(info), "3.x 不应支持 LMOVE");
    }

    @Test
    public void testCommandCapabilitiesReturnFalseWhenUnknown() {
        log.info("unknown info 下所有能力应保守返回 false");
        assertFalse(RedisCommandCapabilityHelper.supportsAcl(null), "null 时 ACL 应返回 false");
        assertFalse(RedisCommandCapabilityHelper.supportsUnlink(unknownInfo("ds1")), "unknown 时 UNLINK 应返回 false");
        assertFalse(RedisCommandCapabilityHelper.supportsGetEx(null), "null 时 GETEX 应返回 false");
    }

    @Test
    public void testSupportsByCapabilityName() {
        RedisServerInfo info = knownInfo("6.2.0");
        log.info("version={}, 测试 supports(capabilityName)", info.getVersion());
        assertTrue(RedisCommandCapabilityHelper.supports(info, RedisCommandCapabilityHelper.CAPABILITY_ACL), "6.2 应支持 ACL");
        assertFalse(RedisCommandCapabilityHelper.supports(info, "UNKNOWN_CAP"), "未知能力名应返回 false");
        assertFalse(RedisCommandCapabilityHelper.supports(info, null), "null 能力名应返回 false");
    }

    private RedisServerInfo knownInfo(String versionStr) {
        return RedisServerInfo.builder()
                .datasourceKey("test-ds")
                .known(true)
                .version(RedisServerVersion.parse(versionStr))
                .redisMode("standalone")
                .build();
    }

    private RedisServerInfo unknownInfo(String datasourceKey) {
        return RedisServerInfo.builder()
                .datasourceKey(datasourceKey)
                .known(false)
                .errorMessage("探测失败")
                .build();
    }
}

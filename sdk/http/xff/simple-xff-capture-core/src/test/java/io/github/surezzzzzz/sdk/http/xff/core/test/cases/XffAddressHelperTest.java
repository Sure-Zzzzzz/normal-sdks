package io.github.surezzzzzz.sdk.http.xff.core.test.cases;

import io.github.surezzzzzz.sdk.http.xff.core.constant.XffIpScope;
import io.github.surezzzzzz.sdk.http.xff.core.constant.XffIpVersion;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffAddressInfo;
import io.github.surezzzzzz.sdk.http.xff.core.support.XffAddressHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XFF 地址判断 Helper 测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class XffAddressHelperTest {

    @Test
    void shouldRecognizeIpLiteralsWithoutAcceptingHostOrPort() {
        log.info("验证 IP 字面量边界");
        assertTrue(XffAddressHelper.isIpLiteral("192.0.2.1"), "IPv4 应被识别");
        assertTrue(XffAddressHelper.isIpLiteral("2001:db8::1"), "IPv6 应被识别");
        assertTrue(XffAddressHelper.isIpLiteral("::ffff:192.168.1.1"), "IPv4-mapped IPv6 应被识别");
        assertFalse(XffAddressHelper.isIpLiteral("example.test"), "域名不应被识别");
        assertFalse(XffAddressHelper.isIpLiteral("192.0.2.1:8080"), "带端口 IPv4 不应被识别");
        assertFalse(XffAddressHelper.isIpLiteral("[2001:db8::1]"), "带方括号 IPv6 不应被识别");
        assertFalse(XffAddressHelper.isIpLiteral("fe80::1%eth0"), "带 zone id IPv6 不应被识别");
        assertFalse(XffAddressHelper.isIpLiteral(" 192.0.2.1 "), "Helper 不应主动 trim 输入");
        assertFalse(XffAddressHelper.isIpLiteral("001.2.3.4"), "IPv4 前导零应被拒绝，避免八进制歧义");
        assertFalse(XffAddressHelper.isIpLiteral("1.2.3.256"), "IPv4 超界段应被拒绝");
        assertFalse(XffAddressHelper.isIpLiteral("1.2.3"), "缺少 IPv4 段应被拒绝");
        assertFalse(XffAddressHelper.isIpLiteral("2001::db8::1"), "多个 IPv6 压缩符应被拒绝");
        assertFalse(XffAddressHelper.isIpLiteral("2001:db8:+1::"), "带正号的 IPv6 分组应被拒绝");
        assertFalse(XffAddressHelper.isIpLiteral("192.0.2.1::"), "嵌入式 IPv4 只能位于 IPv6 末尾");
    }

    @Test
    void shouldAcceptFullIpv6WithEmbeddedIpv4AtFinalSegment() {
        String fullIpv6 = "2001:db8:0:0:0:0:192.0.2.1";
        String fullMappedIpv6 = "0:0:0:0:0:ffff:192.168.1.1";
        XffAddressInfo normalized = XffAddressHelper.classify(fullIpv6);
        XffAddressInfo mapped = XffAddressHelper.classify(fullMappedIpv6);

        log.info("验证完整 IPv6 嵌入 IPv4：normalized={}，mappedScope={}",
                normalized.getNormalizedIp(), mapped.getScope());
        assertTrue(normalized.isIpLiteral(), "完整 IPv6 的末段嵌入 IPv4 应为合法字面量");
        assertEquals("2001:db8::c000:201", normalized.getNormalizedIp(),
                "完整 IPv6 嵌入 IPv4 后应按 IPv6 规范化");
        assertSame(XffIpVersion.IPV6, normalized.getIpVersion(), "完整形式地址应保留 IPv6 版本");
        assertSame(XffIpScope.SPECIAL, normalized.getScope(), "文档 IPv4 嵌入地址应归为特殊用途");
        assertEquals("192.168.1.1", mapped.getNormalizedIp(), "完整 mapped IPv6 应投影为 IPv4");
        assertSame(XffIpVersion.IPV4, mapped.getIpVersion(), "完整 mapped IPv6 应按 IPv4 投影");
        assertSame(XffIpScope.PRIVATE, mapped.getScope(), "完整 mapped IPv6 应按内嵌 IPv4 分类");
        assertFalse(XffAddressHelper.isIpLiteral("2001:db8:192.0.2.1:0:0:0:0:1"),
                "嵌入 IPv4 不在最后段时应被拒绝");
    }

    @Test
    void shouldClassifyPrivateAddresses() {
        log.info("验证 IPv4 与 IPv6 私网地址");
        assertTrue(XffAddressHelper.isPrivateIp("10.0.0.1"), "10/8 应为私网");
        assertTrue(XffAddressHelper.isPrivateIp("172.31.255.255"), "172.16/12 应为私网");
        assertTrue(XffAddressHelper.isPrivateIp("192.168.1.1"), "192.168/16 应为私网");
        assertTrue(XffAddressHelper.isPrivateIp("fc00::1"), "fc00::/7 应为私网");
        assertTrue(XffAddressHelper.isPrivateIp("::ffff:192.168.1.1"), "映射私网 IPv4 应为私网");
        assertFalse(XffAddressHelper.isPrivateIp("127.0.0.1"), "回环地址不属于 private 定义");
    }

    @Test
    void shouldClassifyPublicAndSpecialAddresses() {
        log.info("验证公网与特殊地址分类");
        assertTrue(XffAddressHelper.isPublicIp("8.8.8.8"), "普通公网 IPv4 应为公网");
        assertTrue(XffAddressHelper.isPublicIp("2001:4860:4860::8888"), "普通公网 IPv6 应为公网");
        assertTrue(XffAddressHelper.isPublicIp("192.0.0.9"), "IANA globally reachable 例外应为公网");
        assertFalse(XffAddressHelper.isPublicIp("10.0.0.1"), "私网地址不应为公网");
        assertFalse(XffAddressHelper.isPublicIp("127.0.0.1"), "回环地址不应为公网");
        assertFalse(XffAddressHelper.isPublicIp("100.64.0.1"), "共享地址空间不应为公网");
        assertFalse(XffAddressHelper.isPublicIp("192.0.2.1"), "文档地址不应为公网");
        assertFalse(XffAddressHelper.isPublicIp("224.0.0.1"), "组播地址不应为公网");
        assertFalse(XffAddressHelper.isPublicIp("::1"), "IPv6 回环不应为公网");
        assertFalse(XffAddressHelper.isPublicIp("fe80::1"), "IPv6 链路本地不应为公网");
        assertFalse(XffAddressHelper.isPublicIp("2001:db8::1"), "IPv6 文档地址不应为公网");
        assertFalse(XffAddressHelper.isPublicIp("ff02::1"), "IPv6 组播不应为公网");
        assertFalse(XffAddressHelper.isPublicIp("4000::1"), "当前 Global Unicast 范围外地址不应为公网");
        assertTrue(XffAddressHelper.isPublicIp("64:ff9b::1"), "IANA 明确全局可达的翻译前缀应为公网");
        assertFalse(XffAddressHelper.isPublicIp("64:ff9b:1::1"), "非公网翻译前缀不应为公网");
    }

    @Test
    void shouldAcceptDeterministicGeneratedStandardLiterals() throws Exception {
        Random random = new Random(20260818L);
        int sampleCount = 512;
        for (int index = 0; index < sampleCount; index++) {
            byte[] ipv4 = new byte[4];
            byte[] ipv6 = new byte[16];
            random.nextBytes(ipv4);
            random.nextBytes(ipv6);
            String ipv4Literal = InetAddress.getByAddress(ipv4).getHostAddress();
            String ipv6Literal = InetAddress.getByAddress(ipv6).getHostAddress();
            assertTrue(XffAddressHelper.isIpLiteral(ipv4Literal),
                    "标准生成 IPv4 必须被识别：" + ipv4Literal);
            assertTrue(XffAddressHelper.isIpLiteral(ipv6Literal),
                    "标准生成 IPv6 必须被识别：" + ipv6Literal);
        }

        log.info("已验证固定种子生成的 IPv4/IPv6 样本对数量：{}", sampleCount);
        assertEquals(512, sampleCount, "生成式样本数量必须固定，保证测试可重复");
    }

    @Test
    void shouldReturnNormalizedStructuredClassification() {
        XffAddressInfo ipv4 = XffAddressHelper.classify("8.8.8.8");
        XffAddressInfo ipv6 = XffAddressHelper.classify("2001:0DB8:0:0:0:0:0:1");
        XffAddressInfo mapped = XffAddressHelper.classify("::ffff:192.168.1.1");
        XffAddressInfo invalid = XffAddressHelper.classify("unknown");

        log.info("结构化分类：ipv4Scope={}，ipv6Normalized={}，mappedNormalized={}，invalidScope={}",
                ipv4.getScope(), ipv6.getNormalizedIp(), mapped.getNormalizedIp(), invalid.getScope());
        assertTrue(ipv4.isIpLiteral(), "公网 IPv4 应为合法字面量");
        assertEquals("8.8.8.8", ipv4.getNormalizedIp(), "IPv4 规范化结果应稳定");
        assertEquals(XffIpVersion.IPV4, ipv4.getIpVersion(), "IPv4 版本应准确");
        assertEquals(XffIpScope.PUBLIC, ipv4.getScope(), "IPv4 公网范围应准确");
        assertEquals("2001:db8::1", ipv6.getNormalizedIp(), "IPv6 应按最长零段规范化");
        assertEquals(XffIpVersion.IPV6, ipv6.getIpVersion(), "IPv6 版本应准确");
        assertEquals(XffIpScope.SPECIAL, ipv6.getScope(), "文档 IPv6 应归为特殊用途");
        assertEquals("192.168.1.1", mapped.getNormalizedIp(), "mapped IPv6 应规范化为内嵌 IPv4");
        assertEquals(XffIpVersion.IPV4, mapped.getIpVersion(), "mapped IPv6 按 IPv4 投影");
        assertEquals(XffIpScope.PRIVATE, mapped.getScope(), "mapped 私网 IPv4 范围应准确");
        assertFalse(invalid.isIpLiteral(), "非法值不应标记为 IP");
        assertNull(invalid.getNormalizedIp(), "非法值不能产生规范化 IP");
        assertNull(invalid.getIpVersion(), "非法值不能产生 IP 版本");
        assertEquals(XffIpScope.INVALID, invalid.getScope(), "非法值范围应准确");
        assertFalse(ipv4.toString().contains("8.8.8.8"), "分类结果 toString 不应泄漏 IP");
        assertSame(XffIpScope.PUBLIC, XffIpScope.fromCode("PUBLIC"), "范围枚举应按精确 code 解析");
        assertTrue(XffIpScope.isValid("PRIVATE"), "私网范围 code 应有效");
        assertEquals(4, XffIpScope.getAllCodes().length, "范围枚举 code 数量应稳定");
        assertSame(XffIpVersion.IPV6, XffIpVersion.fromCode("IPV6"), "版本枚举应按精确 code 解析");
        assertTrue(XffIpVersion.isValid("IPV4"), "IPv4 版本 code 应有效");
        assertEquals(2, XffIpVersion.getAllCodes().length, "版本枚举 code 数量应稳定");
    }

    @Test
    void shouldKeepPrivateAndPublicAsIndependentPredicates() {
        String value = "127.0.0.1";
        log.info("特殊地址分类：value={}", value);
        assertFalse(XffAddressHelper.isPrivateIp(value), "回环地址不属于 private 定义");
        assertFalse(XffAddressHelper.isPublicIp(value), "回环地址也不属于公网");
    }
}

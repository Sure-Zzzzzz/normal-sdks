package io.github.surezzzzzz.sdk.http.xff.core.test.cases;

import io.github.surezzzzzz.sdk.http.xff.core.support.XffAddressHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IANA 特殊用途地址注册表快照差分测试。
 *
 * <p>测试直接读取仓库内官方 CSV 快照，对每个 CIDR 的首尾地址验证公网分类。
 * IPv4-mapped IPv6 按内嵌 IPv4 分类，是模块明确声明的唯一差异策略。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class IanaRegistrySnapshotTest {

    private static final String IPV4_FIXTURE = "iana/iana-ipv4-special-registry-1.csv";
    private static final String IPV6_FIXTURE = "iana/iana-ipv6-special-registry-1.csv";
    private static final String IPV4_MAPPED_CIDR = "::ffff:0:0/96";
    private static final int IPV4_SNAPSHOT_CIDR_COUNT = 26;
    private static final int IPV6_SNAPSHOT_CIDR_COUNT = 25;

    @Test
    void shouldMatchIpv4RegistrySnapshot() throws Exception {
        int count = verifyFixture(IPV4_FIXTURE, null);

        log.info("已验证 IANA IPv4 特殊用途 CIDR 数量：{}", count);
        assertEquals(IPV4_SNAPSHOT_CIDR_COUNT, count, "IPv4 官方快照 CIDR 数量必须精确一致");
    }

    @Test
    void shouldMatchIpv6RegistrySnapshot() throws Exception {
        int count = verifyFixture(IPV6_FIXTURE, IPV4_MAPPED_CIDR);

        log.info("已验证 IANA IPv6 特殊用途 CIDR 数量：{}", count);
        assertEquals(IPV6_SNAPSHOT_CIDR_COUNT - 1, count,
                "IPv6 官方快照 CIDR 数量扣除 mapped 差异策略后必须精确一致");
    }

    @Test
    void shouldMatchAdjacentIpv4RegistryBoundaries() throws Exception {
        int count = verifyAdjacentBoundaries(IPV4_FIXTURE, null, true);

        log.info("已验证 IANA IPv4 相邻边界数量：{}", count);
        assertTrue(count >= IPV4_SNAPSHOT_CIDR_COUNT * 2,
                "IPv4 官方 CIDR 应验证首尾及可用的前后相邻地址");
    }

    @Test
    void shouldMatchAdjacentIpv6RegistryBoundaries() throws Exception {
        int count = verifyAdjacentBoundaries(IPV6_FIXTURE, IPV4_MAPPED_CIDR, false);

        log.info("已验证 IANA IPv6 相邻边界数量：{}", count);
        assertTrue(count >= (IPV6_SNAPSHOT_CIDR_COUNT - 1) * 2,
                "IPv6 官方 CIDR 应验证首尾及可用的前后相邻地址");
    }

    @Test
    void shouldApplyDeclaredMappedIpv4Policy() {
        log.info("验证 IPv4-mapped IPv6 按内嵌 IPv4 分类");
        assertTrue(XffAddressHelper.isPublicIp("::ffff:8.8.8.8"), "映射公网 IPv4 应判定为公网");
        assertFalse(XffAddressHelper.isPublicIp("::ffff:10.0.0.1"), "映射私网 IPv4 不应判定为公网");
        assertTrue(XffAddressHelper.isPrivateIp("::ffff:10.0.0.1"), "映射私网 IPv4 应判定为私网");
    }

    @Test
    void shouldExcludeMulticastOutsideSpecialRegistry() {
        log.info("验证基础地址类型中的组播范围");
        assertFalse(XffAddressHelper.isPublicIp("224.0.0.0"), "IPv4 组播首地址不应为公网单播");
        assertFalse(XffAddressHelper.isPublicIp("239.255.255.255"), "IPv4 组播末地址不应为公网单播");
        assertFalse(XffAddressHelper.isPublicIp("ff00::"), "IPv6 组播首地址不应为公网单播");
        assertFalse(XffAddressHelper.isPublicIp("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"),
                "IPv6 组播末地址不应为公网单播");
    }

    private int verifyAdjacentBoundaries(String resource, String excludedCidr, boolean ipv4) throws Exception {
        List<RegistryRule> rules = loadRules(resource, excludedCidr);
        int bitCount = ipv4 ? 32 : 128;
        BigInteger maxAddress = BigInteger.ONE.shiftLeft(bitCount).subtract(BigInteger.ONE);
        int verifiedCount = 0;
        for (RegistryRule rule : rules) {
            BigInteger hostMask = BigInteger.ONE.shiftLeft(bitCount - rule.prefixLength).subtract(BigInteger.ONE);
            BigInteger first = rule.network;
            BigInteger last = rule.network.add(hostMask);
            List<BigInteger> candidates = new ArrayList<>();
            if (first.signum() > 0) {
                candidates.add(first.subtract(BigInteger.ONE));
            }
            candidates.add(first);
            candidates.add(last);
            if (last.compareTo(maxAddress) < 0) {
                candidates.add(last.add(BigInteger.ONE));
            }
            for (BigInteger candidate : candidates) {
                String address = InetAddress.getByAddress(toBytes(candidate, bitCount / Byte.SIZE)).getHostAddress();
                boolean expected = expectedPublic(candidate, rules, ipv4);
                log.info("IANA 相邻边界：cidr={}, address={}, expectedPublic={}",
                        rule.cidr, address, expected);
                assertEquals(expected, XffAddressHelper.isPublicIp(address),
                        "相邻边界公网分类必须符合完整 registry 最长前缀结果：" + rule.cidr);
                verifiedCount++;
            }
        }
        return verifiedCount;
    }

    private boolean expectedPublic(BigInteger address, List<RegistryRule> rules, boolean ipv4) {
        RegistryRule best = null;
        for (RegistryRule rule : rules) {
            if (address.and(rule.mask).equals(rule.network)
                    && (best == null || rule.prefixLength > best.prefixLength)) {
                best = rule;
            }
        }
        if (best != null) {
            return best.publicAddress;
        }
        if (ipv4) {
            int firstOctet = address.shiftRight(24).intValue() & 0xFF;
            return firstOctet < 224;
        }
        BigInteger globalUnicastStart = BigInteger.valueOf(0x2000L).shiftLeft(112);
        BigInteger globalUnicastEnd = BigInteger.valueOf(0x4000L).shiftLeft(112);
        return address.compareTo(globalUnicastStart) >= 0 && address.compareTo(globalUnicastEnd) < 0;
    }

    private List<RegistryRule> loadRules(String resource, String excludedCidr) throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(inputStream, "IANA 官方 CSV 快照必须存在");
        List<RegistryRule> rules = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            List<String> header = parseCsvLine(reader.readLine());
            int addressIndex = header.indexOf("Address Block");
            int globallyReachableIndex = header.indexOf("Globally Reachable");
            String record;
            while ((record = readCsvRecord(reader)) != null) {
                List<String> row = parseCsvLine(record);
                boolean publicAddress = row.get(globallyReachableIndex).trim().startsWith("True");
                for (String cidr : normalizeAddressBlocks(row.get(addressIndex))) {
                    if (!cidr.equals(excludedCidr)) {
                        rules.add(rule(cidr, publicAddress));
                    }
                }
            }
        }
        return rules;
    }

    private RegistryRule rule(String cidr, boolean publicAddress) throws Exception {
        String[] parts = cidr.split("/");
        byte[] bytes = InetAddress.getByName(parts[0]).getAddress();
        int prefixLength = Integer.parseInt(parts[1]);
        int bitCount = bytes.length * Byte.SIZE;
        BigInteger maxAddress = BigInteger.ONE.shiftLeft(bitCount).subtract(BigInteger.ONE);
        BigInteger hostMask = BigInteger.ONE.shiftLeft(bitCount - prefixLength).subtract(BigInteger.ONE);
        BigInteger mask = hostMask.not().and(maxAddress);
        BigInteger network = new BigInteger(1, bytes).and(mask);
        return new RegistryRule(cidr, network, mask, prefixLength, publicAddress);
    }

    private int verifyFixture(String resource, String excludedCidr) throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(inputStream, "IANA 官方 CSV 快照必须存在");
        int verifiedCount = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            List<String> header = parseCsvLine(reader.readLine());
            int addressIndex = header.indexOf("Address Block");
            int globallyReachableIndex = header.indexOf("Globally Reachable");
            assertTrue(addressIndex >= 0, "CSV 必须包含 Address Block 列");
            assertTrue(globallyReachableIndex >= 0, "CSV 必须包含 Globally Reachable 列");

            String record;
            while ((record = readCsvRecord(reader)) != null) {
                List<String> row = parseCsvLine(record);
                assertTrue(row.size() > globallyReachableIndex, "CSV 数据行列数必须完整：" + record);
                String globallyReachable = row.get(globallyReachableIndex).trim();
                boolean expectedPublic = globallyReachable.startsWith("True");
                for (String cidr : normalizeAddressBlocks(row.get(addressIndex))) {
                    if (cidr.equals(excludedCidr)) {
                        continue;
                    }
                    assertCidrEndpoints(cidr, expectedPublic);
                    verifiedCount++;
                }
            }
        }
        return verifiedCount;
    }

    private void assertCidrEndpoints(String cidr, boolean expectedPublic) throws Exception {
        String[] parts = cidr.split("/");
        byte[] source = InetAddress.getByName(parts[0]).getAddress();
        int prefixLength = Integer.parseInt(parts[1]);
        int bitCount = source.length * Byte.SIZE;
        BigInteger sourceValue = new BigInteger(1, source);
        BigInteger hostMask = BigInteger.ONE.shiftLeft(bitCount - prefixLength).subtract(BigInteger.ONE);
        BigInteger network = sourceValue.and(hostMask.not().and(BigInteger.ONE.shiftLeft(bitCount).subtract(BigInteger.ONE)));
        String first = InetAddress.getByAddress(toBytes(network, source.length)).getHostAddress();
        String last = InetAddress.getByAddress(toBytes(network.add(hostMask), source.length)).getHostAddress();

        log.info("IANA CIDR 边界：cidr={}, expectedPublic={}, first={}, last={}",
                cidr, expectedPublic, first, last);
        assertEquals(expectedPublic, XffAddressHelper.isPublicIp(first),
                "CIDR 首地址公网分类必须符合 IANA 快照：" + cidr);
        assertEquals(expectedPublic, XffAddressHelper.isPublicIp(last),
                "CIDR 末地址公网分类必须符合 IANA 快照：" + cidr);
    }

    private byte[] toBytes(BigInteger value, int length) {
        byte[] source = value.toByteArray();
        byte[] result = new byte[length];
        int copyLength = Math.min(source.length, result.length);
        System.arraycopy(source, source.length - copyLength, result, result.length - copyLength, copyLength);
        return result;
    }

    private List<String> normalizeAddressBlocks(String value) {
        String normalized = value.replaceAll("\\s*\\[\\d+\\]", "");
        String[] parts = normalized.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            result.add(part.trim());
        }
        return result;
    }

    private String readCsvRecord(BufferedReader reader) throws Exception {
        String firstLine = reader.readLine();
        if (firstLine == null) {
            return null;
        }
        StringBuilder record = new StringBuilder(firstLine);
        while (hasOpenQuotedField(record)) {
            String continuation = reader.readLine();
            assertNotNull(continuation, "CSV quoted 字段必须正常闭合");
            record.append('\n').append(continuation);
        }
        return record.toString();
    }

    private boolean hasOpenQuotedField(CharSequence value) {
        boolean quoted = false;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '"') {
                continue;
            }
            if (quoted && index + 1 < value.length() && value.charAt(index + 1) == '"') {
                index++;
            } else {
                quoted = !quoted;
            }
        }
        return quoted;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                result.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        result.add(value.toString());
        return result;
    }

    private static final class RegistryRule {
        private final String cidr;
        private final BigInteger network;
        private final BigInteger mask;
        private final int prefixLength;
        private final boolean publicAddress;

        private RegistryRule(String cidr, BigInteger network, BigInteger mask,
                             int prefixLength, boolean publicAddress) {
            this.cidr = cidr;
            this.network = network;
            this.mask = mask;
            this.prefixLength = prefixLength;
            this.publicAddress = publicAddress;
        }
    }
}

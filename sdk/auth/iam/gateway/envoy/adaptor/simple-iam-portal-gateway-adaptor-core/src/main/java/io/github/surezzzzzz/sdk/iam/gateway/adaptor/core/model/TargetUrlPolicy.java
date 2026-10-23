package io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.model;

import io.github.surezzzzzz.sdk.iam.gateway.adaptor.core.constant.SimpleIamPortalGatewayAdaptorConstant;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * 目标地址和公开路由策略。
 *
 * <p>策略不对登记的主机名发起 DNS 或网络访问；{@code DnsResolver} 提供的 IP
 * 字面量仍会通过 {@link InetAddress} 做格式和地址范围校验。</p>
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class TargetUrlPolicy {

    /**
     * 允许的目标端口。
     */
    @Singular
    Set<Integer> allowedPorts;
    /**
     * 平台保留公开前缀。
     */
    @Singular
    Set<String> reservedPrefixes;
    /**
     * 是否允许回环地址。仅本地 runtime 显式开启。
     */
    @Builder.Default
    boolean allowLoopback = false;
    /**
     * 是否允许 RFC1918 私网地址。
     */
    @Builder.Default
    boolean allowPrivateNetwork = false;
    /**
     * 是否允许云元数据地址。
     */
    @Builder.Default
    boolean allowMetadata = false;
    /**
     * 策略版本，进入快照版本摘要。
     */
    @Builder.Default
    String version = SimpleIamPortalGatewayAdaptorConstant.DEFAULT_POLICY_VERSION;

    /**
     * 返回固定 HTTP 上游的默认策略。
     */
    public static TargetUrlPolicy defaults() {
        return TargetUrlPolicy.builder()
                .allowedPort(SimpleIamPortalGatewayAdaptorConstant.PORT_HTTP)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_ROOT)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_IAM)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_IAM_APP)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_APP)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_CLIENT_API)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_PERSONAL_CLIENT_API)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_TOKEN_API)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_AKSK_APPLICATION_AUTHORIZATION_API)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_OAUTH)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_LOGIN)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_HEALTH)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_ACTUATOR)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_XDS)
                .reservedPrefix(SimpleIamPortalGatewayAdaptorConstant.RESERVED_ADMIN)
                .build();
    }

    private static boolean sameOrChild(String path, String prefix) {
        if (SimpleIamPortalGatewayAdaptorConstant.RESERVED_ROOT.equals(prefix)) {
            return SimpleIamPortalGatewayAdaptorConstant.RESERVED_ROOT.equals(path);
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static boolean isIpLiteral(String value) {
        if (value.indexOf('%') >= 0) {
            return false;
        }
        return value.indexOf(':') >= 0
                ? value.matches("[0-9a-f:.]+")
                : value.matches("[0-9.]+");
    }

    private static boolean isMetadata(String ip) {
        return "169.254.169.254".equals(ip) || "fd00:ec2::254".equals(ip);
    }

    private static boolean isPrivate(InetAddress address, byte[] bytes) {
        if (address.isSiteLocalAddress()) {
            return true;
        }
        if (bytes.length == 16 && (bytes[0] & 0xff) >= 0xfc && (bytes[0] & 0xff) <= 0xfd) {
            return true;
        }
        return isMappedIpv4(bytes) && isPrivateIpv4(bytes);
    }

    private static boolean isMappedIpv4(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static boolean isPrivateIpv4(byte[] bytes) {
        int first = bytes[12] & 0xff;
        int second = bytes[13] & 0xff;
        return first == 10 || first == 192 && second == 168
                || first == 172 && second >= 16 && second <= 31;
    }

    private static boolean isMappedUnspecified(byte[] bytes) {
        return isMappedIpv4(bytes) && bytes[12] == 0 && bytes[13] == 0
                && bytes[14] == 0 && bytes[15] == 0;
    }

    private static boolean isMappedLoopback(byte[] bytes) {
        return isMappedIpv4(bytes) && (bytes[12] & 0xff) == 127;
    }

    private static boolean isMappedLinkLocal(byte[] bytes) {
        return isMappedIpv4(bytes) && (bytes[12] & 0xff) == 169
                && (bytes[13] & 0xff) == 254;
    }

    private static boolean isMappedMulticast(byte[] bytes) {
        return isMappedIpv4(bytes) && (bytes[12] & 0xff) >= 224;
    }

    private static boolean isSharedIpv4(byte[] bytes) {
        if (bytes.length != 4 && !isMappedIpv4(bytes)) {
            return false;
        }
        int offset = bytes.length == 4 ? 0 : 12;
        int first = bytes[offset] & 0xff;
        int second = bytes[offset + 1] & 0xff;
        return first == 100 && second >= 64 && second <= 127;
    }

    private static boolean isLimitedBroadcast(byte[] bytes) {
        if (bytes.length != 4 && !isMappedIpv4(bytes)) {
            return false;
        }
        int offset = bytes.length == 4 ? 0 : 12;
        return (bytes[offset] & 0xff) == 255 && (bytes[offset + 1] & 0xff) == 255
                && (bytes[offset + 2] & 0xff) == 255 && (bytes[offset + 3] & 0xff) == 255;
    }

    /**
     * 判断目标是否使用固定的 HTTP 协议。
     */
    public boolean isSchemeAllowed(String scheme) {
        return SimpleIamPortalGatewayAdaptorConstant.SCHEME_HTTP.equalsIgnoreCase(scheme);
    }

    /**
     * 判断端口是否在策略中。
     */
    public boolean isPortAllowed(int port) {
        return allowedPorts != null && allowedPorts.contains(port);
    }

    /**
     * 判断公开前缀是否覆盖平台保留前缀。
     */
    public boolean isReservedPrefix(String routePrefix) {
        if (routePrefix == null || reservedPrefixes == null) {
            return false;
        }
        for (String reservedPrefix : reservedPrefixes) {
            if (sameOrChild(routePrefix, reservedPrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断解析后的 IP 是否允许进入 EDS。
     */
    public boolean isTargetAllowed(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return false;
        }
        String normalized = ip.trim().toLowerCase(Locale.ROOT);
        if (!isIpLiteral(normalized)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(normalized);
            byte[] bytes = address.getAddress();
            if (address.isAnyLocalAddress() || isMappedUnspecified(bytes)) {
                return false;
            }
            if (address.isMulticastAddress() || isMappedMulticast(bytes)
                    || isLimitedBroadcast(bytes) || isSharedIpv4(bytes)) {
                return false;
            }
            if (isMetadata(normalized)) {
                return allowMetadata;
            }
            if ((address.isLoopbackAddress() || isMappedLoopback(bytes)) && !allowLoopback) {
                return false;
            }
            if (address.isLinkLocalAddress() || isMappedLinkLocal(bytes)) {
                return false;
            }
            return !isPrivate(address, bytes) || allowPrivateNetwork;
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}

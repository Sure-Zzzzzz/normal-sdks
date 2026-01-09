package io.github.surezzzzzz.sdk.sensitive.ip.exception;

/**
 * 无效的 CIDR 前缀长度异常
 *
 * @author surezzzzzz
 */
public class InvalidCidrPrefixException extends IpSensitiveException {

    public InvalidCidrPrefixException(int prefixLength) {
        super("Invalid CIDR prefix length: " + prefixLength + " (valid range: 0-32 for IPv4, 0-128 for IPv6)");
    }
}

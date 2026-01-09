package io.github.surezzzzzz.sdk.sensitive.ip.exception;

/**
 * 无效的 CIDR 格式异常
 *
 * @author surezzzzzz
 */
public class InvalidCidrFormatException extends IpSensitiveException {

    public InvalidCidrFormatException(String cidr) {
        super("Invalid CIDR format: " + cidr);
    }
}

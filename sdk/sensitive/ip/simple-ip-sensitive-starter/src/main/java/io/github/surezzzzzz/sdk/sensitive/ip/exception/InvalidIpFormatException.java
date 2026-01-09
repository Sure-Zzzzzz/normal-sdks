package io.github.surezzzzzz.sdk.sensitive.ip.exception;

/**
 * 无效的 IP 格式异常
 *
 * @author surezzzzzz
 */
public class InvalidIpFormatException extends IpSensitiveException {

    public InvalidIpFormatException(String ip) {
        super("Invalid IP format: " + ip);
    }
}

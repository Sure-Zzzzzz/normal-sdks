package io.github.surezzzzzz.sdk.sensitive.ip.exception;

/**
 * 脱敏位置为空异常
 *
 * @author surezzzzzz
 */
public class EmptyMaskPositionsException extends IpSensitiveException {

    public EmptyMaskPositionsException() {
        super("Mask positions cannot be empty");
    }
}

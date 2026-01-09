package io.github.surezzzzzz.sdk.sensitive.ip.exception;

/**
 * 脱敏位置越界异常
 *
 * @author surezzzzzz
 */
public class MaskPositionOutOfBoundsException extends IpSensitiveException {

    public MaskPositionOutOfBoundsException(int position, int maxPosition) {
        super("Mask position " + position + " is out of bounds (valid range: 1-" + maxPosition + ")");
    }
}

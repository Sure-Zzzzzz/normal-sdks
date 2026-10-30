package io.github.surezzzzzz.sdk.license.core.codec;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.util.Base64;

/**
 * 无 padding Base64URL 编解码：不接受 =、+、/。
 *
 * @author surezzzzzz
 */
public final class Base64Url {

    private Base64Url() {
    }

    /** 编码（无 padding）。 */
    public static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 解码；非法字符确定拒绝。 */
    public static byte[] decode(String text) {
        if (text == null || text.isEmpty()) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_ENCODING, ErrorMessage.PROTOCOL_ENCODING_INVALID);
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!ok) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_ENCODING, ErrorMessage.PROTOCOL_ENCODING_INVALID);
            }
        }
        try {
            return Base64.getUrlDecoder().decode(text);
        } catch (IllegalArgumentException failure) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_ENCODING, ErrorMessage.PROTOCOL_ENCODING_INVALID);
        }
    }
}

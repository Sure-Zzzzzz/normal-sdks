package io.github.surezzzzzz.sdk.license.core.codec;

import io.github.surezzzzzz.sdk.license.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import java.nio.charset.StandardCharsets;

/**
 * Compact JWS 严格解析：恰三段、非空、无 padding Base64URL。
 *
 * <p>签名输入为前两段与点号的原始 ASCII 字节；签名段独立返回。
 * 不接受空白、=、+、/。</p>
 *
 * @author surezzzzzz
 */
public final class CompactJws {

    private final String headerSegment;
    private final String payloadSegment;
    private final String signatureSegment;

    private CompactJws(String header, String payload, String signature) {
        this.headerSegment = header;
        this.payloadSegment = payload;
        this.signatureSegment = signature;
    }

    /** 解析 Compact JWS 文本；形态或预算违规确定拒绝。 */
    public static CompactJws parse(String compactJws) {
        if (compactJws == null || compactJws.isEmpty()
                || compactJws.getBytes(StandardCharsets.UTF_8).length > SmartLicenseCoreConstant.JWS_MAX_BYTES) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_ENCODING, ErrorMessage.PROTOCOL_ENCODING_INVALID);
        }
        // split 保留尾随空串：四段（如 h.p.s.）必须按段数拒绝，不能被默认丢弃尾段后误判三段。
        String[] segments = compactJws.split("\\.", -1);
        if (segments.length != SmartLicenseCoreConstant.JWS_SEGMENT_COUNT) {
            throw new LicenseCoreException(ErrorCode.PROTOCOL_ENCODING, ErrorMessage.PROTOCOL_ENCODING_INVALID);
        }
        for (String segment : segments) {
            if (segment.isEmpty() || !isStrictBase64Url(segment)) {
                throw new LicenseCoreException(ErrorCode.PROTOCOL_ENCODING, ErrorMessage.PROTOCOL_ENCODING_INVALID);
            }
        }
        return new CompactJws(segments[0], segments[1], segments[2]);
    }

    /** 拼回 Compact JWS 文本。 */
    public static String join(String headerSegment, String payloadSegment, String signatureSegment) {
        return headerSegment + "." + payloadSegment + "." + signatureSegment;
    }

    public String getHeaderSegment() {
        return headerSegment;
    }

    public String getPayloadSegment() {
        return payloadSegment;
    }

    public String getSignatureSegment() {
        return signatureSegment;
    }

    /** 签名原文（RFC 7515 §5.1：恰前两段与单个分隔点号，无尾点）。 */
    public byte[] signingInput() {
        return (headerSegment + "." + payloadSegment).getBytes(StandardCharsets.US_ASCII);
    }

    /** 签名段解码字节。 */
    public byte[] signatureBytes() {
        return Base64Url.decode(signatureSegment);
    }

    /** Base64URL 编码（无 padding）。 */
    public static String encode(byte[] bytes) {
        return Base64Url.encode(bytes);
    }

    private static boolean isStrictBase64Url(String segment) {
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}

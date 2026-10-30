package io.github.surezzzzzz.sdk.license.core.model;

import java.nio.charset.StandardCharsets;

/**
 * 不可变签名原文：Compact JWS 前两段与点号的原始 ASCII 字节。
 *
 * <p>由三段文本构造或直接持字节；任何一侧不得重新序列化。签名输入原文与展示/领域对象分开，
 * 公开结果不长期携带 raw JWS。</p>
 *
 * @author surezzzzzz
 */
public final class SigningInput {

    private final byte[] bytes;

    private SigningInput(byte[] bytes) {
        this.bytes = bytes;
    }

    /** 从三段 Compact JWS 文本取 RFC 7515 签名原文（恰前两段与单个分隔点号，无尾点）。 */
    public static SigningInput ofCompactJws(String compactJws) {
        if (compactJws == null) {
            throw new IllegalArgumentException("JWS 文本不得为空");
        }
        int firstDot = compactJws.indexOf('.');
        int secondDot = compactJws.indexOf('.', firstDot + 1);
        if (firstDot <= 0 || secondDot <= firstDot + 1 || secondDot == compactJws.length() - 1) {
            throw new IllegalArgumentException("JWS 必须为三段文本");
        }
        byte[] bytes = compactJws.substring(0, secondDot).getBytes(StandardCharsets.US_ASCII);
        return new SigningInput(bytes);
    }

    /** 直接持有原始字节（副本）。 */
    public static SigningInput ofBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("签名原文不得为空");
        }
        return new SigningInput(bytes.clone());
    }

    /** 原始字节副本。 */
    public byte[] getBytes() {
        return bytes.clone();
    }

    /** 字节长度。 */
    public int length() {
        return bytes.length;
    }
}

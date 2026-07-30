package io.github.surezzzzzz.sdk.license.core.test.cases;

import io.github.surezzzzzz.sdk.license.core.constant.SmartLicenseCoreConstant;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseValidationException;
import io.github.surezzzzzz.sdk.license.core.support.LicenseBase64UrlHelper;
import io.github.surezzzzzz.sdk.license.core.support.LicenseJwsHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * License JWS 协议帮助类测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseJwsHelperTest {

    private static final String KID = "lic-example-es256-v1";
    private static final String HEADER_SEGMENT = "eyJhbGciOiJFUzI1NiIsImtpZCI6InRlc3QifQ";
    private static final String PAYLOAD_SEGMENT = "eyJqdGkiOiJ0ZXN0In0";

    @Test
    void shouldConstructFixedHeaderAndRawAsciiSigningInput() {
        String header = LicenseJwsHelper.header(KID);
        byte[] input = LicenseJwsHelper.signingInput(HEADER_SEGMENT, PAYLOAD_SEGMENT);

        log.info("验证固定 protected header 字段和 ASCII 签名输入");
        assertEquals("{\"alg\":\"ES256\",\"kid\":\"" + KID + "\",\"typ\":\"JWT\"}", header,
                "v1 header 必须只按固定字段顺序生成 alg、kid、typ");
        assertArrayEquals((HEADER_SEGMENT + "." + PAYLOAD_SEGMENT).getBytes(StandardCharsets.US_ASCII), input,
                "签名输入必须是 header.payload 的原始 ASCII 字节");
    }

    @Test
    void shouldEncodeWithoutPaddingAndRejectInvalidSegments() {
        String encoded = LicenseBase64UrlHelper.encode(new byte[]{1, 2});
        log.info("验证无 padding Base64URL 和非法段拒绝");
        assertTrue(LicenseBase64UrlHelper.isUnpadded(encoded), "编码结果必须为无 padding Base64URL");
        assertFalse(encoded.contains("="), "编码结果不得包含 padding");
        assertFalse(LicenseBase64UrlHelper.isUnpadded("AA="), "带 padding 的段必须拒绝");
        assertFalse(LicenseBase64UrlHelper.isUnpadded("A"), "无法构成 Base64URL quantum 的段必须拒绝");
        assertFalse(LicenseBase64UrlHelper.isUnpadded("AA*"), "含非法字符的段必须拒绝");
        assertThrows(LicenseValidationException.class, () -> LicenseJwsHelper.signingInput("AA=", PAYLOAD_SEGMENT),
                "非法 header 段不得进入签名输入");
        assertThrows(LicenseValidationException.class,
                () -> LicenseJwsHelper.compact(HEADER_SEGMENT, PAYLOAD_SEGMENT, new byte[1]),
                "非 64 字节 JOSE 签名不得组装 Compact JWS");
    }

    @Test
    void shouldCompactJoseSignatureExactlyOnce() {
        byte[] signature = new byte[SmartLicenseCoreConstant.ES256_JOSE_SIGNATURE_LENGTH];
        String compact = LicenseJwsHelper.compact(HEADER_SEGMENT, PAYLOAD_SEGMENT, signature);
        String[] segments = compact.split("\\.", -1);

        log.info("验证 Compact JWS 组装只产生三段且签名段固定长度");
        assertEquals(SmartLicenseCoreConstant.JWS_SEGMENT_COUNT, segments.length, "Compact JWS 必须只有三段");
        assertEquals(SmartLicenseCoreConstant.ES256_JOSE_SIGNATURE_BASE64URL_LENGTH, segments[2].length(),
                "ES256 JOSE 签名段必须为固定无 padding 长度");
        assertFalse(compact.contains("="), "Compact JWS 不得包含 padding");
    }
}

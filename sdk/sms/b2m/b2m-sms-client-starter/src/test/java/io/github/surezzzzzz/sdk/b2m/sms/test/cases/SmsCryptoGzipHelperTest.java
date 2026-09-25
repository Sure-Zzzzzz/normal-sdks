package io.github.surezzzzzz.sdk.b2m.sms.test.cases;

import io.github.surezzzzzz.sdk.b2m.sms.constant.SmsConstant;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsConfigurationException;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsException;
import io.github.surezzzzzz.sdk.b2m.sms.support.SmsCryptoHelper;
import io.github.surezzzzzz.sdk.b2m.sms.support.SmsGzipHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 加密/压缩 Helper：roundtrip 与失败路径（失败抛异常不返回 null）。
 *
 * @author surezzzzzz
 */
@Slf4j
class SmsCryptoGzipHelperTest {

    private static final byte[] KEY_16 = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTENT = "{\"content\":\"code=834621\",\"mobile\":\"masked\"}"
            .getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("AES 默认算法加解密 roundtrip")
    void shouldRoundTripWithDefaultAlgorithm() {
        byte[] cipher = SmsCryptoHelper.encrypt(CONTENT, KEY_16, SmsConstant.DEFAULT_ALGORITHM);
        byte[] plain = SmsCryptoHelper.decrypt(cipher, KEY_16, SmsConstant.DEFAULT_ALGORITHM);
        log.info("加密后长度={}, 解密还原长度={}", cipher.length, plain.length);
        assertArrayEquals(CONTENT, plain, "解密应还原原文");
    }

    @Test
    @DisplayName("24/32 字节密钥同样 roundtrip（启动校验的合法长度全覆盖）")
    void shouldRoundTripWithLongerKeys() {
        byte[] key24 = "0123456789abcdef01234567".getBytes(StandardCharsets.UTF_8);
        byte[] key32 = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        for (byte[] key : java.util.Arrays.asList(key24, key32)) {
            byte[] cipher = SmsCryptoHelper.encrypt(CONTENT, key, SmsConstant.DEFAULT_ALGORITHM);
            byte[] plain = SmsCryptoHelper.decrypt(cipher, key, SmsConstant.DEFAULT_ALGORITHM);
            log.info("密钥长度={} roundtrip 还原长度={}", key.length, plain.length);
            assertArrayEquals(CONTENT, plain, key.length + " 字节密钥应还原原文");
        }
    }

    @Test
    @DisplayName("密钥长度非法（8 字节）抛配置异常，消息含实际长度")
    void shouldRejectInvalidKeyLength() {
        SmsConfigurationException exception = assertThrows(SmsConfigurationException.class,
                () -> SmsCryptoHelper.requireValidKey("12345678", "UTF-8"));
        log.info("密钥长度异常: errorCode={}, message={}", exception.getErrorCode(), exception.getMessage());
        assertEquals("SMS_CONFIG_002", exception.getErrorCode());
    }

    @Test
    @DisplayName("PKCS7 无 BouncyCastle 时显式抛配置异常（不静默 null）")
    void shouldFailFastWhenPkcs7ProviderMissing() {
        String pkcs7 = "AES/ECB/PKCS7Padding";
        if (java.security.Security.getProvider(SmsConstant.JCE_PROVIDER_BC) != null) {
            log.info("类路径存在 BC，跳过缺失场景");
            return;
        }
        SmsConfigurationException exception = assertThrows(SmsConfigurationException.class,
                () -> SmsCryptoHelper.encrypt(CONTENT, KEY_16, pkcs7));
        log.info("Provider 缺失: errorCode={}", exception.getErrorCode());
        assertEquals("SMS_CRYPTO_003", exception.getErrorCode());
    }

    @Test
    @DisplayName("GZIP 压缩解压 roundtrip")
    void shouldRoundTripGzip() {
        byte[] compressed = SmsGzipHelper.compress(CONTENT);
        byte[] decompressed = SmsGzipHelper.decompress(compressed);
        log.info("原始长度={}, 压缩后长度={}", CONTENT.length, compressed.length);
        assertArrayEquals(CONTENT, decompressed, "解压应还原原文");
    }

    @Test
    @DisplayName("解压非法数据抛 SmsException（不吞异常）")
    void shouldThrowWhenDecompressGarbage() {
        SmsException exception = assertThrows(SmsException.class,
                () -> SmsGzipHelper.decompress("not-gzip".getBytes(StandardCharsets.UTF_8)));
        log.info("解压失败: errorCode={}", exception.getErrorCode());
        assertNotNull(exception.getErrorCode());
    }
}

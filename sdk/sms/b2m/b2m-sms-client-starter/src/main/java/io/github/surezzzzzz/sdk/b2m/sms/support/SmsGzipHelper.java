package io.github.surezzzzzz.sdk.b2m.sms.support;

import io.github.surezzzzzz.sdk.b2m.sms.constant.ErrorCode;
import io.github.surezzzzzz.sdk.b2m.sms.exception.SmsException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * B2M 短信 GZIP 压缩/解压：失败抛 {@code SmsException}。
 *
 * @author surezzzzzz
 */
public final class SmsGzipHelper {

    private SmsGzipHelper() {
        throw new UnsupportedOperationException("帮助类不能实例化");
    }

    /**
     * 压缩。
     *
     * @param bytes 原始字节
     * @return 压缩字节
     */
    public static byte[] compress(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(bytes);
            gzip.finish();
            gzip.flush();
        } catch (IOException exception) {
            throw new SmsException(ErrorCode.CRYPTO_ENCRYPT_FAILED, "B2M短信请求体压缩失败", exception);
        }
        return out.toByteArray();
    }

    /**
     * 解压。
     *
     * @param bytes 压缩字节
     * @return 原始字节
     */
    public static byte[] decompress(byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             GZIPInputStream gzip = new GZIPInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = gzip.read(buffer, 0, buffer.length)) != -1) {
                out.write(buffer, 0, count);
            }
            out.flush();
            return out.toByteArray();
        } catch (IOException exception) {
            throw new SmsException(ErrorCode.CRYPTO_DECRYPT_FAILED, "B2M短信响应体解压失败", exception);
        }
    }
}

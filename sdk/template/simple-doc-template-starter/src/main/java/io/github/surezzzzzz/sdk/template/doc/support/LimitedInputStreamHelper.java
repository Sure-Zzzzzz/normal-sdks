package io.github.surezzzzzz.sdk.template.doc.support;

import io.github.surezzzzzz.sdk.template.doc.annotation.SimpleDocTemplateComponent;
import io.github.surezzzzzz.sdk.template.doc.configuration.SimpleDocTemplateProperties;
import io.github.surezzzzzz.sdk.template.doc.constant.SimpleDocTemplateConstant;
import io.github.surezzzzzz.sdk.template.doc.exception.TemplateRenderException;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Limited InputStream Helper
 *
 * <p>提供受大小限制的 InputStream 读取能力，避免无上限读取。
 *
 * @author surezzzzzz
 */
@SimpleDocTemplateComponent
@RequiredArgsConstructor
public class LimitedInputStreamHelper {

    private final SimpleDocTemplateProperties properties;

    /**
     * 读取模板字节。
     *
     * @param inputStream 输入流
     * @return 字节数组
     */
    public byte[] readTemplateBytes(InputStream inputStream) {
        return readLimited(inputStream, properties.getMaxTemplateBytes(), "模板超过最大大小限制");
    }

    /**
     * 读取图片字节。
     *
     * @param inputStream 输入流
     * @return 字节数组
     */
    public byte[] readImageBytes(InputStream inputStream) {
        return readLimited(inputStream, properties.getMaxImageBytes(), "图片超过最大大小限制");
    }

    /**
     * 受限读取输入流。
     *
     * @param inputStream 输入流
     * @param maxBytes 最大字节数
     * @param message 错误消息
     * @return 字节数组
     */
    public byte[] readLimited(InputStream inputStream, long maxBytes, String message) {
        if (inputStream == null) {
            return new byte[0];
        }
        long limit = maxBytes > 0 ? maxBytes : Long.MAX_VALUE;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[SimpleDocTemplateConstant.IO_BUFFER_SIZE];
        long total = 0;
        int len;
        try {
            while ((len = inputStream.read(buffer)) != -1) {
                total += len;
                if (total > limit) {
                    throw TemplateRenderException.markdownSecurityRejected(message + ": " + maxBytes);
                }
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw TemplateRenderException.markdownRenderFailed(e.getMessage(), e);
        }
    }
}

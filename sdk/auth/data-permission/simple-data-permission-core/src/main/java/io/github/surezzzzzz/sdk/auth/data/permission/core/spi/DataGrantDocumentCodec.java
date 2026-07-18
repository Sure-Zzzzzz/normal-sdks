package io.github.surezzzzzz.sdk.auth.data.permission.core.spi;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;

/**
 * 授权文档编解码 SPI。
 *
 * @author surezzzzzz
 */
public interface DataGrantDocumentCodec {

    /**
     * 解码授权文档文本。
     *
     * @param payload 授权文档文本
     * @return 已校验的授权文档
     */
    DataGrantDocument decode(String payload);

    /**
     * 编码授权文档。
     *
     * @param document 已校验的授权文档
     * @return 规范化授权文档文本
     */
    String encode(DataGrantDocument document);
}

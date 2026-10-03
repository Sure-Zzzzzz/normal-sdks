package io.github.surezzzzzz.sdk.kms.client.auth;

import java.util.Map;

/**
 * 传输中立的调用身份 SPI：向出站请求注入身份头（如 Authorization）。
 *
 * <p>headers 为请求头可写视图；键按 HTTP 语义大小写不敏感，由各传输适配层负责归一。
 * 实现不得读取或修改请求体，不得在网络失败时重试取凭据。每传输装配至多一个
 * applier Bean；未提供时不补造身份头，由 Server 返回 {@code 401}。</p>
 *
 * @author surezzzzzz
 */
public interface KmsCredentialApplier {

    /**
     * 向出站请求头注入调用身份。
     *
     * @param headers 请求头可写视图（大小写不敏感，由传输层归一）
     */
    void apply(Map<String, String> headers);
}

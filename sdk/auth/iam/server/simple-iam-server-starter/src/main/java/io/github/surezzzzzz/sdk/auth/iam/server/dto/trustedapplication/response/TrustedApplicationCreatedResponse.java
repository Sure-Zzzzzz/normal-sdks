package io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 可信应用创建响应（摘要 + 初始客户端一次性密钥）
 *
 * <p>仅在创建端点返回；CONFIDENTIAL 初始客户端携带 {@code initialClientSecret}
 * 明文（自传原样回显、留空服务端生成，均一次性），PUBLIC 客户端为 null。
 * 列表 / 详情契约仍用 {@link TrustedApplicationResponse} / {@link TrustedApplicationDetailResponse}，不含密钥。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class TrustedApplicationCreatedResponse {

    /**
     * 应用摘要
     */
    private TrustedApplicationResponse application;

    /**
     * 初始客户端明文密钥（自传原样回显、留空服务端生成，仅创建响应返回一次；PUBLIC 初始客户端为 null）
     */
    private String initialClientSecret;
}

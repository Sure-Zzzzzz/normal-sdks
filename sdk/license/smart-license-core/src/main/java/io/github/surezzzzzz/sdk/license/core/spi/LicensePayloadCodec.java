package io.github.surezzzzzz.sdk.license.core.spi;

import io.github.surezzzzzz.sdk.license.core.model.LicenseClaims;

/**
 * License v1 payload 确定性编码端口。
 *
 * @author surezzzzzz
 */
public interface LicensePayloadCodec {

    /**
     * 将已验证 Claims 编码为确定性的紧凑 JSON 文本。
     *
     * <p>对自定义条款，Server 仅可在目标校验端已具备同类型执行器时编码签发；
     * Core 不解释条款语义，校验端遇到未实现类型必须拒绝。</p>
     *
     * @param claims 已验证的 v1 声明
     * @return 用于原样签名的 payload 文本
     */
    String encodeV1(LicenseClaims claims);
}

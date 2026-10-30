package io.github.surezzzzzz.sdk.license.core.spi;

import io.github.surezzzzzz.sdk.license.core.model.SigningInput;

/**
 * License 自有签名接口：消费精确业务签名档案引用与不可变签名原文，返回签名结果。
 *
 * <p>不暴露 KMS DTO、钥实体或任何签名服务内部标识；生产实现由宿主适配官方 KMS 客户端，
 * Core 不提供自动本地私钥兜底。kid/钥版本来自档案，调用方不能任意指定。</p>
 *
 * <p>实现契约：必须以档案登记的精确 keyRef+keyVersion 调签名服务（不得缺省版本由服务端选），
 * 并核对服务返回的 keyRef 与版本与档案一致，不一致按失败处理；本地验签一律使用档案
 * 持久化的公钥快照，实现不得在签名路径现场读取公钥。</p>
 *
 * @author surezzzzzz
 */
public interface LicenseSigner {

    /**
     * 对原文执行 ES256 签名。
     *
     * @param signingProfileId 业务签名档案编号（精确用途/产品/kid/钥版本的档案记录）
     * @param input 不可变签名原文
     * @return 64 字节 JOSE 签名段
     */
    byte[] sign(String signingProfileId, SigningInput input);

    /**
     * 带签发任务编号的签名：operationId 进入实现的调用追踪日志（与签名服务审计对账的安全关联锚），
     * 缺省委托两参形态。
     *
     * @param signingProfileId 业务签名档案编号
     * @param operationId 签发任务编号（审计关联，可为 null）
     * @param input 不可变签名原文
     * @return 64 字节 JOSE 签名段
     */
    default byte[] sign(String signingProfileId, String operationId, SigningInput input) {
        return sign(signingProfileId, input);
    }
}

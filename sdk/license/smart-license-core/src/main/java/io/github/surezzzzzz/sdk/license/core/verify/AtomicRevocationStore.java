package io.github.surezzzzzz.sdk.license.core.verify;

import io.github.surezzzzzz.sdk.license.core.codec.RevocationPackageCodec;

/**
 * 支持完整吊销包原子安装的 Verifier 存储端口。
 *
 * <p>调用方必须先完成签名、摘要、连续分片和累计条目验证，再把句柄交给此端口。
 * 安装失败不得清空或替换最近一次有效快照；实现应持久化序号、时间和条目水位。</p>
 *
 * @author surezzzzzz
 */
public interface AtomicRevocationStore extends RevocationStore {

    /** 安装已完整验证的吊销包；旧序号、同序号异快照和条目回退均拒绝。 */
    void install(RevocationPackageCodec.VerifiedPackage verifiedPackage);
}

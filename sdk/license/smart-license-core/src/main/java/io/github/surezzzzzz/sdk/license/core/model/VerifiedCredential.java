package io.github.surezzzzzz.sdk.license.core.model;

import io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload;
import io.github.surezzzzzz.sdk.license.core.verify.VerificationContext;

/**
 * 已验证凭据句柄：仅证明内容曾经通过验签与结构校验，不是长期许可。
 *
 * <p>调用方不能自行构造；文件 authorize 每次重查当前信任/吊销/时间/真实对象/设备/阶段，
 * 不复用导入时结论。</p>
 *
 * @author surezzzzzz
 */
public final class VerifiedCredential {

    private final Protocol2Payload payload;
    private final byte[] rawCompactJws;
    private final long installedTrustVersionFloor;
    /** 仅由验证器保存的受控安装上下文；不能从 payload 反推。 */
    private final VerificationContext verificationContext;

    private VerifiedCredential(Protocol2Payload payload, byte[] rawCompactJws, long installedTrustVersionFloor,
                               VerificationContext verificationContext) {
        this.payload = payload;
        this.rawCompactJws = rawCompactJws.clone();
        this.installedTrustVersionFloor = installedTrustVersionFloor;
        this.verificationContext = verificationContext;
    }

    /** 受控工厂：仅供 Verifier 核心流程创建，调用方不能自行构造已验证句柄。 */
    public static VerifiedCredential create(Protocol2Payload payload, byte[] rawCompactJws) {
        return new VerifiedCredential(payload, rawCompactJws, 0L, null);
    }

    /** 由验证器保存本次安装上下文的信任版本下限，供后续 authorize 重验沿用。 */
    public static VerifiedCredential create(Protocol2Payload payload, byte[] rawCompactJws,
                                            long installedTrustVersionFloor) {
        if (installedTrustVersionFloor < 0L) {
            throw new IllegalArgumentException("信任版本下限不能为负数");
        }
        return new VerifiedCredential(payload, rawCompactJws, installedTrustVersionFloor, null);
    }

    /** 验证器交付句柄的内部工厂；句柄仍须由验证器登记后才可使用。 */
    public static VerifiedCredential issued(Protocol2Payload payload, byte[] rawCompactJws,
                                            VerificationContext verificationContext) {
        if (verificationContext == null) {
            throw new IllegalArgumentException("受控安装上下文不能为空");
        }
        return new VerifiedCredential(payload, rawCompactJws,
                verificationContext.getInstalledTrustVersionFloor(), verificationContext);
    }

    /**
     * 按当前信任、吊销与时间重新核验原始文件，不公开文件字节或复用旧许可。
     * 工厂生成的句柄也必须经过此链，不能以自行构造的 payload 绕过验签。
     */
    public VerifiedCredential revalidate(
            io.github.surezzzzzz.sdk.license.core.verify.DefaultLicenseVerifier verifier) {
        if (verifier == null) {
            throw new IllegalArgumentException("验证器不能为空");
        }
        if (verificationContext == null || !verifier.isIssued(this)) {
            throw new io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException(
                    io.github.surezzzzzz.sdk.license.core.constant.ErrorCode.PROTOCOL_FIELD,
                    io.github.surezzzzzz.sdk.license.core.constant.ErrorMessage.PROTOCOL_FIELD_INVALID);
        }
        return verifier.verify(rawCompactJws, verificationContext);
    }

    public Protocol2Payload getPayload() {
        return payload;
    }

    public String getJti() {
        return payload.getJti();
    }

    public String getGrantId() {
        return payload.getGrantId();
    }

    public SubjectBinding getSubjectBinding() {
        return payload.getSubjectBinding();
    }
}

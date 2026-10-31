package io.github.surezzzzzz.sdk.license.server.constant;

/**
 * 吊销查询、目标校验和执行围栏的常量与安全消息。
 *
 * @author surezzzzzz
 */
public final class RevocationConstant {

    public static final String TARGET_KIND = "targetKind";
    public static final String TARGET_ID = "targetId";
    public static final String GRANT_ID = "grantId";
    public static final String CREDENTIAL_ID = "credentialId";
    public static final String ACTIVATION_ID = "activationId";
    public static final String ASSIGNMENT_ID = "assignmentId";
    public static final String REVOCATION_ID = "revocationId";
    public static final String ACTIVE = "ACTIVE";
    public static final String IAM_SOURCE = "iam";
    public static final String INVALID_KIND = "目标类型仅支持 CREDENTIAL/GRANT/ACTIVATION/ASSIGNMENT/SIGNING_KEY";
    public static final String ALREADY_REVOKED = "目标已吊销";
    public static final String UNUSABLE = "授权、对象或签名档案已失效或吊销，禁止继续签发和交付";
    public static final String INVALID_SNAPSHOT = "冻结凭据与当前授权对象不一致，禁止继续签发和交付";
    public static final String INVALID_PAGE = "page 必须大于等于 1，size 必须在 1 到 100 之间";
    public static final String INVALID_FACTS = "facts 必须为对象";
    public static final String INVALID_SUBJECT = "目标主体缺失或与已认证人员不一致";
    public static final String INVALID_SUBJECT_KIND = "subjectKind 仅支持 DEPLOYMENT/PERSON/SERVICE";
    public static final String OBJECT_UNUSABLE_REASON = "OBJECT_UNUSABLE";
    public static final String GUARD_UNAVAILABLE_REASON = "GUARD_UNAVAILABLE";
    public static final String GUARD_UNAVAILABLE = "签名结果复核暂不可用，任务已标记 UNCERTAIN";
    public static final String DIRECTORY_UNAVAILABLE = "成员资格目录暂不可用，禁止继续签发和交付";
    /** 吊销包签名依赖的固定安全消息，不透传上游签名或密钥资料。 */
    public static final String PACKAGE_SIGNATURE_SHAPE_INVALID = "签名服务返回的签名形态不符";
    public static final String PACKAGE_LOCAL_VERIFY_FAILED = "吊销包本地验签失败";
    public static final String PACKAGE_SIGNATURE_MISMATCH = "吊销包签名与登记公钥不符";
    public static final String PACKAGE_ENCODING_FAILED = "吊销分片内部编码失败";
    public static final String PACKAGE_ENTRY_TOO_LARGE = "单条吊销记录超协议载荷预算";
    public static final int MAX_PAGE_SIZE = 100;

    private RevocationConstant() {
    }
}

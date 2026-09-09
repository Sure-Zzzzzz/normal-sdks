package io.github.surezzzzzz.sdk.auth.iam.resource.core.constant;

/**
 * IAM资源核心常量。
 *
 * @author surezzzzzz
 */
public final class SimpleIamResourceConstant {

    /**
     * Claim集合字段。
     */
    public static final String FIELD_CLAIMS = "claims";
    /**
     * IAM认证信息字段。
     */
    public static final String FIELD_AUTHENTICATION = "authentication";
    /**
     * IAM主体标识字段。
     */
    public static final String FIELD_SUBJECT_ID = "subjectId";
    /**
     * 应用授权快照字段。
     */
    public static final String FIELD_APPLICATION_AUTHORIZATION = "applicationAuthorization";
    /**
     * 字段不能为空详情模板。
     */
    public static final String DETAIL_CANNOT_BE_NULL = "%s不能为null";
    /**
     * IAM主体Claim必须是文本详情。
     */
    public static final String DETAIL_SUBJECT_CLAIM_MUST_BE_TEXT = "IAM主体Claim必须是文本";
    /**
     * IAM授权主体类型无效详情。
     */
    public static final String DETAIL_AUTHORIZATION_SUBJECT_TYPE_INVALID = "IAM授权快照主体类型必须为HUMAN";
    /**
     * IAM主体与授权快照不一致详情。
     */
    public static final String DETAIL_SUBJECT_MISMATCH = "IAM主体与应用授权快照主体不一致";
    /**
     * IAM授权快照Claim无效详情。
     */
    public static final String DETAIL_APPLICATION_AUTHORIZATION_CLAIM_INVALID = "IAM应用授权快照Claim无效";
    /**
     * 常量类实例化提示。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";
    /**
     * 帮助类实例化提示。
     */
    public static final String MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE = "帮助类不能实例化";

    private SimpleIamResourceConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}

package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.constant;

/**
 * 所属人授权协作常量。
 *
 * @author surezzzzzz
 */
public final class SimpleOwnerAuthorizationCollaborationConstant {

    /**
     * 常量类不可实例化提示。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "所属人授权协作常量类不能实例化";
    /**
     * 帮助类不可实例化提示。
     */
    public static final String MESSAGE_HELPER_CLASS_CANNOT_INSTANTIATE = "所属人授权协作帮助类不能实例化";
    /**
     * 长整型零值。
     */
    public static final long LONG_ZERO = 0L;
    /**
     * 目标应用标识字段。
     */
    public static final String FIELD_APPLICATION_ID = "applicationId";
    /**
     * 目标应用展示名字段。
     */
    public static final String FIELD_APPLICATION_NAME = "applicationName";
    /**
     * 目标应用标识快照字段。
     */
    public static final String FIELD_APPLICATION_CODE_SNAPSHOT = "applicationCodeSnapshot";
    /**
     * 变更源顺序位点字段。
     */
    public static final String FIELD_SOURCE_SEQUENCE = "sourceSequence";
    /**
     * 事件标识字段。
     */
    public static final String FIELD_EVENT_ID = "eventId";
    /**
     * 变更类型字段。
     */
    public static final String FIELD_CHANGE_TYPE = "changeType";
    /**
     * 载荷字段。
     */
    public static final String FIELD_PAYLOAD = "payload";
    /**
     * 最低水位字段。
     */
    public static final String FIELD_LOW_WATERMARK = "lowWatermark";
    /**
     * 最高水位字段。
     */
    public static final String FIELD_HIGH_WATERMARK = "highWatermark";
    /**
     * 服务端时间字段。
     */
    public static final String FIELD_SERVER_TIME = "serverTime";
    /**
     * 所属人身份源字段。
     */
    public static final String FIELD_OWNER_SOURCE_ID = "ownerSourceId";
    /**
     * 所属人稳定主体字段。
     */
    public static final String FIELD_OWNER_SUBJECT_ID = "ownerSubjectId";
    /**
     * 恢复位点字段。
     */
    public static final String FIELD_RESUME_AFTER_SEQUENCE = "resumeAfterSequence";
    /**
     * 所属人安全纪元字段。
     */
    public static final String FIELD_OWNER_SECURITY_EPOCH = "ownerSecurityEpoch";
    /**
     * 应用授权纪元字段。
     */
    public static final String FIELD_APPLICATION_AUTHORIZATION_EPOCH = "applicationAuthorizationEpoch";
    /**
     * 所属人继承访问纪元字段。
     */
    public static final String FIELD_OWNER_INHERITED_ACCESS_EPOCH = "ownerInheritedAccessEpoch";
    /**
     * 投影访问纪元字段。
     */
    public static final String FIELD_PROJECTION_ACCESS_EPOCH = "projectionAccessEpoch";
    /**
     * 所属人登录名字段。
     */
    public static final String FIELD_OWNER_USERNAME = "ownerUsername";
    /**
     * 授权 claim 字段。
     */
    public static final String FIELD_AUTHORIZATION = "authorization";
    /**
     * 字段不能为空。
     */
    public static final String DETAIL_FIELD_CANNOT_BE_NULL = "%s不能为空";
    /**
     * 字段不能为空字符串。
     */
    public static final String DETAIL_FIELD_CANNOT_BE_EMPTY = "%s不能为空字符串";
    /**
     * 字段必须为正数。
     */
    public static final String DETAIL_FIELD_MUST_BE_POSITIVE = "%s必须为正数";
    /**
     * 字段不能为负数。
     */
    public static final String DETAIL_FIELD_CANNOT_BE_NEGATIVE = "%s不能为负数";
    /**
     * 变更列表不能包含空值。
     */
    public static final String DETAIL_CHANGES_CANNOT_CONTAIN_NULL = "changes不能包含空值";
    /**
     * 载荷键必须是字符串。
     */
    public static final String DETAIL_PAYLOAD_KEY_MUST_BE_STRING = "payload键必须为字符串";
    /**
     * 载荷不能包含循环容器。
     */
    public static final String DETAIL_PAYLOAD_CANNOT_CONTAIN_CYCLIC_CONTAINERS = "payload不能包含循环容器";
    /**
     * 载荷值必须是 JSON 形态。
     */
    public static final String DETAIL_PAYLOAD_VALUE_MUST_BE_JSON = "payload值必须是JSON标量、Map或List";
    /**
     * 授权 claim 不满足共享契约。
     */
    public static final String DETAIL_AUTHORIZATION_CLAIM_INVALID = "authorization不满足应用授权claim契约";
    /**
     * 水位范围无效。
     */
    public static final String DETAIL_LOW_WATERMARK_MUST_NOT_EXCEED_HIGH_WATERMARK =
            "lowWatermark不能大于highWatermark";
    /**
     * 重同步页不能携带增量变更。
     */
    public static final String DETAIL_RESYNC_PAGE_MUST_NOT_CONTAIN_CHANGES =
            "resyncRequired页面不能携带changes";

    private SimpleOwnerAuthorizationCollaborationConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}

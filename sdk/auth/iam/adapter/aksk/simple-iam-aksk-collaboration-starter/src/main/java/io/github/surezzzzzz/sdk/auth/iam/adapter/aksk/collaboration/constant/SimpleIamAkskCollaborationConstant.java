package io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.constant;

/**
 * Simple IAM AKSK Collaboration 常量。
 *
 * <p>协作路径、scope 与字段键属契约本身（约定即常量），集中在本类收口，禁止以字面量散落。</p>
 *
 * @author surezzzzzz
 */
public final class SimpleIamAkskCollaborationConstant {

    // ==================== 配置相关常量 ====================

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration";

    /**
     * 默认连接超时（毫秒）。
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 1000;

    /**
     * 默认读取超时（毫秒）。
     */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 2000;

    /**
     * 默认单次调用最大尝试次数。
     */
    public static final int DEFAULT_MAX_ATTEMPTS = 2;

    // ==================== 协作契约路径 ====================

    /**
     * 所属人授权投影读取端点。
     */
    public static final String RESOLVE_PATH = "/iam/internal/owner-authorization/resolve";

    /**
     * 候选目标应用目录端点。
     */
    public static final String CANDIDATE_PATH = "/iam/internal/owner-authorization/candidate-applications";

    /**
     * 授权最终态日志拉取端点。
     */
    public static final String CHANGE_PULL_PATH = "/iam/internal/owner-authorization/changes/pull";

    // ==================== 协作契约 scope ====================

    /**
     * 读取 scope（resolve 与候选目录共用）。
     */
    public static final String SCOPE_READ = "iam:internal:owner-authorization:read";

    /**
     * 日志拉取 scope。
     */
    public static final String SCOPE_STREAM = "iam:internal:owner-authorization:stream";

    // ==================== 协作契约字段键 ====================

    /**
     * 投影有效标记。
     */
    public static final String FIELD_ACTIVE = "active";

    /**
     * 身份源标识（resolve/candidate 请求体键）。
     */
    public static final String FIELD_OWNER_SOURCE_ID = "ownerSourceId";

    /**
     * 身份源内稳定主体标识（resolve/candidate 请求体键）。
     */
    public static final String FIELD_OWNER_SUBJECT_ID = "ownerSubjectId";

    /**
     * 目标应用标识（resolve 请求体键）。
     */
    public static final String FIELD_TARGET_APPLICATION_ID = "targetApplicationId";

    /**
     * 所属人安全纪元（resolve 响应键）。
     */
    public static final String FIELD_OWNER_SECURITY_EPOCH = "ownerSecurityEpoch";

    /**
     * 应用授权纪元（resolve 响应键）。
     */
    public static final String FIELD_APPLICATION_AUTHORIZATION_EPOCH = "applicationAuthorizationEpoch";

    /**
     * 所属人继承访问纪元（resolve 响应键）。
     */
    public static final String FIELD_OWNER_INHERITED_ACCESS_EPOCH = "ownerInheritedAccessEpoch";

    /**
     * 投影访问纪元（resolve 响应键）。
     */
    public static final String FIELD_PROJECTION_ACCESS_EPOCH = "projectionAccessEpoch";

    /**
     * 变更消费恢复位点（resolve 响应键）。
     */
    public static final String FIELD_RESUME_AFTER_SEQUENCE = "resumeAfterSequence";

    /**
     * 所属人登录名快照（resolve 响应键）。
     */
    public static final String FIELD_OWNER_USERNAME = "ownerUsername";

    /**
     * 授权快照键（resolve 响应键与变更载荷中性键同名同值）。
     */
    public static final String FIELD_AUTHORIZATION = "authorization";

    /**
     * 候选应用标识（candidate 响应项键）。
     */
    public static final String FIELD_APPLICATION_ID = "applicationId";

    /**
     * 候选应用展示名（candidate 响应项键）。
     */
    public static final String FIELD_APPLICATION_NAME = "applicationName";

    /**
     * 候选应用标识快照（candidate 响应项键）。
     */
    public static final String FIELD_APPLICATION_CODE_SNAPSHOT = "applicationCodeSnapshot";

    /**
     * OAuth2 授权类型表单键。
     */
    public static final String FIELD_GRANT_TYPE = "grant_type";

    /**
     * client_credentials 授权类型值。
     */
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";

    /**
     * OAuth2 scope 表单键。
     */
    public static final String FIELD_SCOPE = "scope";

    /**
     * 访问令牌响应键。
     */
    public static final String FIELD_ACCESS_TOKEN = "access_token";

    /**
     * 有效期响应键。
     */
    public static final String FIELD_EXPIRES_IN = "expires_in";

    /**
     * 拉取位点（changes/pull 请求体键）。
     */
    public static final String FIELD_AFTER_SEQUENCE = "afterSequence";

    /**
     * 页大小（changes/pull 请求体键）。
     */
    public static final String FIELD_PAGE_SIZE = "pageSize";

    /**
     * 是否必须重同步（changes/pull 响应键）。
     */
    public static final String FIELD_RESYNC_REQUIRED = "resyncRequired";

    /**
     * 最低可用水位（changes/pull 响应键）。
     */
    public static final String FIELD_LOW_WATERMARK = "lowWatermark";

    /**
     * 最高水位（changes/pull 响应键）。
     */
    public static final String FIELD_HIGH_WATERMARK = "highWatermark";

    /**
     * 变更列表（changes/pull 响应键）。
     */
    public static final String FIELD_CHANGES = "changes";

    /**
     * 变更源顺序位点（变更项键）。
     */
    public static final String FIELD_SOURCE_SEQUENCE = "sourceSequence";

    /**
     * 事件标识（变更项键）。
     */
    public static final String FIELD_EVENT_ID = "eventId";

    /**
     * 变更类型（变更项键）。
     */
    public static final String FIELD_CHANGE_TYPE = "changeType";

    /**
     * 最终态载荷（变更项键）。
     */
    public static final String FIELD_PAYLOAD = "payload";

    // ==================== 契约边界 ====================

    /**
     * 变更拉取页大小下限。
     */
    public static final int MIN_PULL_PAGE_SIZE = 1;

    /**
     * 变更拉取页大小上限。
     */
    public static final int MAX_PULL_PAGE_SIZE = 200;

    /**
     * 端点协议白名单（http/https 均受支持，由部署方按网络形态选择，其余 scheme 失败关闭）。
     */
    public static final String HTTP_SCHEME = "http";

    /**
     * 端点协议白名单（http/https 均受支持，由部署方按网络形态选择，其余 scheme 失败关闭）。
     */
    public static final String HTTPS_SCHEME = "https";

    /**
     * URL 路径分隔符（baseUri 尾部归一用）。
     */
    public static final String URI_PATH_SEPARATOR = "/";

    private SimpleIamAkskCollaborationConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}

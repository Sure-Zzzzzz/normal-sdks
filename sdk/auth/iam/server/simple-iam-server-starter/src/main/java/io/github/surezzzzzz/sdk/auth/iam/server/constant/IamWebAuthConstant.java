package io.github.surezzzzzz.sdk.auth.iam.server.constant;

/**
 * IAM Web 登录流程的 Starter 内部常量。
 *
 * <p>这些值只服务于 Server Starter 的 HTTP 会话和匿名入口，不属于
 * simple-iam-server-core 的跨模块领域契约。</p>
 *
 * @author surezzzzzz
 */
public final class IamWebAuthConstant {

    /**
     * Web 登录要求公开查询接口。
     */
    public static final String PATH_REQUIREMENTS = "/iam/web/auth/requirements";

    /**
     * 外部登录 state 对应的登录要求版本前缀。
     */
    public static final String SESSION_REQUIREMENTS_REVISION_PREFIX =
            "IAM_EXTERNAL_LOGIN_REQUIREMENTS_REVISION_";

    /**
     * 外部登录 state 对应的已同意文档版本列表前缀。
     */
    public static final String SESSION_REQUIREMENTS_DOCUMENTS_PREFIX =
            "IAM_EXTERNAL_LOGIN_REQUIREMENTS_DOCUMENTS_";

    private IamWebAuthConstant() {
    }
}

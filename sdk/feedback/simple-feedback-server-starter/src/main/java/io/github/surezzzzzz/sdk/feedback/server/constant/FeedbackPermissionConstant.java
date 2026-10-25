package io.github.surezzzzzz.sdk.feedback.server.constant;

/**
 * 三权码常量（PAGE 4 / API 8 / DATA 资源 2，与 web 仓 manifest 逐字一致）
 *
 * @author surezzzzzz
 */
public final class FeedbackPermissionConstant {

    /**
     * 提交反馈
     */
    public static final String API_SUBMISSION_CREATE = "feedback.submission.create";

    // ==================== API 权限码 ====================
    /**
     * 管理侧读取反馈
     */
    public static final String API_SUBMISSION_READ = "feedback.submission.read";
    /**
     * 管理侧处理反馈
     */
    public static final String API_SUBMISSION_MANAGE = "feedback.submission.manage";
    /**
     * 本人反馈只读（我的反馈）
     */
    public static final String API_SUBMISSION_MINE = "feedback.submission.mine";
    /**
     * 提交提示（交集应用+邮箱预填）
     */
    public static final String API_SUBMISSION_HINT = "feedback.submission.hint";
    /**
     * 应用选择列表（启用中注册项）
     */
    public static final String API_APPLICATION_LIST = "feedback.application.list";
    /**
     * 注册表读取（管理）
     */
    public static final String API_APPLICATION_READ = "feedback.application.read";
    /**
     * 注册表维护（管理）
     */
    public static final String API_APPLICATION_MANAGE = "feedback.application.manage";
    /**
     * DATA 资源：反馈提交（维度 application）
     */
    public static final String DATA_RESOURCE_SUBMISSION = "feedback:submission";

    // ==================== DATA 资源与动作 ====================
    /**
     * DATA 资源：应用注册表（维度 application）
     */
    public static final String DATA_RESOURCE_APPLICATION = "feedback:application";
    /**
     * DATA 动作：读取
     */
    public static final String DATA_ACTION_READ = "read";
    /**
     * DATA 动作：处理/维护
     */
    public static final String DATA_ACTION_MANAGE = "manage";
    /**
     * DATA 维度：应用编码
     */
    public static final String DATA_DIMENSION_APPLICATION = "application";

    private FeedbackPermissionConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}

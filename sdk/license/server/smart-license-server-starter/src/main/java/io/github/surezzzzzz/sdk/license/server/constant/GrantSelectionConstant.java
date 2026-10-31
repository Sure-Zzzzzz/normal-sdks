package io.github.surezzzzzz.sdk.license.server.constant;

/**
 * 候选授权发现的分页预算与安全消息。
 *
 * @author surezzzzzz
 */
public final class GrantSelectionConstant {

    public static final int SCAN_BATCH_SIZE = 200;
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final String INVALID_REQUEST = "候选请求的对象种类或动作无效";
    public static final String PROXY_UNSUPPORTED = "当前候选发现不接受代理对象证明";
    public static final String UNEXPECTED_ACTIVATION = "人员或服务候选不得携带激活编号";
    public static final String ACTIVATION_REQUIRED = "部署候选必须携带激活编号";
    public static final String DIRECTORY_UNAVAILABLE = "成员资格目录不可用";
    public static final String DEPENDENCY_UNAVAILABLE = "候选授权依赖不可用";
    public static final String INVALID_PAGE_TYPE = "%s 必须为整数";
    public static final String INVALID_PAGE_RANGE = "%s 超出范围";

    private GrantSelectionConstant() {
    }
}

package io.github.surezzzzzz.sdk.auth.aksk.server.constant;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;

/**
 * Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ServerErrorMessage {

    private ServerErrorMessage() {
        throw new AkskException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

    // ==================== 配置错误 ====================

    public static final String JWT_CONFIG_ERROR = "JWT配置错误：%s";

    public static final String JWT_PUBLIC_KEY_NOT_CONFIGURED = "JWT公钥未配置";

    public static final String JWT_PRIVATE_KEY_NOT_CONFIGURED = "JWT私钥未配置";

    public static final String JWT_KEY_CONFIG_EMPTY = "密钥配置不能为空";

    public static final String JWT_KEY_FILE_NOT_FOUND = "密钥文件不存在: %s";

    public static final String JWT_KEY_FILE_LOAD_FAILED = "加载密钥文件失败: %s";

    // ==================== 数据库错误 ====================

    public static final String DATABASE_ERROR = "数据库操作失败：%s";

    // ==================== Admin页面消息 ====================

    public static final String ADMIN_CREATE_SUCCESS = "平台级AKSK创建成功！请妥善保存Client Secret，此信息仅显示一次。";

    public static final String ADMIN_DELETE_SUCCESS = "删除成功";

    public static final String ADMIN_DELETE_FAILED = "删除失败：%s";

    public static final String ADMIN_QUERY_FAILED = "查询失败：%s";

    public static final String ADMIN_ENABLE_SUCCESS = "启用成功";

    public static final String ADMIN_ENABLE_FAILED = "启用失败：%s";

    public static final String ADMIN_DISABLE_SUCCESS = "禁用成功";

    public static final String ADMIN_DISABLE_FAILED = "禁用失败：%s";

}

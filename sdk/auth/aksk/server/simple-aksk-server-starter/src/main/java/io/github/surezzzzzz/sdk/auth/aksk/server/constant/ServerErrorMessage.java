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

    // ==================== JWE Token 错误 ====================

    /**
     * JWE Token 生成失败
     */
    public static final String JWE_GENERATE_FAILED = "JWE Token 生成失败：%s";

    /**
     * JWE Token 解析失败
     */
    public static final String JWE_DECODE_FAILED = "JWE Token 解析失败：%s";

    /**
     * JWE Token 解密失败
     */
    public static final String JWE_DECRYPT_FAILED = "JWE Token 解密失败：%s";

    /**
     * JWE Token 签名验证失败
     */
    public static final String JWE_SIGNATURE_VERIFICATION_FAILED = "JWE Token 签名验证失败";

    /**
     * AES-256 密钥未配置
     */
    public static final String AES_256_KEY_NOT_CONFIGURED = "AES-256 encryption key 未配置";

    /**
     * AES-256 密钥格式错误
     */
    public static final String AES_256_KEY_FORMAT_ERROR = "AES-256 密钥格式错误，应为 Base64 编码";

    /**
     * AES-256 密钥长度错误
     */
    public static final String AES_256_KEY_LENGTH_ERROR = "AES-256 密钥长度错误，期望 %d 字节，实际 %d 字节";

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

    public static final String ADMIN_OWNER_INFO_UPDATE_SUCCESS = "归属信息更新成功";

    public static final String ADMIN_UPDATE_FIELD_REQUIRED = "请提供要更新的字段（enabled、scopes、name 或 ownerUserId）";

    // ==================== Token管理消息 ====================

    /**
     * clientId 参数缺失
     */
    public static final String CLIENT_ID_REQUIRED = "clientId 不能为空";

    // ==================== Admin页面-Scope验证消息 ====================

    public static final String ADMIN_SCOPE_NEWLINE_NOT_ALLOWED = "权限范围不允许包含换行符，请使用逗号分隔";

    // ==================== Admin页面-Token操作消息 ====================

    public static final String ADMIN_TOKEN_NOT_FOUND = "Token不存在";

    public static final String ADMIN_TOKEN_QUERY_FAILED = "查询Token详情失败: %s";

    public static final String ADMIN_TOKEN_REQUEST_FAILED = "Token请求失败: %s";

    public static final String ADMIN_TOKEN_AUTH_FAILED = "认证失败: %s - %s";

    public static final String ADMIN_TOKEN_EXCHANGE_FAILED = "换Token失败: %s";

    public static final String ADMIN_TOKEN_CLEANUP_SUCCESS = "清理成功";

    public static final String ADMIN_TOKEN_CLEANUP_NONE = "没有过期Token需要清理";

    public static final String ADMIN_TOKEN_REVOKE_SUCCESS = "Token 撤销成功";

    public static final String ADMIN_TOKEN_REVOKE_FAILED = "撤销失败: %s";

    public static final String ADMIN_TOKEN_INTROSPECT_FAILED = "Introspect 失败: %s";

    // ==================== Security Context 错误 ====================

    /**
     * security_context 超过大小限制
     * 参数: maxSize
     */
    public static final String SECURITY_CONTEXT_TOO_LARGE = "security_context 不能超过 %d 字节";

    // ==================== Admin页面-Client更新消息 ====================

    public static final String ADMIN_SCOPES_UPDATE_SUCCESS = "权限范围更新成功";

    public static final String ADMIN_NAME_UPDATE_SUCCESS = "名称更新成功";

    public static final String ADMIN_UPDATE_FAILED = "更新失败：%s";

    // ==================== Client API消息 ====================

    public static final String SYNC_SCOPES_SUCCESS = "权限同步成功";

    public static final String SYNC_SCOPES_NOT_FOUND = "未找到需要更新的AKSK";

    // ==================== OAuth2 Scope错误 ====================

    /**
     * Client在数据库中没有注册scope
     */
    public static final String OAUTH2_CLIENT_NO_SCOPES = "Client has no registered scopes in database";

}

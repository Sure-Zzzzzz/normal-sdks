package io.github.surezzzzzz.sdk.auth.iam.server.event;

import lombok.Getter;

/**
 * IAM 认证事件（登录成败 / 登出 / 账号锁定）。
 *
 * <p>覆盖本地密码与外部身份源（凭证型 / 跳转型）全部登录入口；失败事件的
 * {@code errorCode} 表达失败原因（凭据错误、账号禁用、账号锁定、外部源故障等），
 * 登录失败时 {@code userId} 可能为空（凭据校验未通过、无法定位用户）。
 *
 * <p>仅携带非敏感元数据；任何情况下不得携带密码、外部凭据原文。
 *
 * @author surezzzzzz
 */
@Getter
public class AuthenticationEvent extends AbstractIamEvent {

    /**
     * 认证动作类型。
     */
    private final AuthenticationEventType eventType;

    /**
     * 登录方式编码（local-password / 外部 providerCode），登出与锁定时为发起入口的编码。
     */
    private final String provider;

    /**
     * 尝试登录的用户名；跳转型登录失败回调时可能为空。
     */
    private final String username;

    /**
     * 用户 ID；登录失败且无法定位用户时为空。
     */
    private final Long userId;

    /**
     * 发起端 IP。
     */
    private final String ip;

    /**
     * 发起端 User-Agent。
     */
    private final String userAgent;

    /**
     * 失败原因错误码（仅 LOGIN_FAILED 携带，成功事件为 null）。
     */
    private final String errorCode;

    /**
     * 触发锁定时的累计失败次数（仅 ACCOUNT_LOCKED 携带，其余为 null）。
     */
    private final Integer failureCount;

    public AuthenticationEvent(Object source, AuthenticationEventType eventType,
                               String provider, String username, Long userId,
                               String ip, String userAgent,
                               String errorCode, Integer failureCount) {
        super(source);
        this.eventType = eventType;
        this.provider = provider;
        this.username = username;
        this.userId = userId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.errorCode = errorCode;
        this.failureCount = failureCount;
    }
}

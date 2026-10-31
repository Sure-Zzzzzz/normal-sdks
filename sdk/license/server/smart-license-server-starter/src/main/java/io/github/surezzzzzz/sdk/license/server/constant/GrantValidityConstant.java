package io.github.surezzzzzz.sdk.license.server.constant;

/**
 * 授权新建默认期限与持久期限读取的安全消息。
 *
 * @author surezzzzzz
 */
public final class GrantValidityConstant {

    public static final long DEFAULT_SERVICE_SECONDS = 365L * 86400L;
    public static final long DEFAULT_GRACE_SECONDS = 7L * 86400L;
    public static final String MISSING_VALIDITY = "授权期限未完整固化，请按商业依据补齐后重试";
    public static final String INVALID_VALIDITY = "授权期限内容非法，请核实并修正持久期限后重试";

    private GrantValidityConstant() {
    }
}

package io.github.surezzzzzz.sdk.auth.aksk.server.constant;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;

/**
 * 管理 REST API 授权常量。
 *
 * @author surezzzzzz
 */
public final class ManagementApiAuthorizationConstant {

    /**
     * 数据访问计划请求属性。
     */
    public static final String REQUEST_ATTRIBUTE_DATA_ACCESS_PLAN =
            ManagementApiAuthorizationConstant.class.getName() + ".dataAccessPlan";
    /**
     * 已验证应用授权快照请求属性。
     */
    public static final String REQUEST_ATTRIBUTE_APPLICATION_AUTHORIZATION =
            ManagementApiAuthorizationConstant.class.getName() + ".applicationAuthorization";

    private ManagementApiAuthorizationConstant() {
        throw new AkskException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }
}

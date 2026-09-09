package io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理员绑定外部身份请求
 *
 * @author surezzzzzz
 */
@Getter
@Setter
public class BindExternalIdentityRequest {

    /**
     * 登录方式编码（须为已装配的外部登录方式，如 ldap-password）
     */
    private String providerCode;

    /**
     * 外部体系稳定唯一标识（LDAP DN/uid、OIDC sub）
     */
    private String externalId;
}

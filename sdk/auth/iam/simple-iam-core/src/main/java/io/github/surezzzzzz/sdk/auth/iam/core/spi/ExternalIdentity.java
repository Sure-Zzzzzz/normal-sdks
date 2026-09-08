package io.github.surezzzzzz.sdk.auth.iam.core.spi;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * 外部身份源认证成功后返回的领域身份对象。
 *
 * <p>由适配器在认证成功后构造；externalId 必须是外部体系内稳定不变的标识
 * （如 LDAP 的 DN 或 uid、OIDC 的 sub），不得使用显示名、邮箱等可变值。</p>
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class ExternalIdentity {

    /**
     * 登录方式编码，与认证器/登录提供方注册的 providerCode 一致（如 ldap-password）。
     */
    private final String providerCode;

    /**
     * 外部体系稳定唯一标识。
     */
    private final String externalId;

    /**
     * 建议的本地用户名（如 uid、sAMAccountName、preferred_username）。
     */
    private final String usernameSuggestion;

    /**
     * 显示名，外部源未提供时可为 null。
     */
    private final String displayName;

    /**
     * 邮箱，外部源未提供时可为 null。
     */
    private final String email;

    /**
     * 外部属性透传（只读），无附加属性时为空 Map。
     */
    @Builder.Default
    private final Map<String, Object> attributes = Collections.emptyMap();
}

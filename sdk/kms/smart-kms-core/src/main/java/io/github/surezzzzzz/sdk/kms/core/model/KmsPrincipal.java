package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import lombok.Getter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 已认证 KMS 主体。
 *
 * <p>tenant 必须由认证结果随主体一同传入，任何调用方都不能另行指定 tenant。
 * scope 是服务端完成身份认证后授予的精确权限集合。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class KmsPrincipal {

    /**
     * 已认证主体的稳定标识。
     */
    private final String principalId;
    /**
     * 主体所属 tenant，作为所有资源隔离判断的唯一来源。
     */
    private final String tenantId;
    /**
     * 已认证主体持有的不可变 scope 集合。
     */
    private final Set<String> scopes;

    /**
     * 创建认证后的 KMS 主体快照。
     *
     * @param principalId 已认证主体标识
     * @param tenantId    主体所属 tenant
     * @param scopes      已授予的 scope，可为 {@code null}，表示空集合
     */
    public KmsPrincipal(String principalId, String tenantId, Set<String> scopes) {
        this.principalId = KmsValidationHelper.requirePrincipalId(principalId);
        this.tenantId = KmsValidationHelper.requireTenantId(tenantId);
        this.scopes = Collections.unmodifiableSet(new HashSet<String>(scopes == null
                ? Collections.<String>emptySet() : scopes));
    }

    /**
     * 判断主体是否持有精确 scope。
     *
     * @param scope 待判断的 scope
     * @return 持有该精确 scope 时返回 {@code true}
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}

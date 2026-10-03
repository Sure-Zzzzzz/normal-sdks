package io.github.surezzzzzz.sdk.kms.core.model;

import io.github.surezzzzzz.sdk.kms.core.support.KmsValidationHelper;
import lombok.Getter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 已认证的 KMS 操作主体。
 *
 * <p>主体只标识实际操作者。资源归属由 {@link KmsKey} 的 ownerPrincipalId 表达，不能把主体
 * 自身误当作数据范围。</p>
 *
 * @author surezzzzzz
 */
@Getter
public final class KmsPrincipal {

    private final String principalId;
    /**
     * 当前领域操作针对的资源归属。自助场景固定等于 principalId；管理场景只能由已验证
     * DataPlan 选定，不能来自 HTTP 请求的任意字段。
     */
    private final String ownerPrincipalId;
    private final Set<String> scopes;

    /**
     * 创建认证后的 KMS 主体快照。
     *
     * @param principalId 已认证主体标识
     * @param scopes      已授予的 API 权限快照，可为 {@code null}
     */
    public KmsPrincipal(String principalId, Set<String> scopes) {
        this(principalId, principalId, scopes);
    }

    /**
     * 创建带已验证目标归属的 KMS 主体快照。
     *
     * @param principalId      操作主体标识
     * @param ownerPrincipalId 已授权的目标资源归属
     * @param scopes           已授予的 API 权限快照，可为 {@code null}
     */
    public KmsPrincipal(String principalId, String ownerPrincipalId, Set<String> scopes) {
        this.principalId = KmsValidationHelper.requirePrincipalId(principalId);
        this.ownerPrincipalId = KmsValidationHelper.requirePrincipalId(ownerPrincipalId);
        this.scopes = Collections.unmodifiableSet(new HashSet<String>(scopes == null
                ? Collections.<String>emptySet() : scopes));
    }

    /**
     * 判断主体是否持有精确权限。
     *
     * @param scope 待判断的权限
     * @return 持有该权限时返回 {@code true}
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}

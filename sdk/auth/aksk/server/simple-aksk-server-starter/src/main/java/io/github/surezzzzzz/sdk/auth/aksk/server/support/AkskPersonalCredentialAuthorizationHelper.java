package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 个人 AKU 管理资源的数据范围收敛辅助。
 *
 * <p>身份源的三权仍是唯一授权来源。本辅助只对继承型 AKU 的 Client、Token 管理资源追加
 * 不可放宽的 ownerUserId 条件，避免 {@code all=true} 在个人凭证上扩大为全量数据访问。</p>
 *
 * <p>收敛按行类型区分：用户级（AKU）记录始终限定为本人；平台级（AKP）记录没有 owner，
 * 不属于个人凭证天花板范围——{@code all=true} 的全量授予（如平台管理员特权）在
 * 平台行上按原授权宽度放行，受限授予是否可见平台行由其自身约束决定。</p>
 *
 * @author surezzzzzz
 */
public final class AkskPersonalCredentialAuthorizationHelper {

    private AkskPersonalCredentialAuthorizationHelper() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 为继承型 AKU 生成受所属人约束的有效授权；所属人非法时失败关闭。
     *
     * @param source      身份源当前有效授权
     * @param ownerUserId 不可变 binding 中的所属用户标识
     * @return 收紧后的授权；无法建立 owner 边界时返回 {@code null}
     */
    public static ApplicationAuthorizationContext restrictToOwner(
            ApplicationAuthorizationContext source, String ownerUserId) {
        if (source == null || isBlank(ownerUserId)
                || source.getSubjectType() != ApplicationAuthorizationSubjectType.HUMAN
                || !ownerUserId.equals(source.getSubjectId())) {
            return null;
        }
        DataGrantDocument sourceDocument = source.getDataGrantDocument();
        DataGrantDocument restrictedDocument = sourceDocument == null
                ? null : restrictDocument(sourceDocument, ownerUserId);
        return new ApplicationAuthorizationContext(source.getProtocol(), source.getVersion(), source.getSubjectType(),
                source.getSubjectId(), source.getApplicationCode(), source.isAdmitted(), source.getRoles(),
                source.getPagePermissions(), source.getApiPermissions(), restrictedDocument,
                source.getAuthorizationVersion(), source.getManifestVersion(), source.getManifestDigest(),
                source.getIssuedAt(), source.getExpiresAt());
    }

    private static DataGrantDocument restrictDocument(DataGrantDocument source, String ownerUserId) {
        List<DataGrant> grants = new ArrayList<DataGrant>();
        for (DataGrant grant : source.getGrants()) {
            collectRestrictedGrants(grant, ownerUserId, grants);
        }
        if (grants.isEmpty()) {
            return null;
        }
        return new DataGrantDocument(source.getProtocol(), source.getVersion(), grants);
    }

    /**
     * 个人凭证资源的收敛结果可能为一或两条：用户行（ownerUserId=本人）保底一条；
     * 平台行（clientType=platform）仅在原授予为全量（all=true，如平台管理员特权）时
     * 追加放行，受限授予对平台行的可见性由其自身约束在行级判定。
     */
    private static void collectRestrictedGrants(DataGrant source, String ownerUserId, List<DataGrant> out) {
        if (!isPersonalCredentialResource(source.getResource())) {
            out.add(source);
            return;
        }
        if (source.isAll()) {
            out.add(new DataGrant(source.getResource(), source.getActions(), false,
                    Collections.singletonList(ownerConstraint(ownerUserId))));
            out.add(new DataGrant(source.getResource(), source.getActions(), false,
                    Collections.singletonList(platformConstraint())));
            return;
        }
        List<DataConstraint> constraints = new ArrayList<DataConstraint>();
        boolean hasOwnerConstraint = false;
        for (DataConstraint constraint : source.getConstraints()) {
            if (!SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID.equals(constraint.getDimension())) {
                constraints.add(constraint);
                continue;
            }
            hasOwnerConstraint = true;
            if (!constraint.getValues().contains(ownerUserId)) {
                return;
            }
            constraints.add(ownerConstraint(ownerUserId));
        }
        // 受限授予（含或不含 owner 约束）统一收敛到本人：用户行保护不打折；
        // 平台行不受此条匹配（ownerUserId 维度在平台行上不存在），其可见性只由上面的全量追加条决定。
        if (!hasOwnerConstraint) {
            constraints.add(ownerConstraint(ownerUserId));
        }
        out.add(new DataGrant(source.getResource(), source.getActions(), false, constraints));
    }

    private static DataConstraint platformConstraint() {
        return new DataConstraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_TYPE,
                DataConstraintOperator.IN, Collections.singletonList(ClientType.PLATFORM.getValue()));
    }

    private static boolean isPersonalCredentialResource(String resource) {
        return SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT.equals(resource)
                || SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN.equals(resource);
    }

    private static DataConstraint ownerConstraint(String ownerUserId) {
        return new DataConstraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID,
                DataConstraintOperator.IN, Collections.singletonList(ownerUserId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

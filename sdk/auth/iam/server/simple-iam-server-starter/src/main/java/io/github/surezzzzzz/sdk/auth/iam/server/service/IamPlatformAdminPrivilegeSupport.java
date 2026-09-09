package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 平台管理员特权支撑（解析层合并，不落授权表）
 *
 * <p>平台约定：持有内置角色 iam_admin 的用户即平台管理员。应用授权解析
 * （token 权限集、资源校验、门户可达列表）发现该角色时，该用户对该应用
 * admitted=true 且权限为权限清单（manifest）申报范围的全量——全部角色 /
 * 页面码 / 接口码，数据权限为每种申报资源的 all 全量授权项。</p>
 *
 * <p>特权永不写库：应用注册无需为任何用户回填授权，挂 / 摘角色即刻生效
 * （解析时现查有效角色），manifest 升版特权内容自动跟随（解析时现读清单）。
 * 对特权用户撤销单应用授权不改变解析结果——降权唯一路径是摘除角色；
 * 授权管理面按 platformAdmin 标记禁用撤销入口并提示，避免误以为撤销失效。</p>
 *
 * <p>未申报 manifest 的应用特权退化为"仅准入"：空权限集 context，资源端
 * 失败关闭，语义与空权限手工行一致。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamPlatformAdminPrivilegeSupport {

    private static final String UNMANIFESTED_VERSION = "0";
    private static final String UNMANIFESTED_DIGEST = "unmanifested";

    private final IamEffectiveRoleResolver effectiveRoleResolver;
    private final IamRoleRepository roleRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamApplicationPermissionManifestRepository manifestRepository;

    /**
     * 用户是否平台管理员（有效角色含内置 iam_admin：个人直挂或直属部门挂载均算）
     */
    public boolean isPlatformAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        Set<Long> roleIds = effectiveRoleResolver.resolveRoleIds(userId);
        if (roleIds.isEmpty()) {
            return false;
        }
        return roleRepository.findAllById(roleIds).stream()
                .anyMatch(role -> SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN.equals(role.getCode()));
    }

    /**
     * 合成平台管理员对某应用的特权授权上下文：admitted=true + manifest 申报范围全量。
     *
     * @return 应用不存在返回 null；无 manifest 返回空权限集 context（仅准入）
     */
    public ApplicationAuthorizationContext buildPrivilegedContext(Long userId, Long applicationId,
                                                                  Instant issuedAt, Instant expiresAt) {
        IamTrustedApplicationEntity application = trustedApplicationRepository
                .findById(applicationId).orElse(null);
        if (application == null) {
            return null;
        }
        IamApplicationPermissionManifestEntity manifest = manifestRepository
                .findByApplicationId(applicationId).orElse(null);
        if (manifest == null) {
            log.debug("平台管理员特权解析（无清单，仅准入）：userId={}, applicationId={}", userId, applicationId);
            return new ApplicationAuthorizationContext(
                    SimpleApplicationAuthorizationConstant.PROTOCOL,
                    SimpleApplicationAuthorizationConstant.VERSION,
                    ApplicationAuthorizationSubjectType.HUMAN,
                    String.valueOf(userId),
                    application.getApplicationCode(),
                    true,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null,
                    1L, UNMANIFESTED_VERSION, UNMANIFESTED_DIGEST,
                    issuedAt, expiresAt);
        }
        List<DataGrant> grants = new ArrayList<>();
        for (DataResourceDeclaration resource : IamApplicationAuthorizationJsonCodec
                .readDataResourceList(manifest.getDataResourcesJson())) {
            grants.add(new DataGrant(resource.getResource(), resource.getActions(), true,
                    Collections.emptyList()));
        }
        DataGrantDocument dataGrantDocument = grants.isEmpty()
                ? null
                : new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, grants);
        log.debug("平台管理员特权解析（清单全量）：userId={}, applicationId={}, manifestVersion={}",
                userId, applicationId, manifest.getManifestVersion());
        return new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN,
                String.valueOf(userId),
                application.getApplicationCode(),
                true,
                IamApplicationAuthorizationJsonCodec.readStringList(manifest.getRolesJson(), "roles"),
                IamApplicationAuthorizationJsonCodec.readStringList(
                        manifest.getPagePermissionsJson(), "pagePermissions"),
                IamApplicationAuthorizationJsonCodec.readStringList(
                        manifest.getApiPermissionsJson(), "apiPermissions"),
                dataGrantDocument,
                manifest.getManifestVersion(),
                String.valueOf(manifest.getManifestVersion()),
                manifest.getManifestDigest(),
                issuedAt, expiresAt);
    }
}

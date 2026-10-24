package io.github.surezzzzzz.sdk.auth.iam.server.service.authorization;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.manifest.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.manifest.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.TrustedApplicationBuiltInResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

/**
 * 平台管理员特权支撑（解析层合并，不落授权表）
 *
 * <p>平台约定：持有内置角色 iam_admin 的用户即平台管理员（1.3.1 起特权域=内置应用，
 * 见「角色边界定案」）。应用授权解析（token 权限集、资源校验、门户可达列表）发现
 * 该角色且目标应用为内置应用时，该用户对该应用 admitted=true 且权限为权限清单
 * （manifest）申报范围的全量——全部角色 / 页面码 / 接口码，数据权限为每种申报资源
 * 的 all 全量授权项。非内置（业务）应用不走特权：要访问必须有真实授权行（显式授予
 * 或业务自宣告角色投影）。</p>
 *
 * <p>特权永不写库：应用注册无需为任何用户回填授权，挂 / 摘角色即刻生效
 * （解析时现查有效角色），manifest 升版特权内容自动跟随（解析时现读清单）。
 * 对特权用户撤销内置应用的单应用授权不改变解析结果（解析层特权兜底）——降权
 * 唯一路径是摘除角色；非内置应用撤销后按授权行缺失处理，特权不再兜底（1.3.1）。
 * 响应中的 platformAdmin 标记仅表示"该用户是平台管理员"，管理面据此提示，
 * 无逻辑拦截。</p>
 *
 * <p>未申报 manifest 的【内置】应用特权退化为"仅准入"：空权限集 context，资源端
 * 失败关闭，语义与空权限手工行一致；非内置应用无论有无清单均不走特权（1.3.1）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamPlatformAdminPrivilegeService {

    private static final String UNMANIFESTED_VERSION = "0";
    private static final String UNMANIFESTED_DIGEST = "unmanifested";

    private final IamUserRepository userRepository;
    private final IamEffectiveRoleResolver effectiveRoleResolver;
    private final IamRoleRepository roleRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamApplicationPermissionManifestRepository manifestRepository;
    private final TrustedApplicationBuiltInResolver builtInResolver;

    /**
     * 用户是否平台管理员（有效角色含内置 iam_admin：个人直挂或直属部门挂载均算）
     */
    public boolean isPlatformAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        boolean hasSubjectId = userRepository.findById(userId)
                .map(user -> StringUtils.hasText(user.getSubjectId()))
                .orElse(false);
        if (!hasSubjectId) {
            log.debug("平台管理员角色解析跳过：用户不存在或缺少有效 subjectId，userId={}", userId);
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
     * 合成平台管理员对某内置应用的特权授权上下文：admitted=true + manifest 申报范围全量。
     *
     * @return 应用不存在或非内置应用返回 null；无 manifest 的内置应用返回空权限集 context（仅准入）
     */
    /**
     * 平台管理员兜底上下文的主体段：公开主体 ID；缺失即不兜底（失败关闭，不留数字 id 语义）。
     */
    private String resolveSubjectId(Long userId) {
        String subjectId = userRepository.findById(userId)
                .map(user -> user.getSubjectId())
                .filter(StringUtils::hasText)
                .orElse(null);
        if (!StringUtils.hasText(subjectId)) {
            throw new SimpleIamServerException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    "平台管理员主体缺失有效 subject_id，拒绝兜底上下文");
        }
        return subjectId;
    }

    public ApplicationAuthorizationContext buildPrivilegedContext(Long userId, Long applicationId,
                                                                  Instant issuedAt, Instant expiresAt) {
        IamTrustedApplicationEntity application = trustedApplicationRepository
                .findById(applicationId).orElse(null);
        if (application == null) {
            return null;
        }
        // 1.3.1 特权域收窄：非内置（业务）应用不走平台特权——有清单/无清单两分支同收，
        // 不留"仅准入"的隐性通道；要访问须有真实授权行
        if (!builtInResolver.isBuiltIn(application)) {
            log.debug("平台管理员特权不适用于非内置应用：userId={}, applicationId={}", userId, applicationId);
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
                    resolveSubjectId(userId),
                    application.getApplicationCode(),
                    true,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null,
                    1L, UNMANIFESTED_VERSION, UNMANIFESTED_DIGEST,
                    issuedAt, expiresAt);
        }
        DataGrantDocument dataGrantDocument = synthesizeManifestDataGrant(manifest);
        log.debug("平台管理员特权解析（清单全量）：userId={}, applicationId={}, manifestVersion={}",
                userId, applicationId, manifest.getManifestVersion());
        return new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN,
                resolveSubjectId(userId),
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

    /**
     * 特权保底合并（1.3.7 特权短路缺陷修复）：平台管理员对内置应用的有效授权行投影
     * 与清单申报全量取并集——行可以更细，但不因空准入行劣化为空。
     *
     * <p>pages/apis = union(行投影, 清单)；DATA = 清单全量合成（行 DATA 必然 ⊆ 清单，直接替换）；
     * roles 保留行的（用户真实角色，展示性字段）；authorizationVersion 保留行的（投影乐观锁语义），
     * manifestVersion/digest 取当前清单（union 后内容与当前清单强相关，版本应如实）。
     * 非平台管理员或非内置应用：原样返回（零行为变化）。无清单的内置应用：返回行
     * （无清单可并，行已是唯一事实；"仅准入"语义由行自带）。</p>
     *
     * @param rowContext    行投影构建的上下文（非 null）
     * @param userId        用户 ID
     * @param applicationId 应用 ID
     * @return 保底合并后的上下文（入参原样或增强副本，永不返回 null）
     */
    public ApplicationAuthorizationContext applyPrivilegeFloor(ApplicationAuthorizationContext rowContext,
                                                               Long userId, Long applicationId) {
        IamTrustedApplicationEntity application = trustedApplicationRepository
                .findById(applicationId).orElse(null);
        if (application == null) {
            return rowContext;
        }
        if (!isPlatformAdmin(userId) || !builtInResolver.isBuiltIn(application)) {
            return rowContext;
        }
        IamApplicationPermissionManifestEntity manifest = manifestRepository
                .findByApplicationId(applicationId).orElse(null);
        if (manifest == null) {
            return rowContext;
        }
        Set<String> pages = new LinkedHashSet<>(rowContext.getPagePermissions());
        pages.addAll(IamApplicationAuthorizationJsonCodec.readStringList(
                manifest.getPagePermissionsJson(), "pagePermissions"));
        Set<String> apis = new LinkedHashSet<>(rowContext.getApiPermissions());
        apis.addAll(IamApplicationAuthorizationJsonCodec.readStringList(
                manifest.getApiPermissionsJson(), "apiPermissions"));
        log.info("特权保底合并：userId={}, applicationId={}, 行 pages={}/apis={} → 并集 pages={}/apis={}",
                userId, applicationId,
                rowContext.getPagePermissions().size(), rowContext.getApiPermissions().size(),
                pages.size(), apis.size());
        return new ApplicationAuthorizationContext(
                rowContext.getProtocol(),
                rowContext.getVersion(),
                rowContext.getSubjectType(),
                rowContext.getSubjectId(),
                rowContext.getApplicationCode(),
                true,
                rowContext.getRoles(),
                new ArrayList<>(pages),
                new ArrayList<>(apis),
                synthesizeManifestDataGrant(manifest),
                rowContext.getAuthorizationVersion(),
                String.valueOf(manifest.getManifestVersion()),
                manifest.getManifestDigest(),
                rowContext.getIssuedAt(),
                rowContext.getExpiresAt());
    }

    /**
     * 清单 DATA 全量合成：每种申报资源 all 全动作授权项（特权语义，无清单资源返回 null）。
     */
    private DataGrantDocument synthesizeManifestDataGrant(IamApplicationPermissionManifestEntity manifest) {
        List<DataGrant> grants = new ArrayList<>();
        for (DataResourceDeclaration resource : IamApplicationAuthorizationJsonCodec
                .readDataResourceList(manifest.getDataResourcesJson())) {
            grants.add(new DataGrant(resource.getResource(), resource.getActions(), true,
                    Collections.emptyList()));
        }
        return grants.isEmpty()
                ? null
                : new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, grants);
    }
}

package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleAuthorizationRuleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRoleAuthorizationRuleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * IAM 授权投影更新服务。
 *
 * <p>投影内容 = 用户当前全部角色在该应用下授权规则的并集
 * （page / api 各自并集，DATA 为全部规则模板 grants 并集，无规则即空集）。
 * 本服务在五类事件后重算投影：
 * <ol>
 *   <li>准入授予 {@link #grantApplicationAdmission(Long, Long)}</li>
 *   <li>用户角色分配 / 撤销 {@link #onUserRoleAssigned(Long)}</li>
 *   <li>角色授权规则增删改 {@link #onRoleAuthorizationRuleChanged(Long, Long)}</li>
 *   <li>应用权限清单替换 {@link #onManifestUpdated(Long)}</li>
 *   <li>准入撤销 {@link #revokeApplicationAdmission(Long, Long)}</li>
 * </ol>
 *
 * <p>语义约定：重算只改权限内容与 authorizationVersion；
 * admitted 归准入动作管理——新建投影默认 admitted=1，更新既有投影保持原值，
 * 显式授予 / 撤销才翻转 admitted。管理面手工 PUT 授权
 * （{@link IamApplicationAuthorizationAdminService}）写入的内容
 * 会被下一次触发事件的重算覆盖。
 * 一切权限经角色：DATA 权威通道 = 角色规则模板并集投影，手工授权里的
 * DATA 编辑是重算前的临时精调，角色一变即被规则并集刷新。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamAuthorizationProjectionService {

    private final IamApplicationAuthorizationRepository authorizationRepository;
    private final IamRoleRepository roleRepository;
    private final IamRoleAuthorizationRuleRepository ruleRepository;
    private final IamApplicationPermissionManifestService manifestService;
    private final IamEffectiveRoleResolver effectiveRoleResolver;

    /**
     * 场景1：管理员为用户授予某应用的准入权限。
     * 重算投影内容并置 admitted=1（重复授予幂等）。
     */
    @Transactional
    public void grantApplicationAdmission(Long userId, Long applicationId) {
        log.info("授权投影更新：准入授权触发 - userId={}, applicationId={}", userId, applicationId);
        recomputeProjection(userId, applicationId, true);
    }

    /**
     * 场景2：用户角色分配或撤销。
     *
     * <p>重算范围 = 用户已有生效投影的应用 ∪ 用户当前角色规则覆盖的应用，
     * 后者会在规则首次覆盖时新建投影（admitted=1）；
     * 角色撤销后不再覆盖的应用投影收缩为空权限集，admitted 保持原值。
     */
    @Transactional
    public void onUserRoleAssigned(Long userId) {
        log.info("授权投影更新：角色分配触发 - userId={}", userId);
        Set<Long> applicationIds = new LinkedHashSet<>();
        for (IamApplicationAuthorizationEntity authorization : authorizationRepository.findByUserId(userId)) {
            if (authorization.getAdmitted() != null
                    && SimpleIamServerConstant.STATUS_ACTIVE == authorization.getAdmitted().intValue()) {
                applicationIds.add(authorization.getApplicationId());
            }
        }
        List<Long> roleIds = new ArrayList<>(effectiveRoleResolver.resolveRoleIds(userId));
        if (!roleIds.isEmpty()) {
            applicationIds.addAll(ruleApplicationIds(roleIds));
        }
        for (Long applicationId : applicationIds) {
            recomputeProjection(userId, applicationId, false);
        }
    }

    /**
     * 场景3：角色在某应用下的授权规则新增 / 修改 / 删除。
     *
     * <p>对持有该角色的全部用户重算该应用投影：
     * 无投影的用户新建投影（admitted=1），已有投影的用户更新内容。
     */
    @Transactional
    public void onRoleAuthorizationRuleChanged(Long roleId, Long applicationId) {
        log.info("授权投影更新：角色规则变更触发 - roleId={}, applicationId={}", roleId, applicationId);
        List<Long> userIds = new ArrayList<>(effectiveRoleResolver.resolveUserIdsByRoleId(roleId));
        for (Long userId : userIds) {
            recomputeProjection(userId, applicationId, false);
        }
    }

    /**
     * 场景4：应用申报新版权限清单。
     *
     * <p>角色规则覆盖到的全部用户全量重算投影（内容 + manifest 版本摘要）；
     * 不再被任何规则覆盖的存量投影仅同步 manifestVersion / manifestDigest，
     * 避免清单替换后投影长期挂着旧版本号。
     */
    @Transactional
    public void onManifestUpdated(Long applicationId) {
        log.info("授权投影更新：清单更新触发 - applicationId={}", applicationId);
        IamApplicationPermissionManifestEntity manifest = manifestService.requireManifest(applicationId);
        Set<Long> ruleRoleIds = ruleRepository.findByApplicationId(applicationId).stream()
                .map(IamRoleAuthorizationRuleEntity::getRoleId)
                .collect(Collectors.toSet());
        Set<Long> coveredUserIds = new LinkedHashSet<>();
        if (!ruleRoleIds.isEmpty()) {
            coveredUserIds = effectiveRoleResolver.resolveUserIdsByRoleIdIn(ruleRoleIds);
        }
        for (Long userId : coveredUserIds) {
            recomputeProjection(userId, applicationId, false);
        }
        int syncedOnly = 0;
        for (IamApplicationAuthorizationEntity authorization : authorizationRepository
                .findByApplicationId(applicationId)) {
            if (coveredUserIds.contains(authorization.getUserId())) {
                continue;
            }
            authorization.setManifestVersion(manifest.getManifestVersion().toString());
            authorization.setManifestDigest(manifest.getManifestDigest());
            authorization.setUpdatedAt(Instant.now());
            authorizationRepository.save(authorization);
            syncedOnly++;
        }
        log.info("授权投影清单同步完成：applicationId={}, 重算用户数={}, 仅同步版本数={}",
                applicationId, coveredUserIds.size(), syncedOnly);
    }

    /**
     * 场景5：撤销用户在某应用下的准入权限。
     * admitted 置 0 并记录 revokedAt，投影内容保留用于追溯；
     * 无投影记录时为无操作（幂等）。
     */
    @Transactional
    public void revokeApplicationAdmission(Long userId, Long applicationId) {
        log.info("授权投影更新：撤销准入触发 - userId={}, applicationId={}", userId, applicationId);
        IamApplicationAuthorizationEntity authorization = authorizationRepository
                .findByUserIdAndApplicationId(userId, applicationId)
                .orElse(null);
        if (authorization != null) {
            authorization.setAdmitted(SimpleIamServerConstant.STATUS_INACTIVE);
            authorization.setRevokedAt(Instant.now());
            authorization.setUpdatedAt(Instant.now());
            authorizationRepository.save(authorization);
        }
    }

    /**
     * 重算用户在某应用下的授权投影：用户当前角色规则并集写入内容列。
     *
     * @param admittedActive 新建投影 / 显式准入授予时置 admitted=1；
     *                       例行重算（false）不改动既有投影的 admitted
     */
    private void recomputeProjection(Long userId, Long applicationId, boolean admittedActive) {
        IamApplicationPermissionManifestEntity manifest = manifestService.requireManifest(applicationId);

        List<Long> roleIds = new ArrayList<>(effectiveRoleResolver.resolveRoleIds(userId));

        List<String> roleCodes = roleIds.isEmpty()
                ? Collections.emptyList()
                : roleRepository.findAllById(roleIds).stream()
                .map(IamRoleEntity::getCode)
                .collect(Collectors.toList());

        Set<String> pagePermissions = new HashSet<>();
        Set<String> apiPermissions = new HashSet<>();
        List<DataGrant> dataGrants = new ArrayList<>();
        if (!roleIds.isEmpty()) {
            for (IamRoleAuthorizationRuleEntity rule : ruleRepository
                    .findByRoleIdInAndApplicationId(roleIds, applicationId)) {
                try {
                    pagePermissions.addAll(IamApplicationAuthorizationJsonCodec.readStringList(
                            rule.getPagePermissionsJson(), "pagePermissions"));
                    apiPermissions.addAll(IamApplicationAuthorizationJsonCodec.readStringList(
                            rule.getApiPermissionsJson(), "apiPermissions"));
                    DataGrantDocument template = IamApplicationAuthorizationJsonCodec
                            .readDataGrantDocument(rule.getDataGrantTemplateJson());
                    if (template != null) {
                        dataGrants.addAll(template.getGrants());
                    }
                } catch (Exception e) {
                    log.error("解析角色授权规则失败：ruleId={}", rule.getId(), e);
                }
            }
        } else {
            log.warn("授权投影计算：用户无角色 - userId={}, applicationId={}", userId, applicationId);
        }

        createOrUpdateProjection(userId, applicationId, roleCodes,
                new ArrayList<>(pagePermissions), new ArrayList<>(apiPermissions),
                aggregateDataGrantDocument(dataGrants, userId, applicationId),
                admittedActive, manifest);
    }

    /**
     * 聚合 DATA 投影：用户全部角色规则模板的 grants 并集（DNF OR 语义，
     * 直接拼接后由 DataGrantDocument 构造器规范化排序去重）。
     * 无任何模板返回 null —— 投影无 DATA 文档即资源端失败关闭，语义自洽。
     * 聚合超限（授权项超过文档上限）属配置错误，显式失败而非静默降权。
     */
    private String aggregateDataGrantDocument(List<DataGrant> dataGrants, Long userId, Long applicationId) {
        if (dataGrants.isEmpty()) {
            return null;
        }
        try {
            return IamApplicationAuthorizationJsonCodec.writeDataGrantDocument(new DataGrantDocument(
                    SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION, dataGrants));
        } catch (RuntimeException exception) {
            log.error("DATA 投影聚合失败（模板并集超出文档约束）：userId={}, applicationId={}",
                    userId, applicationId, exception);
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                    String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                            "数据授权模板并集超出授权文档约束（userId=" + userId
                                    + ", applicationId=" + applicationId + "），请收敛各角色模板"));
        }
    }

    private void createOrUpdateProjection(
            Long userId,
            Long applicationId,
            List<String> roleCodes,
            List<String> pagePermissions,
            List<String> apiPermissions,
            String dataGrantDocumentJson,
            boolean admittedActive,
            IamApplicationPermissionManifestEntity manifest) {

        IamApplicationAuthorizationEntity authorization = authorizationRepository
                .findByUserIdAndApplicationId(userId, applicationId)
                .orElse(null);

        boolean created = authorization == null;
        if (created) {
            authorization = new IamApplicationAuthorizationEntity();
            authorization.setUserId(userId);
            authorization.setApplicationId(applicationId);
            authorization.setAuthorizationVersion(1L);
            authorization.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
            authorization.setAdmitted(SimpleIamServerConstant.STATUS_ACTIVE);
            authorization.setCreatedAt(Instant.now());
        } else {
            authorization.setAuthorizationVersion(authorization.getAuthorizationVersion() + 1L);
            if (admittedActive) {
                authorization.setAdmitted(SimpleIamServerConstant.STATUS_ACTIVE);
                authorization.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
                authorization.setRevokedAt(null);
            }
        }

        authorization.setRolesJson(IamApplicationAuthorizationJsonCodec.writeStringList(roleCodes));
        authorization.setPagePermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(pagePermissions));
        authorization.setApiPermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(apiPermissions));
        authorization.setDataGrantDocumentJson(dataGrantDocumentJson);
        authorization.setManifestVersion(manifest.getManifestVersion().toString());
        authorization.setManifestDigest(manifest.getManifestDigest());
        authorization.setUpdatedAt(Instant.now());

        authorizationRepository.save(authorization);

        log.info("授权投影{}成功：userId={}, applicationId={}, roles={}, pages={}, apis={}, version={}",
                created ? "创建" : "更新", userId, applicationId, roleCodes.size(),
                pagePermissions.size(), apiPermissions.size(), authorization.getAuthorizationVersion());
    }

    private Set<Long> ruleApplicationIds(List<Long> roleIds) {
        Set<Long> applicationIds = new LinkedHashSet<>();
        for (IamRoleAuthorizationRuleEntity rule : ruleRepository.findByRoleIdIn(roleIds)) {
            applicationIds.add(rule.getApplicationId());
        }
        return applicationIds;
    }
}

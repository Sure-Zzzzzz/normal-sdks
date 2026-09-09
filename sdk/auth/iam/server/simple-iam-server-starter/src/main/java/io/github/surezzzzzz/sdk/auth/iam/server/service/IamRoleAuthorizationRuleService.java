package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.RoleAuthorizationRuleResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleAuthorizationRuleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRoleAuthorizationRuleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IAM 角色应用授权规则管理服务。
 *
 * <p>规则定义某角色在某应用下拥有的权限集合，是授权投影的计算源：
 * 用户投影权限 = 用户全部角色在该应用下规则的并集（page / api 码与
 * DATA 授权模板同语义聚合）。
 * page / api 权限码与 DATA 模板的 resource / action / dimension
 * 必须落在应用权限清单范围内（申报制）。
 * 规则新增 / 修改 / 删除都会触发持有该角色用户的投影重算。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamRoleAuthorizationRuleService {

    private final IamRoleAuthorizationRuleRepository ruleRepository;
    private final IamRoleRepository roleRepository;
    private final IamTrustedApplicationRepository applicationRepository;
    private final IamApplicationPermissionManifestService manifestService;
    private final IamAuthorizationProjectionService projectionService;

    /**
     * 为角色设置在指定应用下的授权规则（upsert，幂等重放安全）。
     *
     * @param roleId            角色ID
     * @param applicationId     应用ID
     * @param pagePermissions   页面权限码列表（可为空数组，不能为 null）
     * @param apiPermissions    API权限码列表（可为空数组，不能为 null）
     * @param dataGrantTemplate 数据权限授权模板（DataGrantDocument 形态；null = 无数据授权）
     * @return 保存后的规则
     */
    @Transactional
    public RoleAuthorizationRuleResponse putRoleAuthorizationRule(
            Long roleId,
            Long applicationId,
            List<String> pagePermissions,
            List<String> apiPermissions,
            Map<String, Object> dataGrantTemplate) {

        requireRole(roleId);
        requireApplication(applicationId);
        normalize(pagePermissions, apiPermissions);

        IamApplicationPermissionManifestEntity manifest = manifestService.requireManifest(applicationId);
        validatePermissions(pagePermissions, apiPermissions, manifest);
        DataGrantDocument template = manifestService
                .validateDataGrantTemplateWithinManifest(dataGrantTemplate, applicationId);

        IamRoleAuthorizationRuleEntity rule = ruleRepository
                .findByRoleIdAndApplicationId(roleId, applicationId)
                .orElse(null);

        boolean created = rule == null;
        if (created) {
            rule = new IamRoleAuthorizationRuleEntity();
            rule.setRoleId(roleId);
            rule.setApplicationId(applicationId);
            rule.setCreatedAt(Instant.now());
        }

        rule.setPagePermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(pagePermissions));
        rule.setApiPermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(apiPermissions));
        rule.setDataGrantTemplateJson(IamApplicationAuthorizationJsonCodec.writeDataGrantDocument(template));
        rule.setUpdatedAt(Instant.now());

        rule = ruleRepository.save(rule);
        log.info("角色授权规则{}成功：roleId={}, applicationId={}, pages={}, apis={}, dataGrantTemplate={}",
                created ? "创建" : "更新", roleId, applicationId,
                pagePermissions.size(), apiPermissions.size(), template != null);

        projectionService.onRoleAuthorizationRuleChanged(roleId, applicationId);
        return toResponse(rule);
    }

    /**
     * 删除角色在指定应用下的授权规则（无规则时幂等无操作）。
     * 删除后触发持有该角色用户的投影重算（权限收缩为剩余规则并集）。
     *
     * @param roleId        角色ID
     * @param applicationId 应用ID
     */
    @Transactional
    public void deleteRoleAuthorizationRule(Long roleId, Long applicationId) {
        IamRoleAuthorizationRuleEntity rule = ruleRepository
                .findByRoleIdAndApplicationId(roleId, applicationId)
                .orElse(null);
        if (rule == null) {
            return;
        }
        ruleRepository.delete(rule);
        log.info("角色授权规则已删除：roleId={}, applicationId={}", roleId, applicationId);
        projectionService.onRoleAuthorizationRuleChanged(roleId, applicationId);
    }

    /**
     * 查询角色在指定应用下的授权规则。
     *
     * @return 规则；角色对该应用无规则时返回 null（合法状态，非错误）
     */
    @Transactional(readOnly = true)
    public RoleAuthorizationRuleResponse getRoleAuthorizationRule(Long roleId, Long applicationId) {
        requireRole(roleId);
        requireApplication(applicationId);
        return ruleRepository.findByRoleIdAndApplicationId(roleId, applicationId)
                .map(this::toResponse)
                .orElse(null);
    }

    private void requireRole(Long roleId) {
        if (!roleRepository.existsById(roleId)) {
            throw new SimpleIamServerException("角色不存在：" + roleId);
        }
    }

    private void requireApplication(Long applicationId) {
        if (!applicationRepository.existsById(applicationId)) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND_BY_ID, applicationId));
        }
    }

    private void normalize(List<String> pagePermissions, List<String> apiPermissions) {
        String invalidField = null;
        if (pagePermissions == null) {
            invalidField = "pagePermissions 不能为空（可为空数组）";
        } else if (apiPermissions == null) {
            invalidField = "apiPermissions 不能为空（可为空数组）";
        }
        if (invalidField != null) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                    String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID, invalidField));
        }
    }

    private void validatePermissions(
            List<String> pagePermissions,
            List<String> apiPermissions,
            IamApplicationPermissionManifestEntity manifest) {

        Set<String> allowedPages = new HashSet<>(IamApplicationAuthorizationJsonCodec.readStringList(
                manifest.getPagePermissionsJson(), "pagePermissions"));
        Set<String> allowedApis = new HashSet<>(IamApplicationAuthorizationJsonCodec.readStringList(
                manifest.getApiPermissionsJson(), "apiPermissions"));

        for (String code : pagePermissions) {
            if (!allowedPages.contains(code)) {
                throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                        String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                                "页面权限码不在应用清单范围内：" + code));
            }
        }

        for (String code : apiPermissions) {
            if (!allowedApis.contains(code)) {
                throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                        String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                                "API权限码不在应用清单范围内：" + code));
            }
        }
    }

    private RoleAuthorizationRuleResponse toResponse(IamRoleAuthorizationRuleEntity rule) {
        return RoleAuthorizationRuleResponse.builder()
                .roleId(rule.getRoleId())
                .applicationId(rule.getApplicationId())
                .pagePermissions(IamApplicationAuthorizationJsonCodec.readStringList(
                        rule.getPagePermissionsJson(), "pagePermissions"))
                .apiPermissions(IamApplicationAuthorizationJsonCodec.readStringList(
                        rule.getApiPermissionsJson(), "apiPermissions"))
                .dataGrantTemplate(IamApplicationAuthorizationJsonCodec.readDataGrantDocumentMap(
                        rule.getDataGrantTemplateJson()))
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}

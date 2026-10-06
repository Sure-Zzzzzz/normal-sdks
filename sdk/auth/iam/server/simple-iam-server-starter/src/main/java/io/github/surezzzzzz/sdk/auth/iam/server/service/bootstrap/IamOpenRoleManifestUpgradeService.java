package io.github.surezzzzzz.sdk.auth.iam.server.service.bootstrap;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamRoleAuthorizationRuleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.manifest.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleAuthorizationRuleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.manifest.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamRoleAuthorizationRuleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.manifest.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationMutationGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 新装及存量清单增量升级；数据库锁协调每个实例，不依赖引导 Redis 租约。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamOpenRoleManifestUpgradeService {
    private final IamTrustedApplicationRepository applicationRepository;
    private final IamApplicationPermissionManifestRepository manifestRepository;
    private final IamRoleRepository roleRepository;
    private final IamRoleAuthorizationRuleRepository ruleRepository;
    private final IamTrustedApplicationMutationGuard mutationGuard;
    private final IamApplicationPermissionManifestService manifestService;
    private final IamRoleAuthorizationRuleService ruleService;
    private final EntityManager entityManager;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 所有新增精确 API 码；不把粗粒度旧码解释成新能力。
     */
    public static List<String> apiCodes() {
        return Arrays.asList(SimpleIamServerConstant.OPEN_ROLE_CREATE_API, SimpleIamServerConstant.OPEN_ROLE_READ_API,
                SimpleIamServerConstant.OPEN_ROLE_RULE_READ_API, SimpleIamServerConstant.OPEN_ROLE_RULE_WRITE_API,
                SimpleIamServerConstant.OPEN_DEPARTMENT_ROLE_READ_API, SimpleIamServerConstant.OPEN_DEPARTMENT_ROLE_WRITE_API,
                SimpleIamServerConstant.OPEN_DIRECTORY_READ_API, SimpleIamServerConstant.OPEN_APPLICATION_READ_API);
    }

    /**
     * 本批固定资源声明。每次返回独立 DTO，不共享可变请求。
     */
    public static List<DataResourceDeclaration> declarations() {
        List<String> roleDimensions = Arrays.asList(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_APPLICATION_ID,
                SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_ROOT_DEPARTMENT_ID);
        List<String> ruleDimensions = new ArrayList<>(roleDimensions);
        ruleDimensions.add(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_OPEN_ROLE_ID);
        List<String> departmentDimensions = new ArrayList<>(ruleDimensions);
        departmentDimensions.add(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_DEPARTMENT_ID);
        List<String> readWrite = Arrays.asList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ, SimpleIamServerConstant.DATA_RESOURCE_ACTION_WRITE);
        return Arrays.asList(
                declaration(SimpleIamServerConstant.DATA_RESOURCE_OPEN_ROLE,
                        Arrays.asList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_CREATE, SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ), roleDimensions),
                declaration(SimpleIamServerConstant.DATA_RESOURCE_OPEN_ROLE_RULE, readWrite, ruleDimensions),
                declaration(SimpleIamServerConstant.DATA_RESOURCE_OPEN_DEPARTMENT_ROLE, readWrite, departmentDimensions),
                declaration(SimpleIamServerConstant.DATA_RESOURCE_OPEN_DIRECTORY, Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ),
                        Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_ROOT_DEPARTMENT_ID)),
                declaration(SimpleIamServerConstant.DATA_RESOURCE_OPEN_APPLICATION, Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ),
                        Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_DIMENSION_APPLICATION_ID)));
    }

    private static DataResourceDeclaration declaration(String resource, List<String> actions, List<String> dimensions) {
        DataResourceDeclaration declaration = new DataResourceDeclaration();
        declaration.setResource(resource);
        declaration.setActions(new ArrayList<>(actions));
        declaration.setDimensions(new ArrayList<>(dimensions));
        return declaration;
    }

    /**
     * 原清单及所有其他角色保留；重复执行不持续提高清单版本。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void upgrade() {
        IamTrustedApplicationEntity app = applicationRepository.findByApplicationCode(SimpleIamServerConstant.BUILT_IN_APPLICATION_IAM).orElse(null);
        if (app == null) return;
        mutationGuard.requireMutable(app.getId());
        entityManager.refresh(app, LockModeType.PESSIMISTIC_WRITE);
        IamApplicationPermissionManifestEntity manifest = manifestRepository.findByApplicationId(app.getId()).orElse(null);
        if (manifest == null) return;
        entityManager.refresh(manifest, LockModeType.PESSIMISTIC_WRITE);
        List<String> apis = new ArrayList<>(IamApplicationAuthorizationJsonCodec.readStringList(manifest.getApiPermissionsJson(),
                SimpleIamServerConstant.OPEN_ROLE_FIELD_API_PERMISSIONS));
        List<DataResourceDeclaration> resources = new ArrayList<>(IamApplicationAuthorizationJsonCodec.readDataResourceList(manifest.getDataResourcesJson()));
        Map<String, DataResourceDeclaration> existing = resources.stream().collect(Collectors.toMap(DataResourceDeclaration::getResource, value -> value));
        boolean changed = false;
        for (DataResourceDeclaration expected : declarations()) {
            DataResourceDeclaration actual = existing.get(expected.getResource());
            if (actual == null) {
                resources.add(expected);
                changed = true;
            } else if (!new HashSet<>(actual.getActions()).equals(new HashSet<>(expected.getActions()))
                    || !new HashSet<>(actual.getDimensions()).equals(new HashSet<>(expected.getDimensions()))) {
                throw new IamOpenRoleException(ErrorCode.OPEN_ROLE_CONFLICT);
            }
        }
        for (String code : apiCodes())
            if (!apis.contains(code)) {
                apis.add(code);
                changed = true;
            }
        if (changed) {
            PutApplicationPermissionManifestRequest request = new PutApplicationPermissionManifestRequest();
            request.setRoles(new ArrayList<>(IamApplicationAuthorizationJsonCodec.readStringList(manifest.getRolesJson(), SimpleIamServerConstant.OPEN_ROLE_FIELD_ROLES)));
            request.setPagePermissions(new ArrayList<>(IamApplicationAuthorizationJsonCodec.readStringList(manifest.getPagePermissionsJson(), SimpleIamServerConstant.OPEN_ROLE_FIELD_PAGE_PERMISSIONS)));
            request.setApiPermissions(apis);
            request.setDataResources(resources);
            manifestService.putManifest(app.getId(), request);
        }
        upgradeAdminRule(app.getId());
        log.debug("IAM 委托权限清单核对完成：applicationId={}, changed={}", app.getId(), changed);
    }

    private void upgradeAdminRule(Long applicationId) {
        IamRoleEntity admin = roleRepository.findByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).orElse(null);
        if (admin == null) return;
        entityManager.refresh(admin, LockModeType.PESSIMISTIC_WRITE);
        if (!Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE).equals(admin.getBuiltIn())) return;
        IamRoleAuthorizationRuleEntity rule = ruleRepository.findByRoleIdAndApplicationId(admin.getId(), applicationId).orElse(null);
        if (rule == null) return;
        entityManager.refresh(rule, LockModeType.PESSIMISTIC_WRITE);
        List<String> apis = new ArrayList<>(IamApplicationAuthorizationJsonCodec.readStringList(rule.getApiPermissionsJson(), SimpleIamServerConstant.OPEN_ROLE_FIELD_API_PERMISSIONS));
        DataGrantDocument old = IamApplicationAuthorizationJsonCodec.readDataGrantDocument(rule.getDataGrantTemplateJson());
        List<DataGrant> grants = old == null ? new ArrayList<>() : new ArrayList<>(old.getGrants());
        boolean changed = false;
        for (String code : apiCodes())
            if (!apis.contains(code)) {
                apis.add(code);
                changed = true;
            }
        for (DataResourceDeclaration declaration : declarations()) {
            boolean present = grants.stream().anyMatch(grant -> declaration.getResource().equals(grant.getResource())
                    && grant.isAll() && grant.getActions().containsAll(declaration.getActions()));
            if (!present) {
                grants.add(new DataGrant(declaration.getResource(), declaration.getActions(), true, Collections.emptyList()));
                changed = true;
            }
        }
        if (changed) {
            String json = IamApplicationAuthorizationJsonCodec.writeDataGrantDocument(new DataGrantDocument(
                    SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION, grants));
            ruleService.putRoleAuthorizationRule(admin.getId(), applicationId,
                    IamApplicationAuthorizationJsonCodec.readStringList(rule.getPagePermissionsJson(), SimpleIamServerConstant.OPEN_ROLE_FIELD_PAGE_PERMISSIONS),
                    apis, IamApplicationAuthorizationJsonCodec.readDataGrantDocumentMap(json));
            auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.ROLE,
                    admin.getId().toString(), null, SimpleIamServerConstant.OPEN_ROLE_UPGRADE_AUDIT_DETAIL);
        }
    }
}

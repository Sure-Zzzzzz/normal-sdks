package io.github.surezzzzzz.sdk.auth.iam.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.response.ApplicationPermissionManifestResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 可信应用权限清单管理服务（申报制）。
 *
 * <p>清单是应用授权勾选范围的事实源：码空间归应用，IAM 只校验存在性。
 * manifestVersion 服务端单调递增；manifestDigest 为各列表（角色 / 页面 / 接口 /
 * DATA 资源）排序去重后规范化 JSON 的 SHA-256（同集合不同顺序摘要相同）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamApplicationPermissionManifestService {

    private static final int MAX_CODE_LENGTH = 256;
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper();

    private final IamApplicationPermissionManifestRepository manifestRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamAuditEventPublisher auditEventPublisher;
    // ObjectProvider 惰性解析：投影服务反向依赖本服务，构造器直连会成环
    private final ObjectProvider<IamAuthorizationProjectionService> projectionServiceProvider;

    /**
     * 查询应用权限清单。
     *
     * @param applicationId 可信应用ID
     * @return 清单（含版本与摘要）
     */
    public ApplicationPermissionManifestResponse getManifest(Long applicationId) {
        requireApplication(applicationId);
        IamApplicationPermissionManifestEntity entity = requireManifest(applicationId);
        return toResponse(entity);
    }

    /**
     * 登记 / 全量替换应用权限清单（upsert）。
     *
     * <p>无记录则创建（version=1）；已有记录整体替换并 version+1。
     * 重复码折叠；元素 trim 后非空且 ≤256 字符。
     *
     * @param applicationId 可信应用ID
     * @param request       三类码清单（全量）
     * @return 替换后的清单
     */
    @Transactional
    public ApplicationPermissionManifestResponse putManifest(
            Long applicationId, PutApplicationPermissionManifestRequest request) {
        requireApplication(applicationId);
        List<String> roles = normalizeCodes(request.getRoles(), "roles");
        List<String> pagePermissions = normalizeCodes(request.getPagePermissions(), "pagePermissions");
        List<String> apiPermissions = normalizeCodes(request.getApiPermissions(), "apiPermissions");
        List<DataResourceDeclaration> dataResources = normalizeDataResources(request.getDataResources());

        IamApplicationPermissionManifestEntity entity = manifestRepository
                .findByApplicationId(applicationId).orElse(null);
        boolean created = entity == null;
        if (created) {
            entity = new IamApplicationPermissionManifestEntity();
            entity.setApplicationId(applicationId);
            entity.setManifestVersion(0L);
            entity.setCreatedAt(Instant.now());
        }
        entity.setRolesJson(IamApplicationAuthorizationJsonCodec.writeStringList(roles));
        entity.setPagePermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(pagePermissions));
        entity.setApiPermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(apiPermissions));
        entity.setDataResourcesJson(IamApplicationAuthorizationJsonCodec.writeDataResourceList(dataResources));
        entity.setManifestVersion(entity.getManifestVersion() + 1L);
        entity.setManifestDigest(computeDigest(roles, pagePermissions, apiPermissions, dataResources));
        entity.setUpdatedAt(Instant.now());
        try {
            manifestRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_MANIFEST_CONFLICT,
                    String.format(ServerErrorMessage.APPLICATION_MANIFEST_CONFLICT, applicationId));
        }
        log.info("权限清单{}成功：applicationId={}, manifestVersion={}, roles={}, pagePermissions={}, apiPermissions={}, dataResources={}",
                created ? "登记" : "替换", applicationId, entity.getManifestVersion(),
                roles.size(), pagePermissions.size(), apiPermissions.size(), dataResources.size());
        auditEventPublisher.publishAdminAction(
                created ? AdminActionType.CREATED : AdminActionType.REPLACED,
                AdminSubjectType.APPLICATION, String.valueOf(applicationId), null,
                "manifestVersion=" + entity.getManifestVersion() + ", roles=" + roles.size()
                        + ", pagePermissions=" + pagePermissions.size()
                        + ", apiPermissions=" + apiPermissions.size()
                        + ", dataResources=" + dataResources.size());

        // 触发授权投影更新（场景4：应用清单更新）
        projectionServiceProvider.getObject().onManifestUpdated(applicationId);

        return toResponse(entity);
    }

    /**
     * 加载应用当前清单实体（供授权校验），未登记抛 APPLICATION_MANIFEST_NOT_FOUND。
     */
    public IamApplicationPermissionManifestEntity requireManifest(Long applicationId) {
        IamApplicationPermissionManifestEntity entity = manifestRepository
                .findByApplicationId(applicationId).orElse(null);
        if (entity == null) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_MANIFEST_NOT_FOUND,
                    String.format(ServerErrorMessage.APPLICATION_MANIFEST_NOT_FOUND, applicationId));
        }
        return entity;
    }

    /**
     * 校验 DATA 授权模板（DataGrantDocument 形态）落在应用清单 DATA 资源声明范围内：
     * 每条 grant 的 resource / action / constraint.dimension 各自 ⊆ 清单申报。
     * 角色授权规则模板与管理面手工授权的 DATA 编辑共用本口径（与 page / api 码同款申报制）。
     *
     * @param template      模板（null = 无数据授权，直接通过）
     * @param applicationId 可信应用ID
     * @return 已校验的模板文档；输入 null 时返回 null
     */
    public DataGrantDocument validateDataGrantTemplateWithinManifest(
            Map<String, Object> template, Long applicationId) {
        DataGrantDocument document = IamApplicationAuthorizationJsonCodec.parseDataGrantDocument(template);
        if (document == null) {
            return null;
        }
        requireApplication(applicationId);
        Map<String, DataResourceDeclaration> declared = new HashMap<>();
        for (DataResourceDeclaration declaration : IamApplicationAuthorizationJsonCodec
                .readDataResourceList(requireManifest(applicationId).getDataResourcesJson())) {
            declared.put(declaration.getResource(), declaration);
        }
        for (DataGrant grant : document.getGrants()) {
            DataResourceDeclaration declaration = declared.get(grant.getResource());
            if (declaration == null) {
                throw contentInvalid("数据资源不在应用清单范围内：" + grant.getResource());
            }
            for (String action : grant.getActions()) {
                if (!declaration.getActions().contains(action)) {
                    throw contentInvalid("数据动作不在资源申报动作集内：" + grant.getResource() + ":" + action);
                }
            }
            for (DataConstraint constraint : grant.getConstraints()) {
                if (!declaration.getDimensions().contains(constraint.getDimension())) {
                    throw contentInvalid("约束维度不在资源申报维度集内："
                            + grant.getResource() + ":" + constraint.getDimension());
                }
            }
        }
        return document;
    }

    private void requireApplication(Long applicationId) {
        if (!trustedApplicationRepository.existsById(applicationId)) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND_BY_ID, applicationId));
        }
    }

    private List<String> normalizeCodes(List<String> codes, String fieldName) {
        if (codes == null) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                    String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                            fieldName + " 不能为空（可为空数组）"));
        }
        List<String> normalized = new ArrayList<>();
        for (String code : codes) {
            if (code == null) {
                throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                        String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                                fieldName + " 存在 null 元素"));
            }
            String trimmed = code.trim();
            if (trimmed.isEmpty()) {
                throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                        String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                                fieldName + " 存在空白编码"));
            }
            if (trimmed.length() > MAX_CODE_LENGTH) {
                throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                        String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                                fieldName + " 编码超过 " + MAX_CODE_LENGTH + " 字符：" + trimmed));
            }
            if (!normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    /**
     * 规范化 DATA 资源声明：resource trim 后非空、≤256 且不重复；
     * actions / dimensions 各自必填（可为空数组）、元素 trim 后非空且 ≤256、去重。
     */
    private List<DataResourceDeclaration> normalizeDataResources(List<DataResourceDeclaration> declarations) {
        if (declarations == null) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                    String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                            "dataResources 不能为空（可为空数组）"));
        }
        List<DataResourceDeclaration> normalized = new ArrayList<>();
        Set<String> seenResources = new HashSet<>();
        for (DataResourceDeclaration declaration : declarations) {
            if (declaration == null) {
                throw contentInvalid("dataResources 存在 null 元素");
            }
            String resource = requireText(declaration.getResource(), "dataResources.resource");
            if (!seenResources.add(resource)) {
                throw contentInvalid("dataResources 资源标识重复：" + resource);
            }
            DataResourceDeclaration item = new DataResourceDeclaration();
            item.setResource(resource);
            item.setActions(normalizeCodes(declaration.getActions(), "dataResources(" + resource + ").actions"));
            item.setDimensions(normalizeCodes(declaration.getDimensions(),
                    "dataResources(" + resource + ").dimensions"));
            normalized.add(item);
        }
        return normalized;
    }

    private String requireText(String value, String fieldName) {
        if (value == null) {
            throw contentInvalid(fieldName + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw contentInvalid(fieldName + " 不能为空白");
        }
        if (trimmed.length() > MAX_CODE_LENGTH) {
            throw contentInvalid(fieldName + " 超过 " + MAX_CODE_LENGTH + " 字符：" + trimmed);
        }
        return trimmed;
    }

    private SimpleIamServerException contentInvalid(String detail) {
        return new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID, detail));
    }

    private String computeDigest(
            List<String> roles,
            List<String> pagePermissions,
            List<String> apiPermissions,
            List<DataResourceDeclaration> dataResources) {
        Map<String, Object> canonical = new TreeMap<>();
        List<String> sortedRoles = new ArrayList<>(roles);
        sortedRoles.sort(String::compareTo);
        List<String> sortedPages = new ArrayList<>(pagePermissions);
        sortedPages.sort(String::compareTo);
        List<String> sortedApis = new ArrayList<>(apiPermissions);
        sortedApis.sort(String::compareTo);
        List<Map<String, Object>> sortedDataResources = new ArrayList<>();
        for (DataResourceDeclaration declaration : dataResources) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resource", declaration.getResource());
            item.put("actions", sortedCopy(declaration.getActions()));
            item.put("dimensions", sortedCopy(declaration.getDimensions()));
            sortedDataResources.add(item);
        }
        sortedDataResources.sort(Comparator.comparing(item -> String.valueOf(item.get("resource"))));
        canonical.put("apiPermissions", sortedApis);
        canonical.put("dataResources", sortedDataResources);
        canonical.put("pagePermissions", sortedPages);
        canonical.put("roles", sortedRoles);
        try {
            return TokenHashHelper.sha256Hex(CANONICAL_MAPPER.writeValueAsString(canonical));
        } catch (Exception exception) {
            log.warn("权限清单摘要计算失败：applicationId 无关，内容序列化异常", exception);
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                    String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                            "权限清单摘要计算失败"));
        }
    }

    private List<String> sortedCopy(List<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String::compareTo);
        return sorted;
    }

    private ApplicationPermissionManifestResponse toResponse(IamApplicationPermissionManifestEntity entity) {
        return ApplicationPermissionManifestResponse.builder()
                .applicationId(entity.getApplicationId())
                .roles(IamApplicationAuthorizationJsonCodec.readStringList(entity.getRolesJson(), "roles"))
                .pagePermissions(IamApplicationAuthorizationJsonCodec.readStringList(
                        entity.getPagePermissionsJson(), "pagePermissions"))
                .apiPermissions(IamApplicationAuthorizationJsonCodec.readStringList(
                        entity.getApiPermissionsJson(), "apiPermissions"))
                .dataResources(IamApplicationAuthorizationJsonCodec.readDataResourceList(
                        entity.getDataResourcesJson()))
                .manifestVersion(entity.getManifestVersion())
                .manifestDigest(entity.getManifestDigest())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

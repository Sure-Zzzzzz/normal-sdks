package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.PutApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.ApplicationAuthorizationDetailResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.response.ApplicationAuthorizationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * IAM 用户应用授权管理面服务（写侧）。
 *
 * <p>管理面变更立即生效：token 发放与资源令牌验证均实时读取授权投影，
 * 撤销 / 收紧 / 替换在下一次验证即体现。authorizationVersion 由服务端
 * 单调递增，请求不可指定；撤销后再次 PUT 即重激活。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamApplicationAuthorizationAdminService {

    private final IamApplicationAuthorizationRepository authorizationRepository;
    private final IamUserRepository userRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamApplicationPermissionManifestService manifestService;
    private final IamAuditEventPublisher auditEventPublisher;
    private final IamPlatformAdminPrivilegeSupport platformAdminPrivilegeSupport;

    /**
     * 列出用户全部应用授权摘要。
     *
     * @param userId IAM用户ID
     * @return 授权摘要列表
     */
    public List<ApplicationAuthorizationResponse> listAuthorizations(Long userId) {
        requireUser(userId);
        boolean platformAdmin = platformAdminPrivilegeSupport.isPlatformAdmin(userId);
        return authorizationRepository.findByUserId(userId).stream()
                .map(entity -> toSummary(entity, platformAdmin))
                .collect(Collectors.toList());
    }

    /**
     * 查询单条应用授权详情（含四类授权内容投影）。
     *
     * @param userId        IAM用户ID
     * @param applicationId 可信应用ID
     * @return 授权详情
     */
    public ApplicationAuthorizationDetailResponse getAuthorization(Long userId, Long applicationId) {
        requireUser(userId);
        return toDetail(requireAuthorization(userId, applicationId),
                platformAdminPrivilegeSupport.isPlatformAdmin(userId));
    }

    /**
     * 全量替换 / 创建用户应用授权（upsert，幂等重放安全）。
     *
     * <p>无记录则创建（version=1）；已有记录则整体替换授权内容并 version+1；
     * 已撤销记录再次 PUT 即重激活（status 回有效、清空撤销时间）。
     * 三类码与 DATA 授权文档必须为应用当前权限清单的子集；manifestVersion /
     * manifestDigest 由服务端按清单当前版本自动落库，请求不携带。
     * 一切权限经角色：DATA 权威通道为角色规则模板并集投影，此处 DATA 编辑
     * 是重算前的临时精调，角色一变即被规则并集刷新。
     *
     * @param userId        IAM用户ID
     * @param applicationId 可信应用ID
     * @param request       授权内容（全量）
     * @return 替换后的授权详情
     */
    @Transactional
    public ApplicationAuthorizationDetailResponse putAuthorization(
            Long userId, Long applicationId, PutApplicationAuthorizationRequest request) {
        requireUser(userId);
        requireApplication(applicationId);
        normalize(request);
        IamApplicationPermissionManifestEntity manifest = manifestService.requireManifest(applicationId);
        requireCodesWithin(request.getRoles(), manifest.getRolesJson(), "roles");
        requireCodesWithin(request.getPagePermissions(), manifest.getPagePermissionsJson(), "pagePermissions");
        requireCodesWithin(request.getApiPermissions(), manifest.getApiPermissionsJson(), "apiPermissions");
        manifestService.validateDataGrantTemplateWithinManifest(
                request.getDataGrantDocument(), applicationId);
        IamApplicationAuthorizationEntity entity = authorizationRepository
                .findByUserIdAndApplicationId(userId, applicationId).orElse(null);
        boolean created = entity == null;
        if (created) {
            entity = new IamApplicationAuthorizationEntity();
            entity.setUserId(userId);
            entity.setApplicationId(applicationId);
            entity.setCreatedAt(Instant.now());
        }
        entity.setAdmitted(Boolean.TRUE.equals(request.getAdmitted())
                ? SimpleIamServerConstant.STATUS_ACTIVE : SimpleIamServerConstant.STATUS_INACTIVE);
        entity.setRolesJson(IamApplicationAuthorizationJsonCodec.writeStringList(request.getRoles()));
        entity.setPagePermissionsJson(
                IamApplicationAuthorizationJsonCodec.writeStringList(request.getPagePermissions()));
        entity.setApiPermissionsJson(
                IamApplicationAuthorizationJsonCodec.writeStringList(request.getApiPermissions()));
        entity.setDataGrantDocumentJson(
                IamApplicationAuthorizationJsonCodec.writeDataGrantDocument(request.getDataGrantDocument()));
        entity.setManifestVersion(String.valueOf(manifest.getManifestVersion()));
        entity.setManifestDigest(manifest.getManifestDigest());
        entity.setAuthorizationVersion(resolveNextVersion(entity));
        entity.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        entity.setRevokedAt(null);
        entity.setUpdatedAt(Instant.now());
        try {
            authorizationRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONFLICT,
                    String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONFLICT, userId, applicationId));
        }
        log.info("应用授权{}成功：userId={}, applicationId={}, version={}",
                created ? "创建" : "替换", userId, applicationId, entity.getAuthorizationVersion());
        auditEventPublisher.publishAdminAction(
                created ? AdminActionType.GRANTED : AdminActionType.REPLACED,
                AdminSubjectType.APPLICATION_AUTHORIZATION, String.valueOf(userId), null,
                "applicationId=" + applicationId + ", authorizationVersion=" + entity.getAuthorizationVersion());
        return toDetail(entity, platformAdminPrivilegeSupport.isPlatformAdmin(userId));
    }

    /**
     * 撤销用户应用授权（语义撤销留痕，认证与验证立即失效；重复撤销幂等）。
     *
     * @param userId        IAM用户ID
     * @param applicationId 可信应用ID
     */
    @Transactional
    public void revokeAuthorization(Long userId, Long applicationId) {
        requireUser(userId);
        IamApplicationAuthorizationEntity entity = requireAuthorization(userId, applicationId);
        if (entity.getStatus() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == entity.getStatus().intValue()) {
            entity.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
            entity.setRevokedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            authorizationRepository.saveAndFlush(entity);
            log.info("应用授权撤销成功：userId={}, applicationId={}, version={}",
                    userId, applicationId, entity.getAuthorizationVersion());
            auditEventPublisher.publishAdminAction(AdminActionType.REVOKED,
                    AdminSubjectType.APPLICATION_AUTHORIZATION, String.valueOf(userId), null,
                    "applicationId=" + applicationId + ", authorizationVersion=" + entity.getAuthorizationVersion());
        }
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new SimpleIamServerException(ErrorCode.USER_NOT_FOUND,
                    String.format(ServerErrorMessage.USER_NOT_FOUND, userId));
        }
    }

    private void requireApplication(Long applicationId) {
        if (!trustedApplicationRepository.existsById(applicationId)) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND_BY_ID, applicationId));
        }
    }

    private IamApplicationAuthorizationEntity requireAuthorization(Long userId, Long applicationId) {
        IamApplicationAuthorizationEntity entity = authorizationRepository
                .findByUserIdAndApplicationId(userId, applicationId).orElse(null);
        if (entity == null) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_NOT_FOUND,
                    String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_NOT_FOUND, userId, applicationId));
        }
        return entity;
    }

    private void normalize(PutApplicationAuthorizationRequest request) {
        String invalidField = null;
        if (request.getAdmitted() == null) {
            invalidField = "admitted 不能为空";
        } else if (request.getRoles() == null) {
            invalidField = "roles 不能为空（可为空数组）";
        } else if (request.getPagePermissions() == null) {
            invalidField = "pagePermissions 不能为空（可为空数组）";
        } else if (request.getApiPermissions() == null) {
            invalidField = "apiPermissions 不能为空（可为空数组）";
        }
        if (invalidField != null) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                    String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID, invalidField));
        }
    }

    private void requireCodesWithin(List<String> requestCodes, String manifestCodesJson, String fieldName) {
        List<String> manifestCodes = IamApplicationAuthorizationJsonCodec
                .readStringList(manifestCodesJson, fieldName);
        for (String code : requestCodes) {
            if (!manifestCodes.contains(code)) {
                throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                        String.format(ServerErrorMessage.APPLICATION_AUTHORIZATION_CONTENT_INVALID,
                                fieldName + " 编码不在应用权限清单中：" + code));
            }
        }
    }

    private Long resolveNextVersion(IamApplicationAuthorizationEntity entity) {
        Long current = entity.getAuthorizationVersion();
        return (current == null ? 0L : current) + 1L;
    }

    private ApplicationAuthorizationResponse toSummary(IamApplicationAuthorizationEntity entity,
                                                       boolean platformAdmin) {
        return ApplicationAuthorizationResponse.builder()
                .applicationId(entity.getApplicationId())
                .platformAdmin(platformAdmin)
                .admitted(SimpleIamServerConstant.STATUS_ACTIVE == entity.getAdmitted().intValue())
                .authorizationVersion(entity.getAuthorizationVersion())
                .manifestVersion(entity.getManifestVersion())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .revokedAt(entity.getRevokedAt())
                .build();
    }

    private ApplicationAuthorizationDetailResponse toDetail(IamApplicationAuthorizationEntity entity,
                                                            boolean platformAdmin) {
        return ApplicationAuthorizationDetailResponse.builder()
                .applicationId(entity.getApplicationId())
                .platformAdmin(platformAdmin)
                .admitted(SimpleIamServerConstant.STATUS_ACTIVE == entity.getAdmitted().intValue())
                .authorizationVersion(entity.getAuthorizationVersion())
                .manifestVersion(entity.getManifestVersion())
                .status(entity.getStatus())
                .roles(IamApplicationAuthorizationJsonCodec.readStringList(
                        entity.getRolesJson(), "roles"))
                .pagePermissions(IamApplicationAuthorizationJsonCodec.readStringList(
                        entity.getPagePermissionsJson(), "pagePermissions"))
                .apiPermissions(IamApplicationAuthorizationJsonCodec.readStringList(
                        entity.getApiPermissionsJson(), "apiPermissions"))
                .dataGrantDocument(IamApplicationAuthorizationJsonCodec.readDataGrantDocumentMap(
                        entity.getDataGrantDocumentJson()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .revokedAt(entity.getRevokedAt())
                .build();
    }
}

package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

/**
 * IAM 用户应用授权快照服务。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamApplicationAuthorizationService {

    private static final String SQL_FIND_APPLICATION_ID_BY_OAUTH_CLIENT_ID =
            "SELECT application_id FROM oauth2_registered_client WHERE client_id = ?";

    private final IamApplicationAuthorizationRepository authorizationRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamPlatformAdminPrivilegeSupport platformAdminPrivilegeSupport;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 为仍有效的用户应用授权创建权威快照；授权行缺失或无效时，平台管理员
     * （挂内置 iam_admin）特权兜底——manifest 申报范围全量合成，不落授权表。
     */
    public ApplicationAuthorizationContext loadActiveContext(Long userId, Long applicationId,
                                                             Instant issuedAt, Instant expiresAt) {
        if (userId == null || applicationId == null || issuedAt == null || expiresAt == null) {
            return null;
        }
        IamApplicationAuthorizationEntity authorization = authorizationRepository
                .findByUserIdAndApplicationId(userId, applicationId).orElse(null);
        if (!isActiveAndAdmitted(authorization)) {
            if (platformAdminPrivilegeSupport.isPlatformAdmin(userId)) {
                return platformAdminPrivilegeSupport.buildPrivilegedContext(
                        userId, applicationId, issuedAt, expiresAt);
            }
            return null;
        }
        IamTrustedApplicationEntity application = trustedApplicationRepository
                .findById(applicationId).orElse(null);
        if (application == null) {
            return null;
        }
        try {
            return new ApplicationAuthorizationContext(
                    SimpleApplicationAuthorizationConstant.PROTOCOL,
                    SimpleApplicationAuthorizationConstant.VERSION,
                    ApplicationAuthorizationSubjectType.HUMAN,
                    String.valueOf(userId),
                    application.getApplicationCode(),
                    true,
                    IamApplicationAuthorizationJsonCodec.readStringList(
                            authorization.getRolesJson(), "roles"),
                    IamApplicationAuthorizationJsonCodec.readStringList(
                            authorization.getPagePermissionsJson(), "pagePermissions"),
                    IamApplicationAuthorizationJsonCodec.readStringList(
                            authorization.getApiPermissionsJson(), "apiPermissions"),
                    IamApplicationAuthorizationJsonCodec.readDataGrantDocument(
                            authorization.getDataGrantDocumentJson()),
                    authorization.getAuthorizationVersion(),
                    authorization.getManifestVersion(),
                    authorization.getManifestDigest(),
                    issuedAt,
                    expiresAt);
        } catch (RuntimeException exception) {
            if (platformAdminPrivilegeSupport.isPlatformAdmin(userId)) {
                return platformAdminPrivilegeSupport.buildPrivilegedContext(
                        userId, applicationId, issuedAt, expiresAt);
            }
            return null;
        }
    }

    /**
     * 根据现有 OAuth 客户端归属查询可信应用ID。
     */
    public Long findApplicationIdByOAuthClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return null;
        }
        List<Long> applicationIds = jdbcTemplate.queryForList(
                SQL_FIND_APPLICATION_ID_BY_OAUTH_CLIENT_ID, Long.class, clientId);
        if (applicationIds.size() != 1) {
            return null;
        }
        return applicationIds.get(0);
    }

    private boolean isActiveAndAdmitted(IamApplicationAuthorizationEntity authorization) {
        return authorization != null
                && authorization.getStatus() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == authorization.getStatus().intValue()
                && authorization.getAdmitted() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == authorization.getAdmitted().intValue()
                && authorization.getAuthorizationVersion() != null
                && authorization.getAuthorizationVersion().longValue() > 0L;
    }
}

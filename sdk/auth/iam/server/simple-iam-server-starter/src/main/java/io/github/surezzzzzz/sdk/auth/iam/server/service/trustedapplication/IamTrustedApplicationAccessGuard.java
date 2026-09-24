package io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

import java.util.List;
import java.util.Objects;

/**
 * 可信应用 OAuth 生命周期门禁。
 *
 * <p>授权记录里的安全纪元仅用于拒绝停用前签发的旧记录；是否仍可用始终以应用主表为准，
 * 不能只信任已签发 JWT 的声明。</p>
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamTrustedApplicationAccessGuard {

    /**
     * OAuth2Authorization 属性名，持久化应用安全纪元快照。
     */
    public static final String AUTHORIZATION_ATTRIBUTE_APPLICATION_SECURITY_EPOCH =
            "iam.trusted-application-security-epoch";

    private static final String SQL_FIND_APPLICATION_ID_BY_REGISTERED_CLIENT_ID =
            "SELECT application_id FROM oauth2_registered_client WHERE id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final IamTrustedApplicationRepository trustedApplicationRepository;

    /**
     * 查询 OAuth 客户端绑定的可信应用；非可信应用客户端返回 null，保持 SAS 原有语义。
     */
    public IamTrustedApplicationEntity findApplication(String registeredClientId) {
        Long applicationId = findApplicationId(registeredClientId);
        return applicationId == null ? null : trustedApplicationRepository.findById(applicationId).orElse(null);
    }

    /**
     * 新授权请求只允许进入可用的可信应用；非可信应用客户端不受本门禁约束。
     */
    public boolean isNewAuthorizationAllowed(String registeredClientId) {
        Long applicationId = findApplicationId(registeredClientId);
        if (applicationId == null) {
            return true;
        }
        IamTrustedApplicationEntity application = trustedApplicationRepository.findById(applicationId).orElse(null);
        return application != null && isActive(application);
    }

    /**
     * 为可信应用授权写入签发时安全纪元；旧 SAS 客户端保持无此属性。
     */
    public OAuth2Authorization bindSecurityEpoch(OAuth2Authorization authorization) {
        Long applicationId = findApplicationId(authorization.getRegisteredClientId());
        if (applicationId == null) {
            return authorization;
        }
        IamTrustedApplicationEntity application = trustedApplicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            return authorization;
        }
        return OAuth2Authorization.from(authorization)
                .attribute(AUTHORIZATION_ATTRIBUTE_APPLICATION_SECURITY_EPOCH,
                        application.getApplicationSecurityEpoch())
                .build();
    }

    /**
     * 授权码、access token、refresh token 与 userinfo 的统一在线校验。
     */
    public boolean isAuthorizationAllowed(OAuth2Authorization authorization) {
        if (authorization == null) {
            return false;
        }
        Long applicationId = findApplicationId(authorization.getRegisteredClientId());
        if (applicationId == null) {
            return true;
        }
        IamTrustedApplicationEntity application = trustedApplicationRepository.findById(applicationId).orElse(null);
        if (application == null) {
            return false;
        }
        Long authorizationEpoch = authorization.getAttribute(AUTHORIZATION_ATTRIBUTE_APPLICATION_SECURITY_EPOCH);
        // 升级前已有授权没有 attribute，按文档约定解释为初始安全纪元 1。
        if (authorizationEpoch == null) {
            authorizationEpoch = 1L;
        }
        return isActive(application) && Objects.equals(application.getApplicationSecurityEpoch(), authorizationEpoch);
    }

    /**
     * 资源验证客户端的目标应用同样必须可用，不能只校验被验证 token 的来源应用。
     */
    public boolean isApplicationActive(Long applicationId) {
        if (applicationId == null) {
            return false;
        }
        return trustedApplicationRepository.findById(applicationId)
                .map(this::isActive)
                .orElse(false);
    }

    private Long findApplicationId(String registeredClientId) {
        if (registeredClientId == null) {
            return null;
        }
        List<Long> applicationIds = jdbcTemplate.query(SQL_FIND_APPLICATION_ID_BY_REGISTERED_CLIENT_ID,
                (resultSet, rowNum) -> resultSet.getObject("application_id", Long.class), registeredClientId);
        return applicationIds.isEmpty() ? null : applicationIds.get(0);
    }

    private boolean isActive(IamTrustedApplicationEntity application) {
        return application.getStatus() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == application.getStatus().intValue()
                && application.getApplicationSecurityEpoch() != null;
    }
}

package io.github.surezzzzzz.sdk.auth.authorization.application.core.model;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.support.ApplicationAuthorizationValidationHelper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * 已验证的应用授权上下文。
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
public final class ApplicationAuthorizationContext {

    /**
     * 授权协议名称。
     */
    private final String protocol;
    /**
     * 授权协议版本。
     */
    private final String version;
    /**
     * 主体类型。
     */
    private final ApplicationAuthorizationSubjectType subjectType;
    /**
     * Provider 内稳定主体标识。
     */
    private final String subjectId;
    /**
     * 目标应用标识。
     */
    private final String applicationCode;
    /**
     * 是否已通过应用准入。
     */
    private final boolean admitted;
    /**
     * 应用角色。
     */
    private final List<String> roles;
    /**
     * 页面权限。
     */
    private final List<String> pagePermissions;
    /**
     * 精确 API 权限。
     */
    private final List<String> apiPermissions;
    /**
     * 可选的数据授权文档。
     */
    private final DataGrantDocument dataGrantDocument;
    /**
     * 主体在应用下的授权版本。
     */
    private final long authorizationVersion;
    /**
     * 权限清单版本。
     */
    private final String manifestVersion;
    /**
     * 权限清单摘要。
     */
    private final String manifestDigest;
    /**
     * 签发时间。
     */
    private final Instant issuedAt;
    /**
     * 到期时间。
     */
    private final Instant expiresAt;

    /**
     * 创建已验证的应用授权上下文。
     *
     * @param protocol             授权协议名称
     * @param version              授权协议版本
     * @param subjectType          主体类型
     * @param subjectId            主体标识
     * @param applicationCode      应用标识
     * @param admitted             是否已准入
     * @param roles                应用角色
     * @param pagePermissions      页面权限
     * @param apiPermissions       API 权限
     * @param dataGrantDocument    数据授权文档
     * @param authorizationVersion 授权版本
     * @param manifestVersion      权限清单版本
     * @param manifestDigest       权限清单摘要
     * @param issuedAt             签发时间
     * @param expiresAt            到期时间
     */
    public ApplicationAuthorizationContext(String protocol, String version, ApplicationAuthorizationSubjectType subjectType,
                                           String subjectId, String applicationCode, boolean admitted,
                                           Collection<String> roles, Collection<String> pagePermissions,
                                           Collection<String> apiPermissions, DataGrantDocument dataGrantDocument,
                                           long authorizationVersion, String manifestVersion, String manifestDigest,
                                           Instant issuedAt, Instant expiresAt) {
        if (!SimpleApplicationAuthorizationConstant.PROTOCOL.equals(protocol)) {
            throw new io.github.surezzzzzz.sdk.auth.authorization.application.core.exception.ApplicationAuthorizationException(
                    ErrorCode.INVALID_PROTOCOL, String.format(ErrorMessage.INVALID_PROTOCOL, String.valueOf(protocol)));
        }
        if (!SimpleApplicationAuthorizationConstant.VERSION.equals(version)) {
            throw new io.github.surezzzzzz.sdk.auth.authorization.application.core.exception.ApplicationAuthorizationException(
                    ErrorCode.UNSUPPORTED_VERSION, String.format(ErrorMessage.UNSUPPORTED_VERSION, String.valueOf(version)));
        }
        if (subjectType == null) {
            throw ApplicationAuthorizationValidationHelper.invalidContext(String.format(
                    SimpleApplicationAuthorizationConstant.DETAIL_CANNOT_BE_NULL,
                    SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_TYPE));
        }
        if (!admitted) {
            throw ApplicationAuthorizationValidationHelper.invalidContext(
                    SimpleApplicationAuthorizationConstant.DETAIL_CONTEXT_MUST_BE_ADMITTED);
        }
        if (authorizationVersion <= 0L) {
            throw ApplicationAuthorizationValidationHelper.invalidContext(
                    SimpleApplicationAuthorizationConstant.DETAIL_AUTHORIZATION_VERSION_MUST_BE_POSITIVE);
        }
        if (issuedAt == null || expiresAt == null || !issuedAt.isBefore(expiresAt)) {
            throw ApplicationAuthorizationValidationHelper.invalidContext(
                    SimpleApplicationAuthorizationConstant.DETAIL_INVALID_TIME_WINDOW);
        }
        this.protocol = protocol;
        this.version = version;
        this.subjectType = subjectType;
        this.subjectId = ApplicationAuthorizationValidationHelper.requireIdentifier(subjectId,
                SimpleApplicationAuthorizationConstant.FIELD_SUBJECT_ID);
        this.applicationCode = ApplicationAuthorizationValidationHelper.requireApplicationCode(applicationCode);
        this.admitted = admitted;
        this.roles = ApplicationAuthorizationValidationHelper.normalizePermissions(roles,
                SimpleApplicationAuthorizationConstant.FIELD_ROLES);
        this.pagePermissions = ApplicationAuthorizationValidationHelper.normalizePermissions(pagePermissions,
                SimpleApplicationAuthorizationConstant.FIELD_PAGE_PERMISSIONS);
        this.apiPermissions = ApplicationAuthorizationValidationHelper.normalizePermissions(apiPermissions,
                SimpleApplicationAuthorizationConstant.FIELD_API_PERMISSIONS);
        this.dataGrantDocument = dataGrantDocument;
        this.authorizationVersion = authorizationVersion;
        this.manifestVersion = ApplicationAuthorizationValidationHelper.requireIdentifier(manifestVersion,
                SimpleApplicationAuthorizationConstant.FIELD_MANIFEST_VERSION);
        this.manifestDigest = ApplicationAuthorizationValidationHelper.requireManifestDigest(manifestDigest);
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }
}

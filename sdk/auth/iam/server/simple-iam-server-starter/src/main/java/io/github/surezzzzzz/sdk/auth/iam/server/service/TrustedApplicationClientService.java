package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.TrustedApplicationClientType;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.UpdateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationClientResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationClientSecretResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedirectUriHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 可信应用客户端管理服务（client 维度）
 *
 * <p>在指定应用下管理 OAuth2 客户端：clientType / PKCE / secret / grantType / redirectUri /
 * authenticationMethod 的校验与装配策略由本服务实现，客户端归属的应用维度由
 * {@code application_id} 列承载。
 *
 * <p>底层复用 SAS {@link RegisteredClientRepository}（{@code JdbcRegisteredClientRepository}）的
 * save/findByClientId；SAS 不感知 {@code application_id} 列，故创建 client 后由本服务用
 * {@link JdbcTemplate} 回填该列。删除和按应用列表查询也走 {@link JdbcTemplate}。
 *
 * <p>密钥安全：CONFIDENTIAL 客户端 secret 未传入时由服务端 SecureRandom 生成；
 * 明文 secret 仅在创建时返回一次，存储前经 {@link PasswordEncoder} 加密，
 * 列表 / 详情响应中只暴露 secret 是否存在，不回显明文。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class TrustedApplicationClientService {

    private static final String SQL_LIST_CLIENT_IDS_BY_APP =
            "SELECT client_id FROM oauth2_registered_client WHERE application_id = ? ORDER BY client_id_issued_at DESC";
    private static final String SQL_FIND_CLIENT_BELONG =
            "SELECT application_id FROM oauth2_registered_client WHERE client_id = ?";
    private static final String SQL_SET_APPLICATION_ID =
            "UPDATE oauth2_registered_client SET application_id = ? WHERE id = ?";
    private static final String SQL_COUNT_CLIENT_BY_CLIENT_ID =
            "SELECT COUNT(*) FROM oauth2_registered_client WHERE client_id = ?";
    private static final String SQL_DELETE_AUTHORIZATION =
            "DELETE FROM oauth2_authorization WHERE registered_client_id = ?";
    private static final String SQL_DELETE_AUTHORIZATION_CONSENT =
            "DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?";
    private static final String SQL_DELETE_IAM_CONSENT_BY_CLIENT =
            "DELETE FROM iam_consent WHERE client_id = ?";
    private static final String SQL_DELETE_REGISTERED_CLIENT =
            "DELETE FROM oauth2_registered_client WHERE id = ?";
    private static final int SECRET_BYTES = 32;

    private final RegisteredClientRepository registeredClientRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final RedirectUriHelper redirectUriHelper;
    private final IamAuditEventPublisher auditEventPublisher;
    private final SimpleIamServerProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 给已有应用添加 client
     *
     * @param applicationId 所属应用ID
     * @param request       创建请求（CONFIDENTIAL 的 secret 可不传，留空由服务端生成）
     * @return secret 明文响应（仅本次返回）
     */
    @Transactional
    public TrustedApplicationClientSecretResponse addClient(Long applicationId,
                                                            CreateTrustedApplicationClientRequest request) {
        requireApplication(applicationId);
        String clientId = normalizeRequired(request.getClientId(), "客户端ID不能为空");
        if (jdbcTemplate.queryForObject(SQL_COUNT_CLIENT_BY_CLIENT_ID, Integer.class, clientId) > 0) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_ID_EXISTS,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_ID_EXISTS, clientId));
        }
        String clientName = normalizeRequired(request.getClientName(), "客户端名称不能为空");
        List<String> redirectUris = redirectUriHelper.normalizeAndValidate(request.getRedirectUris());
        List<AuthorizationGrantType> grantTypes = resolveGrantTypes(request.getGrantTypes());
        if (redirectUris.isEmpty()) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_REDIRECT_URI_INVALID,
                    ServerErrorMessage.TRUSTED_APPLICATION_REDIRECT_URI_EMPTY);
        }
        TrustedApplicationClientType clientType = resolveClientType(request.getClientType());
        String rawSecret = normalizeClientSecret(request.getClientSecret());
        if (clientType == TrustedApplicationClientType.CONFIDENTIAL && !StringUtils.hasText(rawSecret)) {
            rawSecret = generateSecret();
        }
        List<ClientAuthenticationMethod> authenticationMethods = resolveAuthenticationMethods(
                request.getAuthenticationMethods(), clientType);
        validateClientPolicy(clientType, rawSecret, grantTypes, authenticationMethods);

        RegisteredClient saved = buildAndSaveClient(null, clientId, clientName, clientType, rawSecret,
                redirectUris, grantTypes, authenticationMethods, request.getScopes(),
                Boolean.TRUE.equals(request.getRequireConsent()));

        // SAS save 不感知 application_id 列，创建后回填
        jdbcTemplate.update(SQL_SET_APPLICATION_ID, applicationId, saved.getId());
        log.info("可信应用客户端创建成功：applicationId={}, clientId={}", applicationId, clientId);
        auditEventPublisher.publishAdminAction(AdminActionType.CREATED, AdminSubjectType.OAUTH_CLIENT,
                clientId, clientId, "applicationId=" + applicationId);
        return TrustedApplicationClientSecretResponse.builder()
                .id(saved.getId())
                .clientId(clientId)
                .clientSecret(rawSecret)
                .build();
    }

    /**
     * 应用下 client 列表
     */
    public List<TrustedApplicationClientResponse> listClients(Long applicationId) {
        requireApplication(applicationId);
        List<String> clientIds = jdbcTemplate.queryForList(SQL_LIST_CLIENT_IDS_BY_APP,
                String.class, applicationId);
        List<TrustedApplicationClientResponse> result = new ArrayList<>();
        for (String clientId : clientIds) {
            RegisteredClient client = registeredClientRepository.findByClientId(clientId);
            if (client != null) {
                result.add(toResponse(client));
            }
        }
        return result;
    }

    /**
     * 应用下 client 详情
     */
    public TrustedApplicationClientResponse getClient(Long applicationId, String clientId) {
        RegisteredClient client = requireClientBelongingToApp(applicationId, clientId);
        return toResponse(client);
    }

    /**
     * 更新 client 可维护字段（clientId / clientType 不可变）
     */
    @Transactional
    public TrustedApplicationClientResponse updateClient(Long applicationId, String clientId,
                                                         UpdateTrustedApplicationClientRequest request) {
        RegisteredClient existing = requireClientBelongingToApp(applicationId, clientId);
        List<String> redirectUris = redirectUriHelper.normalizeAndValidate(request.getRedirectUris());
        List<AuthorizationGrantType> grantTypes = Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE);
        if (redirectUris.isEmpty()) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_REDIRECT_URI_INVALID,
                    ServerErrorMessage.TRUSTED_APPLICATION_REDIRECT_URI_EMPTY);
        }
        String clientName = StringUtils.hasText(request.getClientName())
                ? request.getClientName().trim() : existing.getClientName();
        TrustedApplicationClientType clientType = resolveClientType(existing);
        List<ClientAuthenticationMethod> authenticationMethods = Collections.singletonList(
                clientType == TrustedApplicationClientType.PUBLIC
                        ? ClientAuthenticationMethod.NONE : ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        validateClientPolicy(clientType, existing.getClientSecret(), grantTypes, authenticationMethods);

        RegisteredClient updated = buildAndSaveClient(existing, existing.getClientId(), clientName, clientType,
                existing.getClientSecret(), redirectUris, grantTypes, authenticationMethods,
                request.getScopes(),
                request.getRequireConsent() == null
                        ? existing.getClientSettings().isRequireAuthorizationConsent()
                        : request.getRequireConsent());

        log.info("可信应用客户端更新成功：applicationId={}, clientId={}", applicationId, clientId);
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.OAUTH_CLIENT,
                clientId, clientId, "applicationId=" + applicationId);
        return toResponse(updated);
    }

    /**
     * 删除 client（清理授权记录 / consent 投影 / client 本身）
     */
    @Transactional
    public void deleteClient(Long applicationId, String clientId) {
        RegisteredClient client = requireClientBelongingToApp(applicationId, clientId);
        jdbcTemplate.update(SQL_DELETE_AUTHORIZATION, client.getId());
        jdbcTemplate.update(SQL_DELETE_AUTHORIZATION_CONSENT, client.getId());
        jdbcTemplate.update(SQL_DELETE_IAM_CONSENT_BY_CLIENT, clientId);
        jdbcTemplate.update(SQL_DELETE_REGISTERED_CLIENT, client.getId());
        log.info("可信应用客户端删除成功：applicationId={}, clientId={}", applicationId, clientId);
        auditEventPublisher.publishAdminAction(AdminActionType.DELETED, AdminSubjectType.OAUTH_CLIENT,
                clientId, clientId, "applicationId=" + applicationId);
    }

    /**
     * 校验 client 存在且属于指定应用，返回 RegisteredClient。
     *
     * <p>client 不存在 -> {@link ErrorCode#TRUSTED_APPLICATION_NOT_FOUND}；
     * client 存在但 application_id 不匹配 -> {@link ErrorCode#TRUSTED_APPLICATION_CLIENT_NOT_BELONG}。
     */
    private void requireApplication(Long applicationId) {
        if (!trustedApplicationRepository.existsById(applicationId)) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND_BY_ID, applicationId));
        }
    }

    private RegisteredClient requireClientBelongingToApp(Long applicationId, String clientId) {
        List<Long> appIds = jdbcTemplate.queryForList(SQL_FIND_CLIENT_BELONG,
                Long.class, clientId);
        if (appIds.isEmpty()) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND, clientId));
        }
        Long belongAppId = appIds.get(0);
        if (belongAppId == null || !belongAppId.equals(applicationId)) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_CLIENT_NOT_BELONG,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_CLIENT_NOT_BELONG, clientId, applicationId));
        }
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND, clientId));
        }
        return client;
    }

    /**
     * 构建 RegisteredClient 并保存。
     *
     * <p>{@code existing} 非空为更新（保留 id/签发时间），为空为新建。
     * tokenSettings 始终按当前配置重建：TTL 与 refresh token 轮换是服务端
     * 统一策略（{@code reuseRefreshTokens=false}，refresh 一次性使用），
     * 非 per-client 定制项。
     */
    private RegisteredClient buildAndSaveClient(RegisteredClient existing, String clientId, String clientName,
                                                TrustedApplicationClientType clientType, String rawOrHashedSecret,
                                                List<String> redirectUris, List<AuthorizationGrantType> grantTypes,
                                                List<ClientAuthenticationMethod> authenticationMethods,
                                                List<String> scopes, boolean requireConsent) {
        RegisteredClient.Builder builder;
        if (existing == null) {
            builder = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .clientIdIssuedAt(Instant.now())
                    .clientName(clientName);
            if (clientType == TrustedApplicationClientType.CONFIDENTIAL) {
                builder.clientSecret(passwordEncoder.encode(rawOrHashedSecret));
            }
        } else {
            builder = RegisteredClient.withId(existing.getId())
                    .clientId(existing.getClientId())
                    .clientIdIssuedAt(existing.getClientIdIssuedAt())
                    .clientName(clientName);
            if (clientType == TrustedApplicationClientType.CONFIDENTIAL) {
                builder.clientSecret(existing.getClientSecret());
            }
        }
        for (String redirectUri : redirectUris) {
            builder.redirectUri(redirectUri);
        }
        for (String scope : resolveScopes(scopes)) {
            builder.scope(scope);
        }
        for (AuthorizationGrantType grantType : grantTypes) {
            builder.authorizationGrantType(grantType);
        }
        for (ClientAuthenticationMethod method : authenticationMethods) {
            builder.clientAuthenticationMethod(method);
        }
        builder.clientSettings(ClientSettings.builder()
                .requireProofKey(clientType == TrustedApplicationClientType.PUBLIC)
                .requireAuthorizationConsent(requireConsent)
                .build());
        builder.tokenSettings(TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofSeconds(properties.getToken().getAccessExpiresIn()))
                .refreshTokenTimeToLive(Duration.ofSeconds(properties.getToken().getRefreshExpiresIn()))
                .reuseRefreshTokens(false)
                .build());
        RegisteredClient saved = builder.build();
        registeredClientRepository.save(saved);
        return registeredClientRepository.findByClientId(clientId);
    }

    private TrustedApplicationClientResponse toResponse(RegisteredClient client) {
        return TrustedApplicationClientResponse.builder()
                .id(client.getId())
                .clientId(client.getClientId())
                .clientName(client.getClientName())
                .clientType(resolveClientType(client).getCode())
                .requireConsent(client.getClientSettings().isRequireAuthorizationConsent())
                .requireProofKey(client.getClientSettings().isRequireProofKey())
                .redirectUris(new ArrayList<>(client.getRedirectUris()))
                .scopes(new ArrayList<>(client.getScopes()))
                .grantTypes(client.getAuthorizationGrantTypes().stream()
                        .map(AuthorizationGrantType::getValue).collect(Collectors.toList()))
                .authenticationMethods(client.getClientAuthenticationMethods().stream()
                        .map(ClientAuthenticationMethod::getValue).collect(Collectors.toList()))
                .clientIdIssuedAt(client.getClientIdIssuedAt())
                .secretPresent(client.getClientSecret() != null)
                .build();
    }

    private List<AuthorizationGrantType> resolveGrantTypes(List<String> grantTypes) {
        if (grantTypes == null || grantTypes.isEmpty()) {
            return Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE);
        }
        List<AuthorizationGrantType> result = new ArrayList<>();
        for (String raw : grantTypes) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String value = raw.trim();
            // refresh_token 是授权码流程签发 refresh token 的必备伴生 grant，放行；
            // client_credentials 等机器凭证类型仍拒绝（走 aksk-server 签发 AK/SK）
            if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(value)) {
                result.add(new AuthorizationGrantType(value));
                continue;
            }
            if (!AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(value)) {
                throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_GRANT_TYPE_NOT_ALLOWED,
                        ServerErrorMessage.TRUSTED_APPLICATION_GRANT_TYPE_NOT_ALLOWED);
            }
            result.add(new AuthorizationGrantType(value));
        }
        if (result.isEmpty()) {
            return Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE);
        }
        return result;
    }

    private List<String> resolveScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return Collections.emptyList();
        }
        return scopes.stream().filter(StringUtils::hasText).map(String::trim)
                .distinct().collect(Collectors.toList());
    }

    private TrustedApplicationClientType resolveClientType(String rawClientType) {
        if (!StringUtils.hasText(rawClientType)) {
            return TrustedApplicationClientType.CONFIDENTIAL;
        }
        TrustedApplicationClientType clientType = TrustedApplicationClientType.fromCode(rawClientType.trim());
        if (clientType == null) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_CLIENT_POLICY_INVALID,
                    ServerErrorMessage.TRUSTED_APPLICATION_CLIENT_TYPE_INVALID);
        }
        return clientType;
    }

    private TrustedApplicationClientType resolveClientType(RegisteredClient client) {
        return client.getClientSecret() == null
                ? TrustedApplicationClientType.PUBLIC
                : TrustedApplicationClientType.CONFIDENTIAL;
    }

    private String normalizeClientSecret(String rawSecret) {
        return StringUtils.hasText(rawSecret) ? rawSecret.trim() : null;
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private List<ClientAuthenticationMethod> resolveAuthenticationMethods(List<String> methods,
                                                                          TrustedApplicationClientType clientType) {
        if (methods == null || methods.isEmpty()) {
            return Collections.singletonList(clientType == TrustedApplicationClientType.PUBLIC
                    ? ClientAuthenticationMethod.NONE
                    : ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        }
        List<ClientAuthenticationMethod> result = new ArrayList<>();
        for (String raw : methods) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            result.add(new ClientAuthenticationMethod(raw.trim()));
        }
        return result.isEmpty()
                ? Collections.singletonList(clientType == TrustedApplicationClientType.PUBLIC
                ? ClientAuthenticationMethod.NONE
                : ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                : result;
    }

    private void validateClientPolicy(TrustedApplicationClientType clientType, String rawSecret,
                                      List<AuthorizationGrantType> grantTypes,
                                      List<ClientAuthenticationMethod> authenticationMethods) {
        // 白名单（authorization_code + 伴生 refresh_token）已在 resolveGrantTypes 收窄，
        // 此处只需保证授权码流程核心 grant 在场
        if (!grantTypes.contains(AuthorizationGrantType.AUTHORIZATION_CODE)) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_GRANT_TYPE_NOT_ALLOWED,
                    ServerErrorMessage.TRUSTED_APPLICATION_GRANT_TYPE_NOT_ALLOWED);
        }
        if (clientType == TrustedApplicationClientType.PUBLIC) {
            if (StringUtils.hasText(rawSecret)) {
                throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_CLIENT_POLICY_INVALID,
                        ServerErrorMessage.TRUSTED_APPLICATION_PUBLIC_CLIENT_SECRET_FORBIDDEN);
            }
            if (authenticationMethods.size() != 1
                    || !ClientAuthenticationMethod.NONE.equals(authenticationMethods.get(0))) {
                throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_CLIENT_POLICY_INVALID,
                        ServerErrorMessage.TRUSTED_APPLICATION_PUBLIC_CLIENT_AUTH_METHOD_INVALID);
            }
            return;
        }
        // CONFIDENTIAL 空密钥已在调用前生成兜底，此处密钥必非空
        // SAS 默认客户端认证链（ClientSecretAuthenticationProvider）同时支持 basic 与 post 两种密钥提交方式
        if (authenticationMethods.size() != 1
                || !(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.equals(authenticationMethods.get(0))
                || ClientAuthenticationMethod.CLIENT_SECRET_POST.equals(authenticationMethods.get(0)))) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_CLIENT_POLICY_INVALID,
                    ServerErrorMessage.TRUSTED_APPLICATION_CONFIDENTIAL_CLIENT_AUTH_METHOD_INVALID);
        }
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new SimpleIamServerException(message);
        }
        return value.trim();
    }
}

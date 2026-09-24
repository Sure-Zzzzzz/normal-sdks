package io.github.surezzzzzz.sdk.auth.iam.server.service.bootstrap;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 初始化 IAM 内部 AKSK reader SERVICE。
 *
 * <p>该主体不关联可信应用，避免首次安装时依赖 AKSK 应用已登记而形成循环。
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamInternalReaderBootstrap implements ApplicationRunner {

    public static final String READER_CLIENT_ID = "aksk-owner-authorization-reader";
    public static final String READER_SUBJECT = "iam-internal:aksk-owner-authorization-reader";
    public static final String READER_TOKEN_USE = "internal_service";
    public static final String READER_SCOPE = "iam:internal:aksk-owner-authorization:read";
    public static final String STREAM_SCOPE = "iam:internal:aksk-owner-authorization:stream";

    private static final String SQL_DELETE_AUTHORIZATION =
            "DELETE FROM oauth2_authorization WHERE registered_client_id = ?";
    private static final String SQL_DELETE_CONSENT =
            "DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?";
    private static final String SQL_DELETE_REGISTERED_CLIENT =
            "DELETE FROM oauth2_registered_client WHERE id = ?";

    private final SimpleIamServerProperties properties;
    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        SimpleIamServerProperties.InternalReaderConfig reader = properties.getInternalReader();
        if (!reader.isEnabled()) {
            log.info("AKSK 所属人授权 reader 未启用：内部 API 保持拒绝状态");
            return;
        }
        if (!StringUtils.hasText(reader.getClientSecret())) {
            throw new SimpleIamServerException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    "AKSK 所属人授权 reader 已启用但缺少部署注入密钥");
        }
        RegisteredClient existing = registeredClientRepository.findByClientId(READER_CLIENT_ID);
        // SAS 0.4.x 对已有 client 的 save 不更新 client_secret；固定 reader 可受控重建，保证轮换生效。
        removeExistingReader(existing);
        RegisteredClient.Builder builder = existing == null
                ? RegisteredClient.withId(UUID.randomUUID().toString())
                : RegisteredClient.from(existing);
        RegisteredClient registeredClient = builder
                .clientId(READER_CLIENT_ID)
                .clientIdIssuedAt(existing == null ? Instant.now() : existing.getClientIdIssuedAt())
                .clientSecret(passwordEncoder.encode(reader.getClientSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(READER_SCOPE)
                .scope(STREAM_SCOPE)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(reader.getAccessExpiresIn()))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
        registeredClientRepository.save(registeredClient);
        log.info("AKSK 所属人授权 reader SERVICE 已同步部署凭据：clientId={}", READER_CLIENT_ID);
    }

    /**
     * 删除 reader 的短期服务授权和注册记录，为重插新 secret hash 让出唯一键。
     */
    private void removeExistingReader(RegisteredClient existing) {
        if (existing == null) {
            return;
        }
        jdbcTemplate.update(SQL_DELETE_AUTHORIZATION, existing.getId());
        jdbcTemplate.update(SQL_DELETE_CONSENT, existing.getId());
        jdbcTemplate.update(SQL_DELETE_REGISTERED_CLIENT, existing.getId());
    }
}

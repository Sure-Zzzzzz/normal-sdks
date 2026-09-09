package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.request.CreateResourceVerificationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.response.ResourceVerificationClientResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.response.ResourceVerificationClientSecretResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamResourceVerificationClientEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamResourceVerificationClientRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamTrustedApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * IAM 资源令牌验证客户端服务（认证校验 + 管理面）。
 *
 * <p>机器凭据由服务端生成：secret 使用 SecureRandom 生成、明文仅在创建 / 轮换响应中
 * 返回一次，存储只保留 PasswordEncoder 哈希。撤销为语义撤销（status 置为非活跃并记录
 * revokedAt），记录保留供审计，不提供物理删除。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamResourceVerificationClientService {

    /**
     * secret 随机字节数（Base64URL 编码后约 43 字符）
     */
    private static final int SECRET_BYTES = 32;

    /**
     * clientId 最大长度（与实体列约束一致）
     */
    private static final int CLIENT_ID_MAX_LENGTH = 100;

    private final IamResourceVerificationClientRepository clientRepository;
    private final IamAuditEventPublisher auditEventPublisher;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 校验独立资源验证客户端的 Basic 凭据。
     */
    public IamResourceVerificationClientEntity authenticate(String clientId, String clientSecret) {
        if (clientId == null || clientId.trim().isEmpty() || clientSecret == null) {
            return null;
        }
        IamResourceVerificationClientEntity client = clientRepository
                .findByClientId(clientId).orElse(null);
        if (client == null || client.getStatus() == null
                || SimpleIamServerConstant.STATUS_ACTIVE != client.getStatus().intValue()) {
            return null;
        }
        return passwordEncoder.matches(clientSecret, client.getClientSecretHash()) ? client : null;
    }

    /**
     * 在指定可信应用下创建资源验证客户端；secret 服务端生成并在响应中一次性返回。
     *
     * @param applicationId 可信应用ID
     * @param request       创建请求（仅 clientId）
     * @return 含明文 secret 的一次性响应
     */
    @Transactional
    public ResourceVerificationClientSecretResponse createClient(Long applicationId,
                                                                 CreateResourceVerificationClientRequest request) {
        requireApplication(applicationId);
        String clientId = normalizeClientId(request.getClientId());
        if (clientRepository.findByClientId(clientId).isPresent()) {
            throw clientExists(clientId);
        }
        String rawSecret = generateSecret();
        IamResourceVerificationClientEntity entity = new IamResourceVerificationClientEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setApplicationId(applicationId);
        entity.setClientId(clientId);
        entity.setClientSecretHash(passwordEncoder.encode(rawSecret));
        entity.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            clientRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw clientExists(clientId);
        }
        log.info("资源验证客户端创建成功：applicationId={}, clientId={}", applicationId, clientId);
        auditEventPublisher.publishAdminAction(AdminActionType.CREATED, AdminSubjectType.VERIFICATION_CLIENT,
                clientId, clientId, "applicationId=" + applicationId);
        return ResourceVerificationClientSecretResponse.builder()
                .clientId(clientId)
                .clientSecret(rawSecret)
                .build();
    }

    /**
     * 列出指定可信应用下全部资源验证客户端摘要（不回显任何密钥形态）。
     *
     * @param applicationId 可信应用ID
     * @return 客户端摘要列表
     */
    public List<ResourceVerificationClientResponse> listClients(Long applicationId) {
        requireApplication(applicationId);
        return clientRepository.findByApplicationId(applicationId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 查询单个资源验证客户端详情（不回显任何密钥形态）。
     *
     * @param applicationId 可信应用ID
     * @param clientId      客户端ID
     * @return 客户端详情
     */
    public ResourceVerificationClientResponse getClient(Long applicationId, String clientId) {
        return toResponse(requireClientBelongingToApplication(applicationId, clientId));
    }

    /**
     * 轮换资源验证客户端 secret：服务端生成新 secret 整体替换旧值，旧 secret 立即失效。
     *
     * @param applicationId 可信应用ID
     * @param clientId      客户端ID
     * @return 含新明文 secret 的一次性响应
     */
    @Transactional
    public ResourceVerificationClientSecretResponse rotateSecret(Long applicationId, String clientId) {
        IamResourceVerificationClientEntity client = requireClientBelongingToApplication(applicationId, clientId);
        if (client.getStatus() == null
                || SimpleIamServerConstant.STATUS_ACTIVE != client.getStatus().intValue()) {
            throw new SimpleIamServerException(ErrorCode.RESOURCE_VERIFICATION_CLIENT_REVOKED,
                    String.format(ServerErrorMessage.RESOURCE_VERIFICATION_CLIENT_REVOKED, clientId));
        }
        String rawSecret = generateSecret();
        client.setClientSecretHash(passwordEncoder.encode(rawSecret));
        client.setUpdatedAt(Instant.now());
        clientRepository.saveAndFlush(client);
        log.info("资源验证客户端密钥轮换成功：applicationId={}, clientId={}", applicationId, clientId);
        auditEventPublisher.publishAdminAction(AdminActionType.SECRET_ROTATED, AdminSubjectType.VERIFICATION_CLIENT,
                clientId, clientId, "applicationId=" + applicationId);
        return ResourceVerificationClientSecretResponse.builder()
                .clientId(clientId)
                .clientSecret(rawSecret)
                .build();
    }

    /**
     * 撤销资源验证客户端：置为非活跃并记录撤销时间，认证立即失效；重复撤销幂等。
     *
     * @param applicationId 可信应用ID
     * @param clientId      客户端ID
     */
    @Transactional
    public void revokeClient(Long applicationId, String clientId) {
        IamResourceVerificationClientEntity client = requireClientBelongingToApplication(applicationId, clientId);
        if (client.getStatus() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == client.getStatus().intValue()) {
            client.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
            client.setRevokedAt(Instant.now());
            client.setUpdatedAt(Instant.now());
            clientRepository.saveAndFlush(client);
            log.info("资源验证客户端撤销成功：applicationId={}, clientId={}", applicationId, clientId);
            auditEventPublisher.publishAdminAction(AdminActionType.REVOKED, AdminSubjectType.VERIFICATION_CLIENT,
                    clientId, clientId, "applicationId=" + applicationId);
        }
    }

    private void requireApplication(Long applicationId) {
        if (!trustedApplicationRepository.existsById(applicationId)) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND_BY_ID, applicationId));
        }
    }

    private IamResourceVerificationClientEntity requireClientBelongingToApplication(
            Long applicationId, String clientId) {
        IamResourceVerificationClientEntity client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new SimpleIamServerException(
                        ErrorCode.RESOURCE_VERIFICATION_CLIENT_NOT_FOUND,
                        String.format(ServerErrorMessage.RESOURCE_VERIFICATION_CLIENT_NOT_FOUND, clientId)));
        if (applicationId == null
                || !applicationId.equals(client.getApplicationId())) {
            throw new SimpleIamServerException(ErrorCode.RESOURCE_VERIFICATION_CLIENT_NOT_BELONG,
                    String.format(ServerErrorMessage.RESOURCE_VERIFICATION_CLIENT_NOT_BELONG,
                            clientId, applicationId));
        }
        return client;
    }

    private String normalizeClientId(String clientId) {
        String normalized = clientId == null ? null : clientId.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > CLIENT_ID_MAX_LENGTH
                || normalized.indexOf(':') >= 0) {
            throw new SimpleIamServerException(ErrorCode.VALIDATION_FAILED,
                    ServerErrorMessage.RESOURCE_VERIFICATION_CLIENT_ID_INVALID);
        }
        return normalized;
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private SimpleIamServerException clientExists(String clientId) {
        return new SimpleIamServerException(ErrorCode.RESOURCE_VERIFICATION_CLIENT_ID_EXISTS,
                String.format(ServerErrorMessage.RESOURCE_VERIFICATION_CLIENT_ID_EXISTS, clientId));
    }

    private ResourceVerificationClientResponse toResponse(IamResourceVerificationClientEntity entity) {
        return ResourceVerificationClientResponse.builder()
                .clientId(entity.getClientId())
                .applicationId(entity.getApplicationId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .revokedAt(entity.getRevokedAt())
                .build();
    }
}

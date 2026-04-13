package io.github.surezzzzzz.sdk.auth.aksk.server.service.impl;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.support.Base62Helper;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ClientException;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.OAuth2SettingsHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Client Management Service Implementation
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class ClientManagementServiceImpl implements ClientManagementService {

    private final OAuth2RegisteredClientEntityRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final SimpleAkskServerProperties properties;

    @Override
    public ClientInfoResponse createPlatformClient(String clientName) {
        return createPlatformClient(clientName, null);
    }

    @Override
    public ClientInfoResponse createPlatformClient(String clientName, List<String> scopes) {
        String clientId = generatePlatformClientId();
        String clientSecret = generateClientSecret();

        if (clientRepository.existsByClientId(clientId)) {
            throw new ClientException(
                    ErrorCode.CLIENT_ALREADY_EXISTS,
                    String.format(ErrorMessage.CLIENT_ALREADY_EXISTS, clientId)
            );
        }

        String scopesStr = (scopes == null || scopes.isEmpty())
                ? SimpleAkskServerConstant.DEFAULT_SCOPES
                : String.join(SimpleAkskServerConstant.SCOPE_DELIMITER, scopes);

        String encodedSecret = passwordEncoder.encode(clientSecret);

        OAuth2RegisteredClientEntity entity = new OAuth2RegisteredClientEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setClientId(clientId);
        entity.setClientIdIssuedAt(Instant.now());
        entity.setClientSecret(encodedSecret);
        entity.setClientName(clientName);
        entity.setClientType(ClientType.PLATFORM.getCode());
        entity.setClientAuthenticationMethods(SimpleAkskServerConstant.CLIENT_AUTHENTICATION_METHOD);
        entity.setAuthorizationGrantTypes(SimpleAkskServerConstant.AUTHORIZATION_GRANT_TYPE);
        entity.setScopes(scopesStr);
        entity.setClientSettings(OAuth2SettingsHelper.getDefaultClientSettingsJson());
        entity.setTokenSettings(OAuth2SettingsHelper.getTokenSettingsJson(properties.getJwt().getExpiresIn()));

        try {
            clientRepository.save(entity);
            log.info("Created platform client: {}", clientId);

            // Return ClientInfoResponse with plain text secret (not from database)
            ClientInfoResponse clientInfo = new ClientInfoResponse();
            clientInfo.setClientId(clientId);
            clientInfo.setClientSecret(clientSecret);
            clientInfo.setClientName(clientName);
            clientInfo.setClientType(ClientType.PLATFORM.getCode());
            clientInfo.setEnabled(true);
            clientInfo.setScopes(Arrays.asList(scopesStr.split(SimpleAkskServerConstant.SCOPE_DELIMITER)));

            return clientInfo;
        } catch (Exception e) {
            log.error("Failed to create platform client", e);
            throw new ClientException(
                    ErrorCode.CLIENT_CREATE_FAILED,
                    String.format(ErrorMessage.CLIENT_CREATE_FAILED, e.getMessage()),
                    e
            );
        }
    }

    @Override
    public ClientInfoResponse createUserClient(String ownerUserId, String ownerUsername, String clientName) {
        return createUserClient(ownerUserId, ownerUsername, clientName, null);
    }

    @Override
    public ClientInfoResponse createUserClient(String ownerUserId, String ownerUsername, String clientName, List<String> scopes) {
        String clientId = generateUserClientId(ownerUserId);
        String clientSecret = generateClientSecret();

        if (clientRepository.existsByClientId(clientId)) {
            throw new ClientException(
                    ErrorCode.CLIENT_ALREADY_EXISTS,
                    String.format(ErrorMessage.CLIENT_ALREADY_EXISTS, clientId)
            );
        }

        String scopesStr = (scopes == null || scopes.isEmpty())
                ? SimpleAkskServerConstant.DEFAULT_SCOPES
                : String.join(SimpleAkskServerConstant.SCOPE_DELIMITER, scopes);

        OAuth2RegisteredClientEntity entity = new OAuth2RegisteredClientEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setClientId(clientId);
        entity.setClientIdIssuedAt(Instant.now());
        entity.setClientSecret(passwordEncoder.encode(clientSecret));
        entity.setClientName(clientName);
        entity.setClientType(ClientType.USER.getCode());
        entity.setOwnerUserId(ownerUserId);
        entity.setOwnerUsername(ownerUsername);
        entity.setClientAuthenticationMethods(SimpleAkskServerConstant.CLIENT_AUTHENTICATION_METHOD);
        entity.setAuthorizationGrantTypes(SimpleAkskServerConstant.AUTHORIZATION_GRANT_TYPE);
        entity.setScopes(scopesStr);
        entity.setClientSettings(OAuth2SettingsHelper.getDefaultClientSettingsJson());
        entity.setTokenSettings(OAuth2SettingsHelper.getTokenSettingsJson(properties.getJwt().getExpiresIn()));

        try {
            clientRepository.save(entity);
            log.info("Created user client: {} for user: {}", clientId, ownerUserId);

            // Return ClientInfoResponse with plain text secret (not from database)
            ClientInfoResponse clientInfo = new ClientInfoResponse();
            clientInfo.setClientId(clientId);
            clientInfo.setClientSecret(clientSecret);
            clientInfo.setClientName(clientName);
            clientInfo.setClientType(ClientType.USER.getCode());
            clientInfo.setOwnerUserId(ownerUserId);
            clientInfo.setOwnerUsername(ownerUsername);
            clientInfo.setEnabled(true);
            clientInfo.setScopes(Arrays.asList(scopesStr.split(SimpleAkskServerConstant.SCOPE_DELIMITER)));

            return clientInfo;
        } catch (Exception e) {
            log.error("Failed to create user client", e);
            throw new ClientException(
                    ErrorCode.CLIENT_CREATE_FAILED,
                    String.format(ErrorMessage.CLIENT_CREATE_FAILED, e.getMessage()),
                    e
            );
        }
    }

    @Override
    public void deleteClient(String clientId) {
        OAuth2RegisteredClientEntity entity = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientException(
                        ErrorCode.CLIENT_NOT_FOUND,
                        String.format(ErrorMessage.CLIENT_NOT_FOUND, clientId)
                ));

        try {
            clientRepository.delete(entity);
            log.info("Deleted client: {}", clientId);
        } catch (Exception e) {
            log.error("Failed to delete client: {}", clientId, e);
            throw new ClientException(
                    ErrorCode.CLIENT_DELETE_FAILED,
                    String.format(ErrorMessage.CLIENT_DELETE_FAILED, e.getMessage()),
                    e
            );
        }
    }

    @Override
    public ClientInfoResponse getClientById(String clientId) {
        OAuth2RegisteredClientEntity entity = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientException(
                        ErrorCode.CLIENT_NOT_FOUND,
                        String.format(ErrorMessage.CLIENT_NOT_FOUND, clientId)
                ));

        return toClientInfoResponse(entity);
    }

    @Override
    public Map<String, ClientInfoResponse> batchGetClientsByIds(List<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return new HashMap<>();
        }

        // 使用IN查询,一次SQL查询所有记录
        List<OAuth2RegisteredClientEntity> entities = clientRepository.findAllByClientIdIn(clientIds);

        // 直接转换为Map: clientId -> ClientInfoResponse
        return entities.stream()
                .map(this::toClientInfoResponse)
                .collect(Collectors.toMap(
                        ClientInfoResponse::getClientId,
                        Function.identity()
                ));
    }

    @Override
    public PageResponse<ClientInfoResponse> listClients(String ownerUserId, String type, Integer page, Integer size) {
        // 创建分页参数 (Spring Data JPA的页码从0开始，所以需要-1)
        int currentPage = Math.max(1, page);
        int pageSize = Math.max(1, size);
        Sort sort = Sort.by(Sort.Direction.DESC, "clientIdIssuedAt");
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize, sort);

        Page<OAuth2RegisteredClientEntity> entityPage;

        if (ownerUserId != null && !ownerUserId.trim().isEmpty()) {
            // 按用户ID查询（数据库分页）
            entityPage = clientRepository.findByOwnerUserId(ownerUserId, pageable);
        } else if (type != null && !type.trim().isEmpty()) {
            // 按类型查询（数据库分页）
            ClientType clientType = ClientType.PLATFORM.getValue().equalsIgnoreCase(type)
                    ? ClientType.PLATFORM
                    : ClientType.USER;
            entityPage = clientRepository.findByClientType(clientType.getCode(), pageable);
        } else {
            // 查询全部（数据库分页）
            entityPage = clientRepository.findAll(pageable);
        }

        // 转换为Response
        List<ClientInfoResponse> clientList = entityPage.getContent().stream()
                .map(this::toClientInfoResponse)
                .collect(Collectors.toList());

        // 返回PageResponse
        return PageResponse.of(clientList, entityPage.getTotalElements(), currentPage, pageSize);
    }

    @Override
    public String regenerateSecretKey(String clientId) {
        OAuth2RegisteredClientEntity entity = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientException(
                        ErrorCode.CLIENT_NOT_FOUND,
                        String.format(ErrorMessage.CLIENT_NOT_FOUND, clientId)
                ));

        String newSecret = generateClientSecret();
        String encodedSecret = passwordEncoder.encode(newSecret);
        entity.setClientSecret(encodedSecret);

        try {
            clientRepository.save(entity);
            log.info("Regenerated secret for client: {}", clientId);
            return newSecret;
        } catch (Exception e) {
            log.error("Failed to regenerate secret for client: {}", clientId, e);
            throw new ClientException(
                    ErrorCode.CLIENT_CREATE_FAILED,
                    String.format(ErrorMessage.CLIENT_CREATE_FAILED, e.getMessage()),
                    e
            );
        }
    }

    @Override
    public int syncUserScopes(String ownerUserId, List<String> scopes) {
        log.info("Syncing scopes for user: {}, new scopes: {}", ownerUserId, scopes);

        List<OAuth2RegisteredClientEntity> userClients = clientRepository
                .findByOwnerUserIdAndClientType(ownerUserId, ClientType.USER.getCode());

        if (userClients.isEmpty()) {
            log.info("No user-level clients found for user: {}", ownerUserId);
            return 0;
        }

        String scopesStr = String.join(SimpleAkskServerConstant.SCOPE_DELIMITER, scopes);
        int updatedCount = 0;

        for (OAuth2RegisteredClientEntity entity : userClients) {
            entity.setScopes(scopesStr);
            try {
                clientRepository.save(entity);
                updatedCount++;
            } catch (Exception e) {
                log.error("Failed to update scopes for client: {}", entity.getClientId(), e);
            }
        }

        log.info("Successfully synced scopes for {} clients of user: {}", updatedCount, ownerUserId);
        return updatedCount;
    }

    @Override
    public void disableClient(String clientId) {
        OAuth2RegisteredClientEntity entity = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientException(
                        ErrorCode.CLIENT_NOT_FOUND,
                        String.format(ErrorMessage.CLIENT_NOT_FOUND, clientId)
                ));

        entity.setEnabled(false);

        try {
            clientRepository.save(entity);
            log.info("Disabled client: {}", clientId);
        } catch (Exception e) {
            log.error("Failed to disable client: {}", clientId, e);
            throw new ClientException(
                    ErrorCode.CLIENT_UPDATE_FAILED,
                    String.format(ErrorMessage.CLIENT_UPDATE_FAILED, e.getMessage()),
                    e
            );
        }
    }

    @Override
    public void enableClient(String clientId) {
        OAuth2RegisteredClientEntity entity = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientException(
                        ErrorCode.CLIENT_NOT_FOUND,
                        String.format(ErrorMessage.CLIENT_NOT_FOUND, clientId)
                ));

        entity.setEnabled(true);

        try {
            clientRepository.save(entity);
            log.info("Enabled client: {}", clientId);
        } catch (Exception e) {
            log.error("Failed to enable client: {}", clientId, e);
            throw new ClientException(
                    ErrorCode.CLIENT_UPDATE_FAILED,
                    String.format(ErrorMessage.CLIENT_UPDATE_FAILED, e.getMessage()),
                    e
            );
        }
    }

    @Override
    public void updateClientScopes(String clientId, List<String> scopes) {
        OAuth2RegisteredClientEntity entity = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientException(
                        ErrorCode.CLIENT_NOT_FOUND,
                        String.format(ErrorMessage.CLIENT_NOT_FOUND, clientId)
                ));

        String scopesStr = String.join(SimpleAkskServerConstant.SCOPE_DELIMITER, scopes);
        entity.setScopes(scopesStr);

        try {
            clientRepository.save(entity);
            log.info("Updated scopes for client: {}, new scopes: {}", clientId, scopesStr);
        } catch (Exception e) {
            log.error("Failed to update scopes for client: {}", clientId, e);
            throw new ClientException(
                    ErrorCode.CLIENT_UPDATE_FAILED,
                    String.format(ErrorMessage.CLIENT_UPDATE_FAILED, e.getMessage()),
                    e
            );
        }
    }

    private String generatePlatformClientId() {
        String randomPart = Base62Helper.generateRandom(SimpleAkskServerConstant.CLIENT_ID_RANDOM_LENGTH);
        return SimpleAkskServerConstant.CLIENT_ID_PREFIX_PLATFORM + randomPart;
    }

    private String generateUserClientId(String ownerUserId) {
        String randomPart = Base62Helper.generateRandom(SimpleAkskServerConstant.CLIENT_ID_RANDOM_LENGTH);
        return SimpleAkskServerConstant.CLIENT_ID_PREFIX_USER + randomPart;
    }

    private String generateClientSecret() {
        String randomPart = Base62Helper.generateRandom(SimpleAkskServerConstant.SECRET_KEY_RANDOM_LENGTH);
        return SimpleAkskServerConstant.SECRET_KEY_PREFIX + randomPart;
    }

    private ClientInfoResponse toClientInfoResponse(OAuth2RegisteredClientEntity entity) {
        ClientInfoResponse info = new ClientInfoResponse();
        info.setClientId(entity.getClientId());
        info.setClientSecret(null);  // 不返回secret，仅创建时返回一次
        info.setClientName(entity.getClientName());
        info.setClientType(entity.getClientType());
        info.setOwnerUserId(entity.getOwnerUserId());
        info.setOwnerUsername(entity.getOwnerUsername());
        info.setEnabled(entity.isEnabled());
        info.setClientIdIssuedAt(entity.getClientIdIssuedAt());

        // Convert scopes string to list
        if (entity.getScopes() != null && !entity.getScopes().isEmpty()) {
            List<String> scopesList = Arrays.asList(entity.getScopes().split(SimpleAkskServerConstant.SCOPE_DELIMITER));
            info.setScopes(scopesList);
        }

        return info;
    }
}

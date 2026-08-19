package io.github.surezzzzzz.sdk.auth.aksk.server.service.impl;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ApplicationAuthorizationResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventCause;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ApplicationAuthorizationConflictException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ApplicationAuthorizationNotFoundException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ClientException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.SimpleAkskServerException;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ApplicationAuthorizationManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.ManagementDataAccessPlanHelper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.support.ApplicationAuthorizationValidationHelper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.claim.DataGrantDocumentClaimMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AKSK 服务主体应用授权管理实现。
 *
 * @author surezzzzzz
 */
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class ApplicationAuthorizationManagementServiceImpl implements ApplicationAuthorizationManagementService {

    private static final int LIST_SCAN_SIZE = 200;

    private final AkskApplicationAuthorizationRepository authorizationRepository;
    private final OAuth2RegisteredClientEntityRepository clientRepository;
    private final TokenManagementService tokenManagementService;

    @Override
    @Transactional
    public ApplicationAuthorizationResponse createLocal(String clientId, ApplicationAuthorizationRequest request) {
        OAuth2RegisteredClientEntity client = requireClient(clientId);
        if (authorizationRepository.findByClientId(clientId).isPresent()) {
            throw new ApplicationAuthorizationConflictException();
        }
        RequestValue value = normalize(request);
        Instant now = Instant.now();
        AkskApplicationAuthorizationEntity authorization = new AkskApplicationAuthorizationEntity();
        authorization.setClientId(clientId);
        authorization.setCreatedAt(now);
        apply(authorization, value, 1L, now);
        return toResponse(authorizationRepository.save(authorization), client);
    }

    @Override
    public ApplicationAuthorizationResponse getLocal(String clientId) {
        AkskApplicationAuthorizationEntity authorization = authorizationRepository.findByClientId(clientId).orElse(null);
        return authorization == null ? null : toResponse(authorization, requireClient(clientId));
    }

    @Override
    public Map<String, ApplicationAuthorizationResponse> getLocalByClientIds(List<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, ApplicationAuthorizationResponse> authorizations = new LinkedHashMap<String, ApplicationAuthorizationResponse>();
        for (AkskApplicationAuthorizationEntity authorization : authorizationRepository.findAllByClientIdIn(clientIds)) {
            ApplicationAuthorizationResponse response = new ApplicationAuthorizationResponse();
            response.setClientId(authorization.getClientId());
            response.setAdmitted(authorization.getAdmitted());
            response.setEnabled(authorization.getEnabled());
            response.setAuthorizationVersion(authorization.getAuthorizationVersion());
            authorizations.put(authorization.getClientId(), response);
        }
        return authorizations;
    }

    @Override
    @Transactional
    public ApplicationAuthorizationResponse replaceLocal(String clientId, ApplicationAuthorizationRequest request) {
        AkskApplicationAuthorizationEntity authorization = requireAuthorization(clientId);
        RequestValue value = normalize(request);
        if (!authorization.getApplicationCode().equals(value.applicationCode)) {
            throw new SimpleAkskServerException(ErrorCode.VALIDATION_FAILED, "应用编码不能变更");
        }
        apply(authorization, value, nextVersion(authorization), Instant.now());
        AkskApplicationAuthorizationEntity updated = authorizationRepository.save(authorization);
        // 授权内容已整体替换，旧 Token 的权限快照不能继续有效；撤销失败会回滚本次替换。
        tokenManagementService.revokeAllByClientId(clientId,
                TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED);
        return toResponse(updated, requireClient(clientId));
    }

    @Override
    @Transactional
    public void revokeLocal(String clientId) {
        AkskApplicationAuthorizationEntity authorization = requireAuthorization(clientId);
        Instant now = Instant.now();
        authorization.setEnabled(Boolean.FALSE);
        authorization.setAdmitted(Boolean.FALSE);
        authorization.setAuthorizationVersion(nextVersion(authorization));
        authorization.setRevokedAt(now);
        authorization.setUpdatedAt(now);
        authorizationRepository.save(authorization);
        // 授权撤销与活跃 Token 失效属于同一事务，避免已撤销授权仍可使用既有 Token。
        tokenManagementService.revokeAllByClientId(clientId,
                TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED);
    }

    @Override
    @Transactional
    public ApplicationAuthorizationResponse create(String clientId, ApplicationAuthorizationRequest request,
                                                   DataAccessPlan plan) {
        OAuth2RegisteredClientEntity client = requireClient(clientId);
        RequestValue value = normalize(request);
        if (!ManagementDataAccessPlanHelper.isApplicationAuthorizationCreateAllowed(plan, client,
                value.applicationCode)) {
            throw new ManagementAccessDeniedException();
        }
        if (authorizationRepository.findByClientId(clientId).isPresent()) {
            throw new ApplicationAuthorizationConflictException();
        }
        Instant now = Instant.now();
        AkskApplicationAuthorizationEntity authorization = new AkskApplicationAuthorizationEntity();
        authorization.setClientId(clientId);
        authorization.setCreatedAt(now);
        apply(authorization, value, 1L, now);
        return toResponse(authorizationRepository.save(authorization), client);
    }

    @Override
    public ApplicationAuthorizationResponse get(String clientId, DataAccessPlan plan) {
        AkskApplicationAuthorizationEntity authorization = requireAuthorization(clientId);
        OAuth2RegisteredClientEntity client = requireClient(clientId);
        requireAllowed(plan, authorization, client);
        return toResponse(authorization, client);
    }

    @Override
    public PageResponse<ApplicationAuthorizationResponse> list(Integer page, Integer size, DataAccessPlan plan) {
        int resolvedPage = page == null ? 1 : Math.max(1, page);
        int resolvedSize = size == null ? 20 : Math.max(1, size);
        long start = ((long) resolvedPage - 1L) * resolvedSize;
        long total = 0L;
        List<ApplicationAuthorizationResponse> content = new ArrayList<ApplicationAuthorizationResponse>();
        int scanPage = 0;
        Page<AkskApplicationAuthorizationEntity> authorizationPage;
        do {
            authorizationPage = authorizationRepository.findAll(PageRequest.of(scanPage, LIST_SCAN_SIZE,
                    Sort.by(Sort.Direction.DESC, "updatedAt")));
            Map<String, OAuth2RegisteredClientEntity> clientsByClientId = clientsByClientId(
                    authorizationPage.getContent());
            for (AkskApplicationAuthorizationEntity authorization : authorizationPage) {
                ApplicationAuthorizationResponse response = toAllowedResponse(authorization,
                        clientsByClientId.get(authorization.getClientId()), plan);
                if (response == null) {
                    continue;
                }
                if (total >= start && content.size() < resolvedSize) {
                    content.add(response);
                }
                total++;
            }
            scanPage++;
        } while (authorizationPage.hasNext());
        return PageResponse.of(content, Long.valueOf(total), resolvedPage, resolvedSize);
    }

    @Override
    @Transactional
    public ApplicationAuthorizationResponse replace(String clientId, ApplicationAuthorizationRequest request,
                                                    DataAccessPlan plan, DataAccessPlan tokenPlan) {
        AkskApplicationAuthorizationEntity authorization = requireAuthorization(clientId);
        OAuth2RegisteredClientEntity client = requireClient(clientId);
        requireAllowed(plan, authorization, client);
        RequestValue value = normalize(request);
        if (!authorization.getApplicationCode().equals(value.applicationCode)) {
            throw new SimpleAkskServerException(ErrorCode.VALIDATION_FAILED, "应用编码不能变更");
        }
        tokenManagementService.requireAllByClientIdAllowed(clientId, tokenPlan);
        apply(authorization, value, nextVersion(authorization), Instant.now());
        AkskApplicationAuthorizationEntity updated = authorizationRepository.save(authorization);
        // 预检通过后才可整体替换授权；后续 Token 撤销失败会回滚，避免权限快照新旧并存。
        tokenManagementService.revokeAllByClientId(clientId, tokenPlan,
                TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED);
        return toResponse(updated, client);
    }

    @Override
    @Transactional
    public void revoke(String clientId, DataAccessPlan plan, DataAccessPlan tokenPlan) {
        AkskApplicationAuthorizationEntity authorization = requireAuthorization(clientId);
        OAuth2RegisteredClientEntity client = requireClient(clientId);
        requireAllowed(plan, authorization, client);
        tokenManagementService.requireAllByClientIdAllowed(clientId, tokenPlan);
        Instant now = Instant.now();
        authorization.setEnabled(Boolean.FALSE);
        authorization.setAdmitted(Boolean.FALSE);
        authorization.setAuthorizationVersion(nextVersion(authorization));
        authorization.setRevokedAt(now);
        authorization.setUpdatedAt(now);
        authorizationRepository.save(authorization);
        // 授权撤销和全部活跃 Token 失效必须共同提交，预检已确保不存在部分撤销的权限越界。
        tokenManagementService.revokeAllByClientId(clientId, tokenPlan,
                TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED);
    }

    private Map<String, OAuth2RegisteredClientEntity> clientsByClientId(
            List<AkskApplicationAuthorizationEntity> authorizations) {
        if (authorizations.isEmpty()) {
            return Collections.emptyMap();
        }
        return clientRepository.findAllByClientIdIn(authorizations.stream()
                        .map(AkskApplicationAuthorizationEntity::getClientId)
                        .collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(OAuth2RegisteredClientEntity::getClientId, client -> client));
    }

    private ApplicationAuthorizationResponse toAllowedResponse(AkskApplicationAuthorizationEntity authorization,
                                                               OAuth2RegisteredClientEntity client,
                                                               DataAccessPlan plan) {
        if (client == null || !ManagementDataAccessPlanHelper.isApplicationAuthorizationAllowed(plan, authorization,
                client)) {
            return null;
        }
        return toResponse(authorization, client);
    }

    private void requireAllowed(DataAccessPlan plan, AkskApplicationAuthorizationEntity authorization,
                                OAuth2RegisteredClientEntity client) {
        if (!ManagementDataAccessPlanHelper.isApplicationAuthorizationAllowed(plan, authorization, client)) {
            throw new ManagementAccessDeniedException();
        }
    }

    private AkskApplicationAuthorizationEntity requireAuthorization(String clientId) {
        return authorizationRepository.findByClientId(clientId)
                .orElseThrow(ApplicationAuthorizationNotFoundException::new);
    }

    private OAuth2RegisteredClientEntity requireClient(String clientId) {
        return clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientException(ErrorCode.CLIENT_NOT_FOUND,
                        String.format(ErrorMessage.CLIENT_NOT_FOUND, clientId)));
    }

    private RequestValue normalize(ApplicationAuthorizationRequest request) {
        if (request == null) {
            throw new SimpleAkskServerException(ErrorCode.VALIDATION_FAILED, "应用授权请求不能为空");
        }
        try {
            RequestValue value = new RequestValue();
            value.applicationCode = ApplicationAuthorizationValidationHelper.requireApplicationCode(
                    request.getApplicationCode());
            value.admitted = Boolean.TRUE.equals(request.getAdmitted());
            value.roles = ApplicationAuthorizationValidationHelper.normalizePermissions(
                    requiredList(request.getRoles()), "roles");
            value.pagePermissions = ApplicationAuthorizationValidationHelper.normalizePermissions(
                    requiredList(request.getPagePermissions()), "pagePermissions");
            value.apiPermissions = ApplicationAuthorizationValidationHelper.normalizePermissions(
                    requiredList(request.getApiPermissions()), "apiPermissions");
            value.manifestVersion = ApplicationAuthorizationValidationHelper.requireIdentifier(
                    request.getManifestVersion(), "manifestVersion");
            value.manifestDigest = ApplicationAuthorizationValidationHelper.requireManifestDigest(
                    request.getManifestDigest());
            value.dataGrantDocument = request.getDataGrantDocument() == null ? null
                    : DataGrantDocumentClaimMapper.fromClaim(request.getDataGrantDocument());
            return value;
        } catch (RuntimeException exception) {
            throw new SimpleAkskServerException(ErrorCode.VALIDATION_FAILED, "应用授权请求无效", exception);
        }
    }

    private void apply(AkskApplicationAuthorizationEntity authorization, RequestValue value, long version, Instant now) {
        authorization.setApplicationCode(value.applicationCode);
        authorization.setAdmitted(Boolean.valueOf(value.admitted));
        authorization.setRolesJson(AkskApplicationAuthorizationJsonCodec.writeStringList(value.roles));
        authorization.setPagePermissionsJson(AkskApplicationAuthorizationJsonCodec.writeStringList(value.pagePermissions));
        authorization.setApiPermissionsJson(AkskApplicationAuthorizationJsonCodec.writeStringList(value.apiPermissions));
        authorization.setDataGrantDocumentJson(value.dataGrantDocument == null ? null
                : AkskApplicationAuthorizationJsonCodec.writeDataGrantDocument(value.dataGrantDocument));
        authorization.setManifestVersion(value.manifestVersion);
        authorization.setManifestDigest(value.manifestDigest);
        authorization.setAuthorizationVersion(Long.valueOf(version));
        authorization.setEnabled(Boolean.TRUE);
        authorization.setRevokedAt(null);
        authorization.setUpdatedAt(now);
    }

    private long nextVersion(AkskApplicationAuthorizationEntity authorization) {
        return authorization.getAuthorizationVersion() == null ? 1L
                : authorization.getAuthorizationVersion().longValue() + 1L;
    }

    private ApplicationAuthorizationResponse toResponse(AkskApplicationAuthorizationEntity authorization,
                                                        OAuth2RegisteredClientEntity client) {
        ApplicationAuthorizationResponse response = new ApplicationAuthorizationResponse();
        response.setClientId(authorization.getClientId());
        response.setClientType(client.getClientType());
        response.setOwnerUserId(client.getOwnerUserId());
        response.setApplicationCode(authorization.getApplicationCode());
        response.setAdmitted(authorization.getAdmitted());
        response.setEnabled(authorization.getEnabled());
        response.setRoles(AkskApplicationAuthorizationJsonCodec.readStringList(authorization.getRolesJson()));
        response.setPagePermissions(AkskApplicationAuthorizationJsonCodec.readStringList(
                authorization.getPagePermissionsJson()));
        response.setApiPermissions(AkskApplicationAuthorizationJsonCodec.readStringList(
                authorization.getApiPermissionsJson()));
        DataGrantDocument document = AkskApplicationAuthorizationJsonCodec.readDataGrantDocument(
                authorization.getDataGrantDocumentJson());
        response.setDataGrantDocument(document == null ? null : DataGrantDocumentClaimMapper.toClaim(document));
        response.setAuthorizationVersion(authorization.getAuthorizationVersion());
        response.setManifestVersion(authorization.getManifestVersion());
        response.setManifestDigest(authorization.getManifestDigest());
        response.setCreatedAt(authorization.getCreatedAt());
        response.setUpdatedAt(authorization.getUpdatedAt());
        response.setRevokedAt(authorization.getRevokedAt());
        return response;
    }

    private List<String> requiredList(List<String> value) {
        return value == null ? new ArrayList<String>() : value;
    }

    private static final class RequestValue {
        private String applicationCode;
        private boolean admitted;
        private List<String> roles;
        private List<String> pagePermissions;
        private List<String> apiPermissions;
        private DataGrantDocument dataGrantDocument;
        private String manifestVersion;
        private String manifestDigest;
    }
}

package io.github.surezzzzzz.sdk.auth.iam.server.controller;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.oauth2.response.OAuth2ConsentInfoResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OAuth2 协议辅助接口
 *
 * <p>仅供 Vue SPA 查询 consent 页面所需数据；实际协议提交仍由 SAS 端点处理。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestController
@RequestMapping("/iam/web/oauth2")
@RequiredArgsConstructor
public class IamOAuth2ProtocolEndpoint {

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2AuthorizationConsentService authorizationConsentService;
    private final RegisteredClientRepository registeredClientRepository;
    private final SessionService sessionService;

    /**
     * 获取 Consent 页面所需数据
     *
     * @param state       SAS 内部 state（由 /oauth2/authorize 重定向时携带）
     * @param userDetails 当前已认证用户
     */
    @GetMapping("/consent-info")
    public ResponseEntity<OAuth2ConsentInfoResponse> consentInfo(
            @RequestParam(OAuth2ParameterNames.STATE) String state,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest servletRequest) {

        OAuth2Authorization authorization = authorizationService.findByToken(
                state, new OAuth2TokenType(OAuth2ParameterNames.STATE));
        if (authorization == null || userDetails == null
                || !userDetails.getUsername().equals(authorization.getPrincipalName())) {
            return ResponseEntity.badRequest().build();
        }
        IamSessionEntity iamSession = servletRequest.getSession(false) == null ? null
                : sessionService.getActiveBinding(servletRequest.getSession(false));
        String authorizationIamSessionId = authorization.getAttribute(
                SimpleIamServerConstant.AUTHORIZATION_ATTRIBUTE_IAM_SESSION_ID);
        if (iamSession == null || !iamSession.getUsername().equals(authorization.getPrincipalName())
                || authorizationIamSessionId == null || !authorizationIamSessionId.equals(iamSession.getId())) {
            return ResponseEntity.badRequest().build();
        }

        RegisteredClient registeredClient = registeredClientRepository.findById(
                authorization.getRegisteredClientId());
        if (registeredClient == null) {
            return ResponseEntity.badRequest().build();
        }

        // 从原始授权请求取请求 scope
        Set<String> requestedScopeSet = registeredClient.getScopes();
        OAuth2AuthorizationRequest authRequest = authorization.getAttribute(
                OAuth2AuthorizationRequest.class.getName());
        if (authRequest != null && authRequest.getScopes() != null) {
            requestedScopeSet = authRequest.getScopes();
        }

        // 查已批准的 scope
        List<String> previouslyApproved = Collections.emptyList();
        if (userDetails != null) {
            OAuth2AuthorizationConsent existing = authorizationConsentService.findById(
                    registeredClient.getId(), userDetails.getUsername());
            if (existing != null && existing.getScopes() != null) {
                previouslyApproved = new ArrayList<>(existing.getScopes());
            }
        }

        List<String> requested = requestedScopeSet != null
                ? requestedScopeSet.stream().sorted().collect(Collectors.toList())
                : Collections.emptyList();

        return ResponseEntity.ok(new OAuth2ConsentInfoResponse(
                registeredClient.getId(),
                registeredClient.getClientId(),
                registeredClient.getClientName(),
                state,
                requested,
                previouslyApproved
        ));
    }
}

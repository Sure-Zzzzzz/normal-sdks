package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientCredentialsAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default Scope Authentication Converter
 * <p>
 * 当客户端请求token时未提供scope参数，自动使用数据库中注册的scope
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultScopeAuthenticationConverter implements AuthenticationConverter {

    private final OAuth2RegisteredClientEntityRepository entityRepository;

    @Override
    public Authentication convert(HttpServletRequest request) {
        // 只处理 client_credentials grant type
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(grantType)) {
            return null;
        }

        // 获取客户端认证信息（由OAuth2ClientAuthenticationFilter已经完成认证）
        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        if (clientPrincipal == null || !clientPrincipal.isAuthenticated()) {
            return null;
        }

        // 获取请求中的scope参数
        String scopeParam = request.getParameter(OAuth2ParameterNames.SCOPE);
        Set<String> requestedScopes = new HashSet<>();

        if (StringUtils.hasText(scopeParam)) {
            // 如果提供了scope，使用请求中的scope
            requestedScopes = Arrays.stream(scopeParam.split("\\s+"))
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toSet());
            log.debug("Using requested scopes: {}", requestedScopes);
        } else {
            // 如果未提供scope，从数据库读取注册的scope
            String clientId = clientPrincipal.getName();
            Optional<OAuth2RegisteredClientEntity> entityOpt = entityRepository.findByClientId(clientId);

            if (entityOpt.isPresent()) {
                OAuth2RegisteredClientEntity entity = entityOpt.get();
                if (StringUtils.hasText(entity.getScopes())) {
                    requestedScopes = Arrays.stream(entity.getScopes().split(SimpleAkskServerConstant.SCOPE_DELIMITER))
                            .filter(StringUtils::hasText)
                            .collect(Collectors.toSet());
                    log.info("Using default scopes from database for client {}: {}", clientId, requestedScopes);
                }
            }

            // 如果数据库中也没有scope，抛出异常（数据完整性问题）
            if (requestedScopes.isEmpty()) {
                log.error("No scopes found in database for client: {}, this indicates data integrity issue", clientId);
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_scope",
                        "Client has no registered scopes in database", null));
            }
        }

        // 获取额外参数（如security_context）
        Map<String, Object> additionalParameters = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!key.equals(OAuth2ParameterNames.GRANT_TYPE) &&
                    !key.equals(OAuth2ParameterNames.CLIENT_ID) &&
                    !key.equals(OAuth2ParameterNames.CLIENT_SECRET) &&
                    !key.equals(OAuth2ParameterNames.SCOPE)) {
                additionalParameters.put(key, values.length == 1 ? values[0] : Arrays.asList(values));
            }
        });

        return new OAuth2ClientCredentialsAuthenticationToken(
                clientPrincipal,
                requestedScopes,
                additionalParameters
        );
    }
}

package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientCredentialsAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Map;

/**
 * JWT Token Customizer
 * <p>
 * 自定义JWT claims，添加client_type, user_id, username等字段
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class JwtTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private final OAuth2RegisteredClientEntityRepository entityRepository;
    private final SimpleAkskServerProperties properties;

    @Override
    public void customize(JwtEncodingContext context) {
        String clientId = context.getRegisteredClient().getClientId();

        // 查询客户端扩展信息
        OAuth2RegisteredClientEntity entity = entityRepository.findByClientId(clientId).orElse(null);
        if (entity == null) {
            log.warn("未找到客户端扩展信息: clientId={}", clientId);
            return;
        }

        // 添加client_id claim（与sub相同，但更明确）
        context.getClaims().claim(SimpleAkskServerConstant.JWT_CLAIM_CLIENT_ID, clientId);

        // 添加client_type claim
        ClientType clientType = ClientType.fromCode(entity.getClientType());
        if (clientType != null) {
            context.getClaims().claim(SimpleAkskServerConstant.JWT_CLAIM_CLIENT_TYPE, clientType.getValue());
            log.debug("添加client_type claim: clientId={}, clientType={}", clientId, clientType.getValue());
        }

        // 如果是用户级AKSK，添加user_id和username
        if (ClientType.USER.equals(clientType) && entity.getOwnerUserId() != null) {
            context.getClaims().claim(SimpleAkskServerConstant.JWT_CLAIM_USER_ID, entity.getOwnerUserId());
            log.debug("添加user_id claim: clientId=, userId={}", clientId, entity.getOwnerUserId());

            // 添加username claim
            if (entity.getOwnerUsername() != null) {
                context.getClaims().claim(SimpleAkskServerConstant.JWT_CLAIM_USERNAME, entity.getOwnerUsername());
                log.debug("添加username claim: clientId={}, username={}", clientId, entity.getOwnerUsername());
            }
        }

        // 添加security_context claim（如果客户端在token请求中提供）
        try {
            if (context.getAuthorizationGrant() instanceof OAuth2ClientCredentialsAuthenticationToken) {
                OAuth2ClientCredentialsAuthenticationToken authenticationToken =
                        (OAuth2ClientCredentialsAuthenticationToken) context.getAuthorizationGrant();
                Map<String, Object> additionalParameters = authenticationToken.getAdditionalParameters();

                if (additionalParameters.containsKey(SimpleAkskServerConstant.OAUTH2_PARAM_SECURITY_CONTEXT)) {
                    Object securityContext = additionalParameters.get(SimpleAkskServerConstant.OAUTH2_PARAM_SECURITY_CONTEXT);
                    if (securityContext != null) {
                        // 验证大小
                        if (securityContext instanceof String) {
                            String contextStr = (String) securityContext;
                            Integer maxSize = properties.getJwt().getSecurityContextMaxSize();
                            if (contextStr.length() > maxSize) {
                                log.warn("security_context 超过大小限制: clientId={}, size={}, maxSize={}",
                                        clientId, contextStr.length(), maxSize);
                                throw new OAuth2AuthenticationException(new OAuth2Error("security_context_too_large",
                                        "security_context 不能超过 " + maxSize + " 字节", null));
                            }
                        }
                        context.getClaims().claim(SimpleAkskServerConstant.JWT_CLAIM_SECURITY_CONTEXT, securityContext);
                        log.debug("添加security_context claim: clientId={}, securityContext={}", clientId, securityContext);
                    }
                }
            }
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("读取security_context参数失败: clientId={}", clientId, e);
        }
    }
}

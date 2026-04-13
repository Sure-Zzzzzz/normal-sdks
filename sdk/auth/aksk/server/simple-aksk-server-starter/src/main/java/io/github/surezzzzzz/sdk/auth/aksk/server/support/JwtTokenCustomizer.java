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
 * 自定义JWT claims，添加以下扩展字段：
 * <ul>
 *   <li>client_id: 客户端ID（AKSK）</li>
 *   <li>auth_server_id: 认证服务器标识（用于多认证服务器场景区分token来源）</li>
 *   <li>client_type: 客户端类型（platform/user）</li>
 *   <li>user_id: 用户ID（仅用户级AKSK）</li>
 *   <li>username: 用户名（仅用户级AKSK）</li>
 *   <li>security_context: 自定义安全上下文（可选）</li>
 * </ul>
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

        // 1. 查询客户端扩展信息
        OAuth2RegisteredClientEntity entity = entityRepository.findByClientId(clientId).orElse(null);
        if (entity == null) {
            log.warn("未找到客户端扩展信息: clientId={}", clientId);
            return;
        }

        // 2. 添加基础 claims
        // client_id: 客户端标识（与标准claim sub相同，但更明确）
        context.getClaims().claim(SimpleAkskServerConstant.JWT_CLAIM_CLIENT_ID, clientId);

        // auth_server_id: 认证服务器标识，用于多认证服务器场景
        // 从配置的 jwt.key-id 中获取，通常与 JWT Header 的 kid 保持一致
        // 用途：API网关（如APISIX）可通过此字段识别token来源，实现多租户或多环境隔离
        context.getClaims().claim(SimpleAkskServerConstant.JWT_CLAIM_AUTH_SERVER_ID, properties.getJwt().getKeyId());
        log.debug("添加基础claims: clientId={}, authServerId={}", clientId, properties.getJwt().getKeyId());

        // 3. 添加 client_type claim（平台级/用户级）
        ClientType clientType = ClientType.fromCode(entity.getClientType());
        if (clientType != null) {
            context.getClaims().claim(SimpleAkskServerConstant.JWT_CLAIM_CLIENT_TYPE, clientType.getValue());
            log.debug("添加client_type claim: clientId={}, clientType={}", clientId, clientType.getValue());
        }

        // 4. 如果是用户级AKSK，添加用户相关信息
        if (ClientType.USER.equals(clientType) && entity.getOwnerUserId() != null) {
            // user_id: 用户唯一标识
            context.getClaims().claim(SimpleAkskServerConstant.JWT_CLAIM_USER_ID, entity.getOwnerUserId());
            log.debug("添加user_id claim: clientId={}, userId={}", clientId, entity.getOwnerUserId());

            // username: 用户名（可选）
            if (entity.getOwnerUsername() != null) {
                context.getClaims().claim(SimpleAkskServerConstant.JWT_CLAIM_USERNAME, entity.getOwnerUsername());
                log.debug("添加username claim: clientId={}, username={}", clientId, entity.getOwnerUsername());
            }
        }

        // 5. 添加 security_context claim（可选，由客户端在token请求中提供）
        // 用途：允许客户端在换取token时传递自定义的安全上下文信息（如租户ID、区域等）
        // 使用方式：POST /oauth2/token?grant_type=client_credentials&security_context={"tenant":"xxx"}
        try {
            if (context.getAuthorizationGrant() instanceof OAuth2ClientCredentialsAuthenticationToken) {
                OAuth2ClientCredentialsAuthenticationToken authenticationToken =
                        (OAuth2ClientCredentialsAuthenticationToken) context.getAuthorizationGrant();
                Map<String, Object> additionalParameters = authenticationToken.getAdditionalParameters();

                if (additionalParameters.containsKey(SimpleAkskServerConstant.OAUTH2_PARAM_SECURITY_CONTEXT)) {
                    Object securityContext = additionalParameters.get(SimpleAkskServerConstant.OAUTH2_PARAM_SECURITY_CONTEXT);
                    if (securityContext != null) {
                        // 验证 security_context 大小，防止JWT过大
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
            // OAuth2认证异常直接抛出，阻止token签发
            throw e;
        } catch (Exception e) {
            // 其他异常仅记录日志，不影响token签发
            log.warn("读取security_context参数失败: clientId={}", clientId, e);
        }
    }
}

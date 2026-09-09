package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.service;

import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.annotation.SimpleIamOidcAdapterComponent;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.configuration.SimpleIamOidcAdapterProperties;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.constant.SimpleIamOidcAdapterConstant;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.exception.SimpleIamOidcAdapterException;
import io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.model.PendingAuthorization;
import io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.core.exception.IamProtocolException;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalBrowserLoginProvider;
import io.github.surezzzzzz.sdk.auth.iam.core.spi.ExternalIdentity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * OIDC 授权码模式登录提供方。
 *
 * <p>授权阶段生成 nonce 并以 state 为键经 {@link PendingStateStore} 暂存
 * （与回调 redirect_uri 一并记忆），回调阶段以授权码换令牌、用 JWKS 验 ID token
 * 签名并校验 iss/aud/exp/nonce，验证通过后以 sub 为 externalId 构造
 * {@link ExternalIdentity}。store 由装配方按部署形态选择（多实例 Redis / 单实例内存）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamOidcAdapterComponent
public class OidcBrowserLoginProvider implements ExternalBrowserLoginProvider {

    private final SimpleIamOidcAdapterProperties properties;

    private final RestOperations restOperations;

    private final JwtDecoder jwtDecoder;

    /**
     * state -> 授权上下文（nonce + 回调地址）；consume 原子取删，过期由 store 自身接管。
     *
     * <p>适配器不持有浏览器会话（SPI 无状态契约），state 是 authorize 与 callback
     * 两次调用间唯一的关联物；多实例部署下两次调用可落在不同实例，
     * Redis 实现以共享存储承载，内存实现退回单实例语义。</p>
     */
    private final PendingStateStore pendingStateStore;

    /**
     * 由适配器精准扫描装配；RestTemplate / NimbusJwtDecoder 的组装与
     * iss/aud 校验器配置、必填项校验在构造内自治完成
     * （原 AutoConfiguration @Bean 组装逻辑平移）。
     *
     * @param properties        OIDC 适配器配置
     * @param pendingStateStore 授权暂存上下文（由装配方按部署形态选型）
     */
    public OidcBrowserLoginProvider(SimpleIamOidcAdapterProperties properties,
                                    PendingStateStore pendingStateStore) {
        validate(properties);
        this.properties = properties;
        // IdP 故障时不得无限挂起回调线程：token 端点与 JWKS 拉取均走此超时配置
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(SimpleIamOidcAdapterConstant.DEFAULT_CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(SimpleIamOidcAdapterConstant.DEFAULT_READ_TIMEOUT_MILLIS);
        this.restOperations = new RestTemplate(requestFactory);
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri())
                        .restOperations((RestTemplate) this.restOperations)
                        .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(Arrays.asList(
                JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
                new JwtClaimValidator<List<String>>("aud",
                        aud -> aud != null && aud.contains(properties.getClientId())))));
        this.jwtDecoder = decoder;
        this.pendingStateStore = pendingStateStore;
        log.info("OIDC 登录适配器就绪：issuer={}, clientId={}, 暂存实现={}",
                properties.getIssuer(), properties.getClientId(),
                pendingStateStore.getClass().getSimpleName());
    }

    private void validate(SimpleIamOidcAdapterProperties properties) {
        boolean complete = StringUtils.hasText(properties.getIssuer())
                && StringUtils.hasText(properties.getAuthorizationUri())
                && StringUtils.hasText(properties.getTokenUri())
                && StringUtils.hasText(properties.getJwkSetUri())
                && StringUtils.hasText(properties.getClientId())
                && StringUtils.hasText(properties.getClientSecret());
        if (!complete) {
            throw new SimpleIamOidcAdapterException(
                    SimpleIamOidcAdapterConstant.ERROR_CONFIG_INCOMPLETE,
                    String.format(SimpleIamOidcAdapterConstant.TEMPLATE_CONFIG_INCOMPLETE,
                            SimpleIamOidcAdapterConstant.CONFIG_PREFIX));
        }
    }

    /**
     * 登录方式编码（外部身份源注册标识）
     */
    @Override
    public String providerCode() {
        return SimpleIamOidcAdapterConstant.PROVIDER_CODE;
    }

    /**
     * 构造上游授权端点跳转地址（携带 state / nonce）
     */
    @Override
    public String buildAuthorizeUrl(String callbackUrl, String state) {
        String nonce = UUID.randomUUID().toString();
        pendingStateStore.save(state, new PendingAuthorization(nonce, callbackUrl),
                Duration.ofMinutes(SimpleIamOidcAdapterConstant.PENDING_TTL_MINUTES));
        log.debug("OIDC 授权发起：callback 已登记，TTL={} 分钟",
                SimpleIamOidcAdapterConstant.PENDING_TTL_MINUTES);
        return UriComponentsBuilder.fromHttpUrl(properties.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", callbackUrl)
                .queryParam("scope", String.join(" ", properties.getScopes()))
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .build()
                .encode()
                .toUriString();
    }

    /**
     * 消费回调：校验 state、以授权码换 token 并装载外部身份
     */
    @Override
    public ExternalIdentity consumeCallback(Map<String, String> callbackParams) {
        if (callbackParams != null && StringUtils.hasText(callbackParams.get("error"))) {
            throw new IamProtocolException(ErrorCode.EXTERNAL_CALLBACK_INVALID, "外部 IdP 返回登录失败");
        }
        String code = callbackParams == null ? null : callbackParams.get("code");
        String state = callbackParams == null ? null : callbackParams.get("state");
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new IamProtocolException(ErrorCode.EXTERNAL_CALLBACK_INVALID, "回调参数缺失");
        }
        PendingAuthorization pending = pendingStateStore.consume(state);
        if (pending == null) {
            throw new IamProtocolException(ErrorCode.EXTERNAL_CALLBACK_INVALID, "state 无效或已使用");
        }
        String idTokenValue = exchangeIdToken(code, pending);
        Jwt idToken = decodeAndValidate(idTokenValue, pending.getNonce());
        log.debug("OIDC 回调验证通过：externalId={}", idToken.getSubject());
        return toIdentity(idToken);
    }

    private String exchangeIdToken(String code, PendingAuthorization pending) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(properties.getClientId(), properties.getClientSecret(), StandardCharsets.UTF_8);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", pending.getCallbackUrl());
        try {
            ResponseEntity<Map<String, Object>> response = restOperations.exchange(
                    properties.getTokenUri(), HttpMethod.POST, new HttpEntity<>(form, headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Object idToken = response.getBody() == null ? null : response.getBody().get("id_token");
            if (idToken instanceof String && StringUtils.hasText((String) idToken)) {
                return (String) idToken;
            }
            throw new IamProtocolException(ErrorCode.EXTERNAL_CALLBACK_INVALID, "令牌端点未返回 ID token");
        } catch (RestClientResponseException exception) {
            if (exception.getRawStatusCode() >= 400 && exception.getRawStatusCode() < 500) {
                log.warn("外部 IdP 拒绝授权码交换：status={}", exception.getRawStatusCode(),
                        exception);
                throw new IamProtocolException(ErrorCode.EXTERNAL_CALLBACK_INVALID,
                        "授权码被外部 IdP 拒绝");
            }
            log.warn("外部 IdP 令牌端点异常：status={}", exception.getRawStatusCode(),
                    exception);
            throw new IamProtocolException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE,
                    "外部 IdP 令牌端点异常");
        } catch (ResourceAccessException exception) {
            log.warn("外部 IdP 不可达：{}", exception.getMessage(), exception);
            throw new IamProtocolException(ErrorCode.EXTERNAL_PROVIDER_UNAVAILABLE,
                    "外部 IdP 不可达");
        }
    }

    private Jwt decodeAndValidate(String idTokenValue, String expectedNonce) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idTokenValue);
        } catch (JwtException exception) {
            log.debug("ID token 校验未通过: {}", exception.getMessage());
            throw new IamProtocolException(ErrorCode.EXTERNAL_CALLBACK_INVALID, "ID token 校验未通过");
        }
        if (!Objects.equals(expectedNonce, jwt.getClaimAsString("nonce"))) {
            throw new IamProtocolException(ErrorCode.EXTERNAL_CALLBACK_INVALID, "nonce 不符");
        }
        return jwt;
    }

    private ExternalIdentity toIdentity(Jwt idToken) {
        String subject = idToken.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new IamProtocolException(ErrorCode.EXTERNAL_CALLBACK_INVALID, "ID token 缺少 sub");
        }
        String username = idToken.getClaimAsString("preferred_username");
        return ExternalIdentity.builder()
                .providerCode(providerCode())
                .externalId(subject)
                .usernameSuggestion(StringUtils.hasText(username) ? username : subject)
                .displayName(idToken.getClaimAsString("name"))
                .email(idToken.getClaimAsString("email"))
                .build();
    }
}

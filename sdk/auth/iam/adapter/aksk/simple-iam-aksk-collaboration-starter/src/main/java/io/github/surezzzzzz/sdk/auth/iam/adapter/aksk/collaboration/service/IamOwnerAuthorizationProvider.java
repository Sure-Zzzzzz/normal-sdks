package io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.*;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.spi.OwnerAuthorizationProvider;
import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.annotation.SimpleIamAkskCollaborationComponent;
import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.configuration.SimpleIamAkskCollaborationProperties;
import io.github.surezzzzzz.sdk.auth.iam.adapter.aksk.collaboration.constant.SimpleIamAkskCollaborationConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * IAM owner projection reader 的唯一 HTTP 客户端。
 *
 * <p>不缓存投影、不保存上次成功值，也不在异常时降级为本地授权。仅在内存中短暂缓存按 scope 隔离的
 * reader service token，绝不落库或写日志；任何协议、认证或网络失败均返回 inactive，由调用方按 AKU
 * 失败关闭处理。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamAkskCollaborationComponent
public class IamOwnerAuthorizationProvider implements OwnerAuthorizationProvider {

    /**
     * 提前刷新偏移（秒）：缓存在到期前该偏移即视为不可用，覆盖单次请求耗时。
     */
    private static final long TOKEN_REFRESH_SKEW_SECONDS = 30L;

    private static final ParameterizedTypeReference<Map<String, Object>> OBJECT_RESPONSE_TYPE =
            new ParameterizedTypeReference<Map<String, Object>>() {
            };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> OBJECT_LIST_RESPONSE_TYPE =
            new ParameterizedTypeReference<List<Map<String, Object>>>() {
            };

    private final SimpleAkskServerProperties serverProperties;
    private final SimpleIamAkskCollaborationProperties properties;
    private final RestTemplate restTemplate;
    private final ConcurrentMap<String, ServiceAccessToken> serviceAccessTokens =
            new ConcurrentHashMap<String, ServiceAccessToken>();

    /**
     * 创建 IAM owner authorization provider。
     *
     * @param serverProperties AKSK Server 配置
     * @param properties       适配器连接配置
     */
    @Autowired
    public IamOwnerAuthorizationProvider(SimpleAkskServerProperties serverProperties,
                                         SimpleIamAkskCollaborationProperties properties) {
        this(serverProperties, properties, createRestTemplate(properties));
    }

    /**
     * 创建可注入受控 HTTP client 的 provider。
     *
     * <p>供测试与定制装配注入受控 {@link RestTemplate}，不作为自动装配入口。</p>
     *
     * @param serverProperties AKSK Server 配置
     * @param properties       适配器连接配置
     * @param restTemplate     受控 HTTP client
     */
    public IamOwnerAuthorizationProvider(SimpleAkskServerProperties serverProperties,
                                         SimpleIamAkskCollaborationProperties properties, RestTemplate restTemplate) {
        this.serverProperties = Objects.requireNonNull(serverProperties, "serverProperties");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
    }

    private static RestTemplate createRestTemplate(SimpleIamAkskCollaborationProperties config) {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        // 未启用的适配器也会完成 Bean 装配；非法超时由 isAvailable 失败关闭，不在启动阶段空指针。
        factory.setConnectTimeout(timeoutOrDefault(config.getConnectTimeoutMillis(),
                SimpleIamAkskCollaborationConstant.DEFAULT_CONNECT_TIMEOUT_MILLIS));
        factory.setReadTimeout(timeoutOrDefault(config.getReadTimeoutMillis(),
                SimpleIamAkskCollaborationConstant.DEFAULT_READ_TIMEOUT_MILLIS));
        return new RestTemplate(factory);
    }

    @Override
    public boolean isAvailable() {
        return isEnabled() && isEndpointConfigurationValid();
    }

    /**
     * 读取所属人在目标应用的当前授权。
     *
     * @param owner               所属人稳定键
     * @param targetApplicationId 目标应用标识
     * @return 中性读取结果；不可用或失败关闭时返回无效结果
     */
    @Override
    public OwnerAuthorizationReadResult resolve(OwnerAuthorizationKey owner, Long targetApplicationId) {
        if (owner == null) {
            return OwnerAuthorizationReadResult.inactive();
        }
        return resolve(owner.getOwnerSourceId(), owner.getOwnerSubjectId(), targetApplicationId, null);
    }

    /**
     * 在持久化 binding 前读取 owner 当前授权，用于首次创建时建立本地投影。
     *
     * <p>调用方不得伪造临时 clientId；该读取只以 owner 与目标应用三元组为输入。</p>
     *
     * @param ownerSourceId       身份源标识
     * @param ownerSubjectId      身份源内稳定主体标识
     * @param targetApplicationId 目标应用标识
     * @param clientId            调用方上下文客户端标识
     * @return 中性读取结果
     */
    private OwnerAuthorizationReadResult resolve(String ownerSourceId, String ownerSubjectId,
                                                 Long targetApplicationId, String clientId) {
        if (!isEnabled() || !isEndpointConfigurationValid() || !hasValidOwnerIdentity(ownerSourceId, ownerSubjectId)
                || targetApplicationId == null
                || targetApplicationId.longValue() <= 0L) {
            return OwnerAuthorizationReadResult.inactive();
        }
        int maxAttempts = properties.getMaxAttempts().intValue();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String accessToken = null;
            try {
                accessToken = obtainServiceAccessToken(SimpleIamAkskCollaborationConstant.SCOPE_READ);
                if (StringUtils.hasText(accessToken)) {
                    return requestResolve(accessToken, ownerSourceId, ownerSubjectId, targetApplicationId, clientId);
                }
            } catch (RuntimeException exception) {
                invalidateServiceAccessToken(SimpleIamAkskCollaborationConstant.SCOPE_READ, accessToken, exception);
                if (attempt == maxAttempts) {
                    log.warn("IAM owner reader 调用失败，AKU 按失败关闭：clientId={}, applicationId={}, category={}",
                            clientId, targetApplicationId,
                            exception.getClass().getSimpleName());
                }
            }
        }
        return OwnerAuthorizationReadResult.inactive();
    }

    /**
     * 返回当前 owner 可创建 inherited AKU 的最小目标应用目录。
     *
     * @param owner 所属人稳定键
     * @return 候选应用列表；不可用或失败关闭时返回空列表
     */
    @Override
    public List<OwnerAuthorizationCandidate> listCandidates(OwnerAuthorizationKey owner) {
        if (!isEnabled() || !isEndpointConfigurationValid() || !hasValidOwnerIdentity(owner)) {
            return Collections.emptyList();
        }
        int maxAttempts = properties.getMaxAttempts().intValue();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String accessToken = null;
            try {
                accessToken = obtainServiceAccessToken(SimpleIamAkskCollaborationConstant.SCOPE_READ);
                if (StringUtils.hasText(accessToken)) {
                    return requestCandidates(accessToken, owner);
                }
            } catch (RuntimeException exception) {
                invalidateServiceAccessToken(SimpleIamAkskCollaborationConstant.SCOPE_READ, accessToken, exception);
                if (attempt == maxAttempts) {
                    log.warn("IAM owner 候选目录读取失败，拒绝创建 AKU：category={}",
                            exception.getClass().getSimpleName());
                }
            }
        }
        return Collections.emptyList();
    }

    private OwnerAuthorizationReadResult requestResolve(String accessToken, String ownerSourceId,
                                                        String ownerSubjectId, Long targetApplicationId,
                                                        String clientId) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put(SimpleIamAkskCollaborationConstant.FIELD_OWNER_SOURCE_ID, ownerSourceId);
        request.put(SimpleIamAkskCollaborationConstant.FIELD_OWNER_SUBJECT_ID, ownerSubjectId);
        request.put(SimpleIamAkskCollaborationConstant.FIELD_TARGET_APPLICATION_ID, targetApplicationId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(resolveUri(), HttpMethod.POST,
                new HttpEntity<Map<String, Object>>(request, headers), OBJECT_RESPONSE_TYPE);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return OwnerAuthorizationReadResult.inactive();
        }
        return parseResolveResponse(response.getBody(), ownerSubjectId, targetApplicationId, clientId);
    }

    private List<OwnerAuthorizationCandidate> requestCandidates(String accessToken,
                                                                OwnerAuthorizationKey owner) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put(SimpleIamAkskCollaborationConstant.FIELD_OWNER_SOURCE_ID, owner.getOwnerSourceId());
        request.put(SimpleIamAkskCollaborationConstant.FIELD_OWNER_SUBJECT_ID, owner.getOwnerSubjectId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(candidateUri(), HttpMethod.POST,
                new HttpEntity<Map<String, Object>>(request, headers), OBJECT_LIST_RESPONSE_TYPE);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return Collections.emptyList();
        }
        List<OwnerAuthorizationCandidate> candidates = new ArrayList<OwnerAuthorizationCandidate>();
        for (Map<String, Object> candidate : response.getBody()) {
            Object applicationId = candidate.get(SimpleIamAkskCollaborationConstant.FIELD_APPLICATION_ID);
            Object applicationName = candidate.get(SimpleIamAkskCollaborationConstant.FIELD_APPLICATION_NAME);
            Object applicationCode = candidate.get(SimpleIamAkskCollaborationConstant.FIELD_APPLICATION_CODE_SNAPSHOT);
            if (!(applicationId instanceof Number) || ((Number) applicationId).longValue() <= 0L
                    || !(applicationName instanceof String) || !(applicationCode instanceof String)) {
                return Collections.emptyList();
            }
            candidates.add(new OwnerAuthorizationCandidate(Long.valueOf(((Number) applicationId).longValue()),
                    (String) applicationName, (String) applicationCode));
        }
        return Collections.unmodifiableList(candidates);
    }

    /**
     * 拉取 afterSequence 之后的连续 IAM 授权最终态日志。
     *
     * @param afterSequence 上次消费位点
     * @param pageSize      页大小
     * @return 有序变更页；不可用时返回不可用页
     */
    @Override
    public OwnerAuthorizationChangePage pullChanges(Long afterSequence, int pageSize) {
        if (!isEnabled() || !isEndpointConfigurationValid() || afterSequence == null || afterSequence.longValue() < 0L) {
            return OwnerAuthorizationChangePage.unavailable();
        }
        if (pageSize < SimpleIamAkskCollaborationConstant.MIN_PULL_PAGE_SIZE
                || pageSize > SimpleIamAkskCollaborationConstant.MAX_PULL_PAGE_SIZE) {
            log.warn("变更拉取页大小越出契约区间，按失败关闭：pageSize={}", pageSize);
            return OwnerAuthorizationChangePage.unavailable();
        }
        int maxAttempts = properties.getMaxAttempts().intValue();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String accessToken = null;
            try {
                accessToken = obtainServiceAccessToken(SimpleIamAkskCollaborationConstant.SCOPE_STREAM);
                if (StringUtils.hasText(accessToken)) {
                    return requestChanges(accessToken, afterSequence, pageSize);
                }
            } catch (RuntimeException exception) {
                invalidateServiceAccessToken(SimpleIamAkskCollaborationConstant.SCOPE_STREAM, accessToken, exception);
                if (attempt == maxAttempts) {
                    log.warn("IAM owner 授权日志拉取失败，保留现有本地租约：category={}",
                            exception.getClass().getSimpleName());
                }
            }
        }
        return OwnerAuthorizationChangePage.unavailable();
    }

    private String obtainServiceAccessToken(String scope) {
        Instant now = Instant.now();
        ServiceAccessToken cached = serviceAccessTokens.get(scope);
        if (cached != null && cached.isUsableAt(now)) {
            return cached.getValue();
        }
        synchronized (serviceAccessTokens) {
            cached = serviceAccessTokens.get(scope);
            now = Instant.now();
            if (cached != null && cached.isUsableAt(now)) {
                return cached.getValue();
            }
            return requestServiceAccessToken(scope, now);
        }
    }

    private String requestServiceAccessToken(String scope, Instant requestedAt) {
        SimpleIamAkskCollaborationProperties config = properties;
        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
        form.add(SimpleIamAkskCollaborationConstant.FIELD_GRANT_TYPE,
                SimpleIamAkskCollaborationConstant.GRANT_TYPE_CLIENT_CREDENTIALS);
        form.add(SimpleIamAkskCollaborationConstant.FIELD_SCOPE, scope);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(config.getClientId(), config.getClientSecret());
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(URI.create(config.getTokenUri()), HttpMethod.POST,
                new HttpEntity<MultiValueMap<String, String>>(form, headers), OBJECT_RESPONSE_TYPE);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return null;
        }
        Object accessToken = response.getBody().get(SimpleIamAkskCollaborationConstant.FIELD_ACCESS_TOKEN);
        if (!(accessToken instanceof String) || !StringUtils.hasText((String) accessToken)) {
            return null;
        }
        Object expiresIn = response.getBody().get(SimpleIamAkskCollaborationConstant.FIELD_EXPIRES_IN);
        if (expiresIn instanceof Number && ((Number) expiresIn).longValue() > TOKEN_REFRESH_SKEW_SECONDS) {
            Instant refreshAt = requestedAt.plusSeconds(((Number) expiresIn).longValue() - TOKEN_REFRESH_SKEW_SECONDS);
            serviceAccessTokens.put(scope, new ServiceAccessToken((String) accessToken, refreshAt));
        }
        return (String) accessToken;
    }

    private OwnerAuthorizationReadResult parseResolveResponse(Map<String, Object> response, String ownerSubjectId,
                                                              Long targetApplicationId, String clientId) {
        if (!Boolean.TRUE.equals(response.get(SimpleIamAkskCollaborationConstant.FIELD_ACTIVE))) {
            return inactiveWithResumeSequence(response.get(
                    SimpleIamAkskCollaborationConstant.FIELD_RESUME_AFTER_SEQUENCE));
        }
        Object ownerEpoch = response.get(SimpleIamAkskCollaborationConstant.FIELD_OWNER_SECURITY_EPOCH);
        Object applicationEpoch = response.get(SimpleIamAkskCollaborationConstant.FIELD_APPLICATION_AUTHORIZATION_EPOCH);
        Object ownerInheritedEpoch = response.get(SimpleIamAkskCollaborationConstant.FIELD_OWNER_INHERITED_ACCESS_EPOCH);
        Object projectionEpoch = response.get(SimpleIamAkskCollaborationConstant.FIELD_PROJECTION_ACCESS_EPOCH);
        Object resumeAfterSequence = response.get(SimpleIamAkskCollaborationConstant.FIELD_RESUME_AFTER_SEQUENCE);
        Object ownerUsername = response.get(SimpleIamAkskCollaborationConstant.FIELD_OWNER_USERNAME);
        if (!(ownerEpoch instanceof Number) || !(applicationEpoch instanceof Number)
                || !(ownerInheritedEpoch instanceof Number) || !(projectionEpoch instanceof Number)
                || !(resumeAfterSequence instanceof Number)
                || !(ownerUsername instanceof String) || !StringUtils.hasText((String) ownerUsername)
                || ((Number) ownerEpoch).longValue() <= 0L || ((Number) applicationEpoch).longValue() <= 0L
                || ((Number) ownerInheritedEpoch).longValue() <= 0L
                || ((Number) projectionEpoch).longValue() <= 0L
                || ((Number) resumeAfterSequence).longValue() < 0L) {
            return OwnerAuthorizationReadResult.inactive();
        }
        try {
            Map<String, Object> authorizationClaim = asStringObjectMap(
                    response.get(SimpleIamAkskCollaborationConstant.FIELD_AUTHORIZATION));
            if (authorizationClaim == null) {
                return OwnerAuthorizationReadResult.inactive();
            }
            ApplicationAuthorizationContext authorization = ApplicationAuthorizationContextClaimMapper
                    .fromClaim(authorizationClaim);
            if (authorization.getSubjectType() != ApplicationAuthorizationSubjectType.HUMAN
                    || !ownerSubjectId.equals(authorization.getSubjectId())
                    || !authorization.isAdmitted()) {
                return OwnerAuthorizationReadResult.inactive();
            }
            return new OwnerAuthorizationReadResult(true,
                    Long.valueOf(((Number) ownerEpoch).longValue()),
                    Long.valueOf(((Number) applicationEpoch).longValue()),
                    Long.valueOf(((Number) ownerInheritedEpoch).longValue()),
                    Long.valueOf(((Number) projectionEpoch).longValue()),
                    Long.valueOf(((Number) resumeAfterSequence).longValue()), (String) ownerUsername,
                    authorizationClaim);
        } catch (RuntimeException exception) {
            log.warn("IAM owner reader 响应不满足授权契约，AKU 按失败关闭：clientId={}, applicationId={}",
                    clientId, targetApplicationId);
            return OwnerAuthorizationReadResult.inactive();
        }
    }

    private boolean isEnabled() {
        return Boolean.TRUE.equals(serverProperties.getOwnerAuthorization().getEnabled())
                && Boolean.TRUE.equals(properties.getEnabled());
    }

    private boolean isEndpointConfigurationValid() {
        SimpleIamAkskCollaborationProperties config = properties;
        return StringUtils.hasText(serverProperties.getOwnerAuthorization().getOwnerSourceId())
                && StringUtils.hasText(config.getClientId())
                && StringUtils.hasText(config.getClientSecret())
                && isSupportedEndpointUri(config.getTokenUri()) && isSupportedEndpointUri(config.getBaseUri())
                && positive(config.getConnectTimeoutMillis()) && positive(config.getReadTimeoutMillis())
                && positive(config.getMaxAttempts());
    }

    private URI resolveUri() {
        return endpointUri(SimpleIamAkskCollaborationConstant.RESOLVE_PATH);
    }

    private URI candidateUri() {
        return endpointUri(SimpleIamAkskCollaborationConstant.CANDIDATE_PATH);
    }

    private OwnerAuthorizationChangePage requestChanges(String accessToken, Long afterSequence, int pageSize) {
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put(SimpleIamAkskCollaborationConstant.FIELD_AFTER_SEQUENCE, afterSequence);
        request.put(SimpleIamAkskCollaborationConstant.FIELD_PAGE_SIZE, Integer.valueOf(pageSize));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(changePullUri(), HttpMethod.POST,
                new HttpEntity<Map<String, Object>>(request, headers), OBJECT_RESPONSE_TYPE);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return OwnerAuthorizationChangePage.unavailable();
        }
        return parseChangePullResponse(response.getBody(), afterSequence);
    }

    private URI changePullUri() {
        return endpointUri(SimpleIamAkskCollaborationConstant.CHANGE_PULL_PATH);
    }

    private OwnerAuthorizationChangePage parseChangePullResponse(Map<String, Object> response, Long afterSequence) {
        Object resyncRequired = response.get(SimpleIamAkskCollaborationConstant.FIELD_RESYNC_REQUIRED);
        Object oldest = response.get(SimpleIamAkskCollaborationConstant.FIELD_LOW_WATERMARK);
        Object highWater = response.get(SimpleIamAkskCollaborationConstant.FIELD_HIGH_WATERMARK);
        Object items = response.get(SimpleIamAkskCollaborationConstant.FIELD_CHANGES);
        Instant serverTime = parseServerTime(response.get(SimpleIamAkskCollaborationConstant.FIELD_SERVER_TIME));
        if (!(resyncRequired instanceof Boolean) || !(oldest instanceof Number) || !(highWater instanceof Number)
                || !(items instanceof List) || ((Number) oldest).longValue() < 0L
                || ((Number) highWater).longValue() < ((Number) oldest).longValue() || serverTime == null) {
            return OwnerAuthorizationChangePage.unavailable();
        }
        List<OwnerAuthorizationChange> changes = new ArrayList<OwnerAuthorizationChange>();
        long previous = afterSequence.longValue();
        for (Object item : (List) items) {
            Map<String, Object> value = asStringObjectMap(item);
            if (value == null) {
                return OwnerAuthorizationChangePage.unavailable();
            }
            Object sourceSequence = value.get(SimpleIamAkskCollaborationConstant.FIELD_SOURCE_SEQUENCE);
            Object eventId = value.get(SimpleIamAkskCollaborationConstant.FIELD_EVENT_ID);
            Object changeType = value.get(SimpleIamAkskCollaborationConstant.FIELD_CHANGE_TYPE);
            Map<String, Object> payload = asStringObjectMap(
                    value.get(SimpleIamAkskCollaborationConstant.FIELD_PAYLOAD));
            if (!(sourceSequence instanceof Number) || ((Number) sourceSequence).longValue() <= previous
                    || !(eventId instanceof String) || !(changeType instanceof String) || payload == null) {
                return OwnerAuthorizationChangePage.unavailable();
            }
            previous = ((Number) sourceSequence).longValue();
            changes.add(new OwnerAuthorizationChange(Long.valueOf(previous), (String) eventId,
                    (String) changeType, payload));
        }
        return new OwnerAuthorizationChangePage(true, ((Boolean) resyncRequired).booleanValue(),
                Collections.unmodifiableList(changes), Long.valueOf(((Number) oldest).longValue()),
                Long.valueOf(((Number) highWater).longValue()), serverTime);
    }

    private URI endpointUri(String path) {
        String baseUri = properties.getBaseUri();
        String separator = SimpleIamAkskCollaborationConstant.URI_PATH_SEPARATOR;
        String normalized = baseUri.endsWith(separator)
                ? baseUri.substring(0, baseUri.length() - separator.length()) : baseUri;
        return URI.create(normalized + path);
    }

    /**
     * 端点 URI 校验：http 与 https 均受支持，由部署方按网络形态选择；其余 scheme 或非法 URI 失败关闭。
     *
     * @param value 端点地址
     * @return true 表示 scheme 受支持且 URI 合法
     */
    private boolean isSupportedEndpointUri(String value) {
        try {
            if (!StringUtils.hasText(value)) {
                return false;
            }
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            boolean supportedScheme = SimpleIamAkskCollaborationConstant.HTTP_SCHEME.equalsIgnoreCase(scheme)
                    || SimpleIamAkskCollaborationConstant.HTTPS_SCHEME.equalsIgnoreCase(scheme);
            return supportedScheme && StringUtils.hasText(uri.getHost())
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean positive(Integer value) {
        return value != null && value.intValue() > 0;
    }

    private static int timeoutOrDefault(Integer value, int defaultValue) {
        return positive(value) ? value.intValue() : defaultValue;
    }

    private boolean hasValidOwnerIdentity(OwnerAuthorizationKey owner) {
        return owner != null && hasValidOwnerIdentity(owner.getOwnerSourceId(), owner.getOwnerSubjectId());
    }

    private boolean hasValidOwnerIdentity(String ownerSourceId, String ownerSubjectId) {
        String configuredOwnerSourceId = serverProperties.getOwnerAuthorization().getOwnerSourceId();
        return StringUtils.hasText(ownerSourceId) && StringUtils.hasText(ownerSubjectId)
                && ownerSourceId.length() <= SimpleIamAkskCollaborationConstant.OWNER_SOURCE_ID_MAX_LENGTH
                && ownerSubjectId.length() <= SimpleIamAkskCollaborationConstant.OWNER_SUBJECT_ID_MAX_LENGTH
                && StringUtils.hasText(configuredOwnerSourceId)
                && configuredOwnerSourceId.equals(ownerSourceId);
    }

    private OwnerAuthorizationReadResult inactiveWithResumeSequence(Object resumeAfterSequence) {
        if (resumeAfterSequence instanceof Number
                && ((Number) resumeAfterSequence).longValue() >= 0L) {
            return OwnerAuthorizationReadResult.inactive(
                    Long.valueOf(((Number) resumeAfterSequence).longValue()));
        }
        return OwnerAuthorizationReadResult.inactive();
    }

    private Instant parseServerTime(Object value) {
        if (!(value instanceof String) || !StringUtils.hasText((String) value)) {
            return null;
        }
        try {
            return Instant.parse((String) value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Map<String, Object> asStringObjectMap(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        Map<?, ?> source = (Map<?, ?>) value;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                return null;
            }
            result.put((String) entry.getKey(), entry.getValue());
        }
        return result;
    }

    private void invalidateServiceAccessToken(String scope, String accessToken, RuntimeException exception) {
        if (!StringUtils.hasText(accessToken) || !(exception instanceof HttpClientErrorException)
                || !HttpStatus.UNAUTHORIZED.equals(((HttpClientErrorException) exception).getStatusCode())) {
            return;
        }
        ServiceAccessToken cached = serviceAccessTokens.get(scope);
        if (cached != null && accessToken.equals(cached.getValue())) {
            serviceAccessTokens.remove(scope, cached);
        }
    }

    /**
     * 只保存 reader service token 与提前刷新时刻，三权投影始终按独立链路处理。
     */
    private static final class ServiceAccessToken {

        private final String value;
        private final Instant refreshAt;

        private ServiceAccessToken(String value, Instant refreshAt) {
            this.value = value;
            this.refreshAt = refreshAt;
        }

        private String getValue() {
            return value;
        }

        private boolean isUsableAt(Instant now) {
            return now.isBefore(refreshAt);
        }
    }
}

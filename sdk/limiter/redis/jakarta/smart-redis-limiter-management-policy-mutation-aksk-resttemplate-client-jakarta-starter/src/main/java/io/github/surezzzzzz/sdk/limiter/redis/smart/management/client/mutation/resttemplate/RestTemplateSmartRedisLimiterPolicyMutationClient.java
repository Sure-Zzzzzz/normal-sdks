package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.SmartRedisLimiterPolicyMutationClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyMutationResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyRuleView;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model.PolicyUpsertCommand;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate.annotation.SmartRedisLimiterPolicyMutationClientRestTemplateComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate.configuration.SmartRedisLimiterPolicyMutationClientProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.support.PolicyMutationJsonCodec;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.support.TypedRuleWire;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 RestTemplate 的管理面策略变更客户端
 *
 * <p>传输与认证全部复用 AKSK 底座 {@code akskClientRestTemplate}（连接池、超时、令牌头）；
 * 本类只拼 v2 规则端点与 JSON 编解码。全部状态交由客户端统一分类：
 * 2xx 解析契约，409 语义化为 {@link PolicyMutationConflictException}（供编排层重读重试），
 * 其余 4xx/5xx 按管理端错误体原样透传为异常。</p>
 *
 * @author surezzzzzz
 */
@SmartRedisLimiterPolicyMutationClientRestTemplateComponent
public class RestTemplateSmartRedisLimiterPolicyMutationClient implements SmartRedisLimiterPolicyMutationClient {

    private static final int HTTP_OK = 200;
    private static final int HTTP_CREATED = 201;
    private static final int HTTP_CONFLICT = 409;
    private static final String RULES_PATH = "/v2/policy/rules";
    private static final String RULE_PATH = "/v2/policy/rule";

    private final SmartRedisLimiterPolicyMutationClientProperties properties;
    private final PolicyMutationJsonCodec jsonCodec;
    private final RestTemplate restTemplate;

    public RestTemplateSmartRedisLimiterPolicyMutationClient(
            SmartRedisLimiterPolicyMutationClientProperties properties,
            PolicyMutationJsonCodec jsonCodec,
            @Qualifier("akskClientRestTemplate") RestTemplate akskClientRestTemplate) {
        this.properties = properties;
        this.jsonCodec = jsonCodec;
        this.restTemplate = akskClientRestTemplate;
        this.restTemplate.setErrorHandler(new PassthroughResponseErrorHandler());
    }

    @Override
    public PolicyRuleView findByKey(PolicyUpsertCommand key) {
        // 七字段全过滤（含 namespace/customType），并翻页直到取完——分页大小只影响单次请求，
        // 不能因目标规则落在后续页而漏检。终止依据是服务端回传的实际页大小（宿主可配
        // max-size 钳制请求值，客户端不能假设请求多少就返回多少）
        List<TypedRuleWire> matched = new ArrayList<>();
        int page = 1;
        int pageLimit = 100;
        while (page <= pageLimit) {
            URI uri = UriComponentsBuilder
                    .fromUriString(requireBaseUrl() + RULES_PATH)
                    .queryParam("serviceCode", key.getServiceCode())
                    .queryParam("resourceCode", key.getResourceCode())
                    .queryParam("dimension", key.getDimension())
                    .queryParam("selector", key.getSelector())
                    .queryParam("namespace", key.getNamespace() == null ? "" : key.getNamespace())
                    .queryParam("customType", key.getCustomType() == null ? "" : key.getCustomType())
                    .queryParam("objectId", key.getObjectId() == null ? "" : key.getObjectId())
                    .queryParam("page", page)
                    .queryParam("size", 100)
                    .build()
                    .encode()
                    .toUri();
            TypedRuleWire.PageWire result = exchange(uri, HttpMethod.GET, null, null, PolicyMutationOutcome.PAGE);
            List<TypedRuleWire> items = result.getItems() == null
                    ? java.util.Collections.<TypedRuleWire>emptyList() : result.getItems();
            for (TypedRuleWire item : items) {
                if (sameIdentity(item, key)) {
                    matched.add(item);
                }
            }
            long total = result.getTotal() == null ? items.size() : result.getTotal();
            long actualSize = result.getSize() == null ? items.size() : result.getSize();
            // 终止：本页不满（无下一页）或已覆盖 total；页数上限防御异常大的 total
            if (items.isEmpty() || actualSize <= 0 || items.size() < actualSize
                    || (long) page * actualSize >= total) {
                break;
            }
            page++;
        }
        if (matched.isEmpty()) {
            return null;
        }
        if (matched.size() > 1) {
            throw new SmartRedisLimiterException(ErrorCode.POLICY_DUPLICATE_KEY,
                    "完整策略键命中多条规则（" + matched.size() + " 条），拒绝盲改，请检查管理端数据");
        }
        return toView(matched.get(0));
    }

    @Override
    public PolicyRuleView findByIdentityRow(long ruleId) {
        URI uri = UriComponentsBuilder
                .fromUriString(requireBaseUrl() + RULE_PATH + "/" + ruleId)
                .build()
                .encode()
                .toUri();
        TypedRuleWire wire = exchange(uri, HttpMethod.GET, null, null, PolicyMutationOutcome.RULE);
        return wire == null ? null : toView(wire);
    }

    @Override
    public PolicyMutationResult create(PolicyUpsertCommand command) {
        URI uri = UriComponentsBuilder
                .fromUriString(requireBaseUrl() + RULE_PATH)
                .build()
                .encode()
                .toUri();
        String body = jsonCodec.encode(createBody(command));
        TypedRuleWire.MutationWire wire = exchange(uri, HttpMethod.POST,
                body, "application/json", PolicyMutationOutcome.MUTATION_CREATED);
        return toResult(wire);
    }

    @Override
    public PolicyMutationResult updateLimits(long ruleId, long expectedRowVersion, List<PolicyLimit> limits) {
        URI uri = ruleUri(ruleId);
        String body = jsonCodec.encode(new CasLimitsBody(expectedRowVersion, limits));
        TypedRuleWire.MutationWire wire = exchange(uri, HttpMethod.PUT,
                body, "application/json", PolicyMutationOutcome.MUTATION_OK);
        return toResult(wire);
    }

    @Override
    public PolicyMutationResult updateEnabled(long ruleId, long expectedRowVersion, boolean enabled) {
        URI uri = ruleUri(ruleId);
        String body = jsonCodec.encode(new CasEnabledBody(enabled, expectedRowVersion));
        TypedRuleWire.MutationWire wire = exchange(uri, HttpMethod.PATCH,
                body, "application/json", PolicyMutationOutcome.MUTATION_OK);
        return toResult(wire);
    }

    @Override
    public PolicyMutationResult delete(long ruleId, long expectedRowVersion) {
        URI uri = UriComponentsBuilder
                .fromUriString(requireBaseUrl() + RULE_PATH + "/" + ruleId)
                .queryParam("expectedRowVersion", expectedRowVersion)
                .build()
                .encode()
                .toUri();
        TypedRuleWire.MutationWire wire = exchange(uri, HttpMethod.DELETE,
                null, null, PolicyMutationOutcome.MUTATION_OK);
        return toResult(wire);
    }

    private enum PolicyMutationOutcome {
        PAGE, RULE, MUTATION_CREATED, MUTATION_OK
    }

    private <T> T exchange(URI uri, HttpMethod method, String body, String contentType,
                           PolicyMutationOutcome outcome) {
        try {
            Object result = restTemplate.execute(uri, method, request -> {
                if (body != null) {
                    request.getHeaders().setContentType(
                            MediaType.parseMediaType(contentType));
                    request.getBody().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }, response -> read(response, outcome));
            @SuppressWarnings("unchecked")
            T cast = (T) result;
            return cast;
        } catch (PolicyMutationConflictException ex) {
            throw ex;
        } catch (SmartRedisLimiterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID, ex.getMessage()),
                    ex);
        }
    }

    private Object read(ClientHttpResponse response, PolicyMutationOutcome outcome) throws IOException {
        int status = response.getRawStatusCode();
        if (status == HTTP_CONFLICT) {
            throw new PolicyMutationConflictException("管理端返回 409：身份重复或行版本过期");
        }
        if (status == 404 && outcome == PolicyMutationOutcome.RULE) {
            // 单规则回读 404 = 规则不存在：返回 null 供编排层判定（upsert 竞态删除后的
            // 正常语义）；其余端点 404（路径错/范围外主键）仍按非预期状态上抛
            return null;
        }
        boolean created = outcome == PolicyMutationOutcome.MUTATION_CREATED;
        int expected = created ? HTTP_CREATED : HTTP_OK;
        if (status != expected) {
            throw new SmartRedisLimiterException(
                    ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID,
                            "管理端返回非预期状态码 " + status + "，期望 " + expected));
        }
        InputStream body = limited(response.getBody());
        switch (outcome) {
            case PAGE:
                return jsonCodec.decodePage(body);
            case RULE:
                return jsonCodec.decodeRule(body);
            default:
                return jsonCodec.decodeMutation(body);
        }
    }

    private PolicyMutationResult toResult(TypedRuleWire.MutationWire wire) {
        if (wire.getRule() == null || wire.getRule().getId() == null) {
            throw new SmartRedisLimiterException(ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID, "变更响应缺 rule"));
        }
        TypedRuleWire rule = wire.getRule();
        return new PolicyMutationResult(rule.getId(), rule.getRowVersion() == null ? 0L : rule.getRowVersion(),
                wire.getRevision() == null ? PolicyMutationResult.NO_REVISION : wire.getRevision(),
                Boolean.TRUE.equals(rule.getEnabled()));
    }

    private PolicyRuleView toView(TypedRuleWire rule) {
        List<PolicyLimit> limits = new ArrayList<>();
        if (rule.getLimits() != null) {
            for (TypedRuleWire.LimitWire limit : rule.getLimits()) {
                limits.add(new PolicyLimit(limit.getCount(), limit.getWindow(), limit.getUnit()));
            }
        }
        return new PolicyRuleView(rule.getId(), rule.getRowVersion() == null ? 0L : rule.getRowVersion(),
                rule.getServiceCode(), rule.getResourceCode(), rule.getDimension(), rule.getSelector(),
                rule.getNamespace(), rule.getCustomType(), rule.getObjectId(),
                Boolean.TRUE.equals(rule.getEnabled()), limits);
    }

    private boolean sameIdentity(TypedRuleWire item, PolicyUpsertCommand key) {
        return equals(item.getServiceCode(), key.getServiceCode())
                && equals(item.getResourceCode(), key.getResourceCode())
                && equals(item.getDimension(), key.getDimension())
                && equals(item.getSelector(), key.getSelector())
                && equals(item.getNamespace(), key.getNamespace())
                && equals(item.getCustomType(), key.getCustomType())
                && equals(item.getObjectId(), key.getObjectId() == null ? "" : key.getObjectId());
    }

    private boolean equals(String left, String right) {
        return left == null ? right == null || right.isEmpty() : left.equals(right);
    }

    private java.util.Map<String, Object> createBody(PolicyUpsertCommand command) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("serviceCode", command.getServiceCode());
        body.put("resourceCode", command.getResourceCode());
        body.put("dimension", command.getDimension());
        body.put("selector", command.getSelector());
        body.put("namespace", command.getNamespace());
        if (command.getCustomType() != null) {
            body.put("customType", command.getCustomType());
        }
        if (command.getObjectId() != null) {
            body.put("objectId", command.getObjectId());
        }
        body.put("enabled", command.isEnabled());
        body.put("limits", command.getLimits());
        return body;
    }

    private URI ruleUri(long ruleId) {
        return UriComponentsBuilder
                .fromUriString(requireBaseUrl() + RULE_PATH + "/" + ruleId)
                .build()
                .encode()
                .toUri();
    }

    private String requireBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new SmartRedisLimiterException(ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    "policy-mutation 客户端 base-url 未配置");
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private InputStream limited(InputStream body) {
        return new LimitedInputStream(body, properties.getMaxResponseBytes());
    }

    /**
     * CAS 限额更新请求体：{ expectedRowVersion, limits }
     */
    private static final class CasLimitsBody {
        @SuppressWarnings("unused")
        private final long expectedRowVersion;
        @SuppressWarnings("unused")
        private final List<PolicyLimit> limits;

        private CasLimitsBody(long expectedRowVersion, List<PolicyLimit> limits) {
            this.expectedRowVersion = expectedRowVersion;
            this.limits = limits;
        }

        public long getExpectedRowVersion() {
            return expectedRowVersion;
        }

        public List<PolicyLimit> getLimits() {
            return limits;
        }
    }

    /**
     * CAS 启停请求体：{ enabled, expectedRowVersion }
     */
    private static final class CasEnabledBody {
        @SuppressWarnings("unused")
        private final boolean enabled;
        @SuppressWarnings("unused")
        private final long expectedRowVersion;

        private CasEnabledBody(boolean enabled, long expectedRowVersion) {
            this.enabled = enabled;
            this.expectedRowVersion = expectedRowVersion;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public long getExpectedRowVersion() {
            return expectedRowVersion;
        }
    }

    /**
     * 将全部 HTTP 状态交给客户端统一分类
     */
    private static final class PassthroughResponseErrorHandler implements ResponseErrorHandler {

        @Override
        public boolean hasError(ClientHttpResponse response) {
            return false;
        }

        @Override
        public void handleError(ClientHttpResponse response) {
            // 不提前抛异常，由响应提取器统一处理状态码。
        }
    }

    /**
     * 读取过程中强制限制字节数的输入流
     */
    private static final class LimitedInputStream extends java.io.FilterInputStream {

        private final long maxBytes;
        private long consumed;

        private LimitedInputStream(InputStream inputStream, long maxBytes) {
            super(inputStream);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                increaseConsumed(1L);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                increaseConsumed(read);
            }
            return read;
        }

        private void increaseConsumed(long count) throws IOException {
            consumed += count;
            if (consumed > maxBytes) {
                throw new IOException("响应超过最大字节数上限 " + maxBytes);
            }
        }
    }
}

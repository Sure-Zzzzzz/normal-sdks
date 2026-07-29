package io.github.surezzzzzz.sdk.kms.client.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.client.exception.KmsProtocolException;
import io.github.surezzzzzz.sdk.kms.client.model.*;
import io.github.surezzzzzz.sdk.kms.client.support.KmsClientUriHelper;
import io.github.surezzzzzz.sdk.kms.client.support.KmsHttpExecutor;
import io.github.surezzzzzz.sdk.kms.client.support.KmsValidationHelper;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 基于专属 RestTemplate 的 KMS HTTP 客户端。
 *
 * <p>严格按 KMS 协议进行路径段编码、UTC 毫秒时间转换和无填充 Base64url 编解码；任何响应形状、
 * 枚举值或编码不符合契约时均以协议异常失败，绝不尝试猜测或兼容不明确的数据。</p>
 *
 * @author surezzzzzz
 */
public class RestTemplateKmsClient implements KmsClient {

    /**
     * KMS 线上的唯一时间格式，出站时间截断到毫秒，入站时间必须严格匹配。
     */
    private static final DateTimeFormatter UTC_MILLIS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final String UTC_MILLIS_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";
    private static final Set<String> KEY_PURPOSES = values(
            SimpleKmsClientConstant.PURPOSE_SIGN,
            SimpleKmsClientConstant.PURPOSE_ENCRYPT);
    private static final Set<String> KEY_ALGORITHMS = values(
            SimpleKmsClientConstant.ALGORITHM_ES256,
            SimpleKmsClientConstant.ALGORITHM_AES_256_GCM);
    private static final Set<String> KEY_STATES = values(
            SimpleKmsClientConstant.STATE_ACTIVE,
            SimpleKmsClientConstant.STATE_DISABLED,
            SimpleKmsClientConstant.STATE_PENDING_DESTRUCTION,
            SimpleKmsClientConstant.STATE_DESTROYED);
    private static final Set<String> KEY_VERSION_STATES = values(
            SimpleKmsClientConstant.STATE_ACTIVE,
            SimpleKmsClientConstant.STATE_RETIRED,
            SimpleKmsClientConstant.STATE_PENDING_DESTRUCTION,
            SimpleKmsClientConstant.STATE_DESTROYED);
    private static final Set<String> OPERATIONS = values(
            SimpleKmsClientConstant.PURPOSE_SIGN,
            SimpleKmsClientConstant.OPERATION_VERIFY,
            SimpleKmsClientConstant.PURPOSE_ENCRYPT,
            SimpleKmsClientConstant.OPERATION_DECRYPT,
            SimpleKmsClientConstant.OPERATION_READ_PUBLIC_KEY,
            SimpleKmsClientConstant.OPERATION_CREATE_KEY,
            SimpleKmsClientConstant.OPERATION_ROTATE_KEY,
            SimpleKmsClientConstant.OPERATION_CHANGE_KEY_STATE,
            SimpleKmsClientConstant.OPERATION_SCHEDULE_KEY_DESTRUCTION,
            SimpleKmsClientConstant.OPERATION_CANCEL_KEY_DESTRUCTION,
            SimpleKmsClientConstant.OPERATION_CREATE_KEY_POLICY,
            SimpleKmsClientConstant.OPERATION_REVOKE_KEY_POLICY,
            SimpleKmsClientConstant.OPERATION_PROCESS_KEY_DESTRUCTION);

    private final URI baseUri;
    private final KmsHttpExecutor executor;

    /**
     * 创建 HTTP 客户端。
     *
     * @param baseUri  固定 KMS API 根地址
     * @param executor 限长 HTTP 执行器
     */
    public RestTemplateKmsClient(URI baseUri, KmsHttpExecutor executor) {
        this.baseUri = KmsClientUriHelper.fixedApiBaseUri(baseUri);
        this.executor = KmsValidationHelper.requireValue(executor);
    }

    private static URI uri(UriComponentsBuilder builder) {
        return builder.build().encode().toUri();
    }

    private static void query(UriComponentsBuilder builder, String name, Object value) {
        if (value != null) {
            builder.queryParam(name, value);
        }
    }

    private static void optional(Map<String, Object> body, String name, Object value) {
        if (value != null) {
            body.put(name, value);
        }
    }

    private static Map<String, String> idempotency(String value) {
        return Collections.singletonMap(SimpleKmsClientConstant.HEADER_IDEMPOTENCY_KEY, text(value));
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    private static String text(String value) {
        return KmsValidationHelper.requireText(value);
    }

    private static <T> T value(T value) {
        return KmsValidationHelper.requireValue(value);
    }

    private static String utcMillis(Instant value) {
        return UTC_MILLIS.format(value(value).truncatedTo(ChronoUnit.MILLIS));
    }

    /**
     * 发送无填充 Base64url，复制调用方数组以避免编码过程受外部并发修改影响。
     */
    private static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value(value).clone());
    }

    private static String text(JsonNode node, String field) {
        if (!node.path(field).isTextual()) {
            throw protocol();
        }
        return node.path(field).textValue();
    }

    private static Integer integer(JsonNode node, String field) {
        if (!node.path(field).canConvertToInt()) {
            throw protocol();
        }
        return Integer.valueOf(node.path(field).intValue());
    }

    private static Long longValue(JsonNode node, String field) {
        if (!node.path(field).canConvertToLong()) {
            throw protocol();
        }
        return Long.valueOf(node.path(field).longValue());
    }

    private static Instant utcMillis(JsonNode node, String field) {
        String value = text(node, field);
        if (!value.matches(UTC_MILLIS_PATTERN)) {
            throw protocol();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw protocol();
        }
    }

    private static JsonNode array(JsonNode node, String field) {
        if (!node.path(field).isArray()) {
            throw protocol();
        }
        return node.path(field);
    }

    /**
     * 仅接受无填充 Base64url 响应，拒绝填充字符以防服务端协议漂移被静默接受。
     */
    private static byte[] base64(JsonNode node, String field) {
        String encoded = text(node, field);
        if (encoded.indexOf('=') >= 0) {
            throw protocol();
        }
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (RuntimeException exception) {
            throw protocol();
        }
    }

    private static KmsKey key(JsonNode node) {
        return KmsKey.builder()
                .keyRef(text(node, SimpleKmsClientConstant.FIELD_KEY_REF))
                .keyAlias(text(node, SimpleKmsClientConstant.FIELD_KEY_ALIAS))
                .purpose(enumValue(node, SimpleKmsClientConstant.FIELD_PURPOSE, KEY_PURPOSES))
                .algorithm(enumValue(node, SimpleKmsClientConstant.FIELD_ALGORITHM, KEY_ALGORITHMS))
                .state(enumValue(node, SimpleKmsClientConstant.FIELD_STATE, KEY_STATES))
                .activeVersion(integer(node, SimpleKmsClientConstant.FIELD_ACTIVE_VERSION))
                .rowVersion(longValue(node, SimpleKmsClientConstant.FIELD_ROW_VERSION))
                .createdAt(utcMillis(node, SimpleKmsClientConstant.FIELD_CREATED_AT))
                .updatedAt(utcMillis(node, SimpleKmsClientConstant.FIELD_UPDATED_AT))
                .build();
    }

    private static KmsPolicy policy(JsonNode node) {
        return KmsPolicy.builder()
                .policyId(text(node, SimpleKmsClientConstant.FIELD_POLICY_ID))
                .keyRef(text(node, SimpleKmsClientConstant.FIELD_KEY_REF))
                .principalId(text(node, SimpleKmsClientConstant.FIELD_PRINCIPAL_ID))
                .keyVersion(optionalInteger(node, SimpleKmsClientConstant.FIELD_KEY_VERSION))
                .operation(enumValue(node, SimpleKmsClientConstant.FIELD_OPERATION, OPERATIONS))
                .expiresAt(optionalUtcMillis(node, SimpleKmsClientConstant.FIELD_EXPIRES_AT))
                .rowVersion(longValue(node, SimpleKmsClientConstant.FIELD_ROW_VERSION))
                .build();
    }

    private static Integer optionalInteger(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : integer(node, field);
    }

    private static Instant optionalUtcMillis(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : utcMillis(node, field);
    }

    private static KmsPublicKey publicKey(JsonNode node) {
        String algorithm = enumValue(node, SimpleKmsClientConstant.FIELD_ALGORITHM,
                values(SimpleKmsClientConstant.ALGORITHM_ES256));
        return KmsPublicKey.builder()
                .keyRef(text(node, SimpleKmsClientConstant.FIELD_KEY_REF))
                .version(integer(node, SimpleKmsClientConstant.FIELD_VERSION))
                .algorithm(algorithm)
                .state(enumValue(node, SimpleKmsClientConstant.FIELD_STATE, KEY_VERSION_STATES))
                .publicKey(base64(node, SimpleKmsClientConstant.FIELD_PUBLIC_KEY))
                .build();
    }

    private static String enumValue(JsonNode node, String field, Set<String> allowed) {
        String value = text(node, field);
        if (!allowed.contains(value)) {
            throw protocol();
        }
        return value;
    }

    private static Set<String> values(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(values)));
    }

    private static KmsProtocolException protocol() {
        return new KmsProtocolException(SimpleKmsClientConstant.MESSAGE_PROTOCOL_ERROR);
    }

    @Override
    public KmsKey createKey(String idempotencyKey, String keyAlias, String purpose, String algorithm) {
        Map<String, Object> body = map(
                SimpleKmsClientConstant.FIELD_KEY_ALIAS, text(keyAlias),
                SimpleKmsClientConstant.FIELD_PURPOSE, text(purpose),
                SimpleKmsClientConstant.FIELD_ALGORITHM, text(algorithm));
        return key(execute(SimpleKmsClientConstant.RESOURCE_KEYS, HttpMethod.POST, idempotency(idempotencyKey), body));
    }

    @Override
    public KmsKey getKey(String keyRef) {
        return key(executePath(SimpleKmsClientConstant.RESOURCE_KEYS, keyRef, HttpMethod.GET, null, null));
    }

    /**
     * 键列表使用包含 items 与分页字段的对象响应，区别于公钥列表的直接数组响应。
     */
    @Override
    public KmsKeyPage listKeys(Integer page, Integer size, String alias, String purpose, String algorithm, String state) {
        UriComponentsBuilder builder = path(SimpleKmsClientConstant.RESOURCE_KEYS);
        query(builder, SimpleKmsClientConstant.FIELD_PAGE, page);
        query(builder, SimpleKmsClientConstant.FIELD_SIZE, size);
        query(builder, SimpleKmsClientConstant.QUERY_ALIAS, alias);
        query(builder, SimpleKmsClientConstant.FIELD_PURPOSE, purpose);
        query(builder, SimpleKmsClientConstant.FIELD_ALGORITHM, algorithm);
        query(builder, SimpleKmsClientConstant.FIELD_STATE, state);

        JsonNode node = executor.execute(uri(builder), HttpMethod.GET, null, null);
        List<KmsKey> items = new ArrayList<KmsKey>();
        for (JsonNode item : array(node, SimpleKmsClientConstant.FIELD_ITEMS)) {
            items.add(key(item));
        }
        return KmsKeyPage.builder()
                .items(items)
                .page(integer(node, SimpleKmsClientConstant.FIELD_PAGE))
                .size(integer(node, SimpleKmsClientConstant.FIELD_SIZE))
                .total(longValue(node, SimpleKmsClientConstant.FIELD_TOTAL))
                .build();
    }

    @Override
    public KmsKey changeKeyState(String idempotencyKey, String keyRef, String state, Long expectedRowVersion) {
        return key(executePath(SimpleKmsClientConstant.RESOURCE_KEYS, keyRef, SimpleKmsClientConstant.RESOURCE_STATE,
                HttpMethod.PATCH, idempotency(idempotencyKey), map(
                        SimpleKmsClientConstant.FIELD_STATE, text(state),
                        SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, value(expectedRowVersion))));
    }

    @Override
    public KmsKey rotateKey(String idempotencyKey, String keyRef, Long expectedRowVersion) {
        return key(executePath(SimpleKmsClientConstant.RESOURCE_KEYS, keyRef, SimpleKmsClientConstant.RESOURCE_VERSIONS,
                HttpMethod.POST, idempotency(idempotencyKey), map(
                        SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, value(expectedRowVersion))));
    }

    @Override
    public KmsKey scheduleDestruction(String idempotencyKey, String keyRef, Instant destroyAfter, Long expectedRowVersion) {
        return key(executePath(SimpleKmsClientConstant.RESOURCE_KEYS, keyRef,
                SimpleKmsClientConstant.RESOURCE_DESTRUCTION, HttpMethod.PUT, idempotency(idempotencyKey), map(
                        SimpleKmsClientConstant.FIELD_DESTROY_AFTER, utcMillis(destroyAfter),
                        SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, value(expectedRowVersion))));
    }

    @Override
    public void cancelDestruction(String idempotencyKey, String keyRef, Long expectedRowVersion) {
        executePath(SimpleKmsClientConstant.RESOURCE_KEYS, keyRef, SimpleKmsClientConstant.RESOURCE_DESTRUCTION,
                HttpMethod.DELETE, idempotency(idempotencyKey), map(
                        SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, value(expectedRowVersion)));
    }

    @Override
    public KmsPolicy createPolicy(String idempotencyKey, String keyRef, String principalId, Integer keyVersion,
                                  String operation, Instant expiresAt) {
        Map<String, Object> body = map(
                SimpleKmsClientConstant.FIELD_PRINCIPAL_ID, text(principalId),
                SimpleKmsClientConstant.FIELD_OPERATION, text(operation));
        optional(body, SimpleKmsClientConstant.FIELD_KEY_VERSION, keyVersion);
        optional(body, SimpleKmsClientConstant.FIELD_EXPIRES_AT, expiresAt == null ? null : utcMillis(expiresAt));
        return policy(executePath(SimpleKmsClientConstant.RESOURCE_KEYS, keyRef, SimpleKmsClientConstant.RESOURCE_POLICIES,
                HttpMethod.POST, idempotency(idempotencyKey), body));
    }

    @Override
    public List<KmsPolicy> listPolicies(String keyRef) {
        JsonNode node = executePath(SimpleKmsClientConstant.RESOURCE_KEYS, keyRef,
                SimpleKmsClientConstant.RESOURCE_POLICIES, HttpMethod.GET, null, null);
        List<KmsPolicy> policies = new ArrayList<KmsPolicy>();
        for (JsonNode item : array(node, SimpleKmsClientConstant.FIELD_ITEMS)) {
            policies.add(policy(item));
        }
        return Collections.unmodifiableList(policies);
    }

    @Override
    public void revokePolicy(String idempotencyKey, String keyRef, String policyId, Long expectedRowVersion) {
        executePath(SimpleKmsClientConstant.RESOURCE_KEYS, keyRef, SimpleKmsClientConstant.RESOURCE_POLICIES, policyId,
                HttpMethod.DELETE, idempotency(idempotencyKey), map(
                        SimpleKmsClientConstant.FIELD_EXPECTED_ROW_VERSION, value(expectedRowVersion)));
    }

    @Override
    public KmsSignature sign(String keyRef, Integer version, byte[] signingInput) {
        executor.validateBinaryValues(signingInput);
        Map<String, Object> body = map(
                SimpleKmsClientConstant.FIELD_KEY_REF, text(keyRef),
                SimpleKmsClientConstant.FIELD_INPUT, base64(signingInput));
        optional(body, SimpleKmsClientConstant.FIELD_VERSION, version);

        JsonNode node = execute(SimpleKmsClientConstant.RESOURCE_CRYPTO, SimpleKmsClientConstant.RESOURCE_SIGNATURES,
                HttpMethod.POST, null, body);
        return KmsSignature.builder()
                .keyRef(text(node, SimpleKmsClientConstant.FIELD_KEY_REF))
                .version(integer(node, SimpleKmsClientConstant.FIELD_VERSION))
                .signature(base64(node, SimpleKmsClientConstant.FIELD_SIGNATURE))
                .build();
    }

    @Override
    public boolean verify(String keyRef, Integer version, byte[] signingInput, byte[] signature) {
        executor.validateBinaryValues(signingInput, signature);
        Map<String, Object> body = map(
                SimpleKmsClientConstant.FIELD_KEY_REF, text(keyRef),
                SimpleKmsClientConstant.FIELD_INPUT, base64(signingInput),
                SimpleKmsClientConstant.FIELD_SIGNATURE, base64(signature));
        optional(body, SimpleKmsClientConstant.FIELD_VERSION, version);

        JsonNode node = execute(SimpleKmsClientConstant.RESOURCE_CRYPTO,
                SimpleKmsClientConstant.RESOURCE_VERIFICATIONS, HttpMethod.POST, null, body);
        if (!node.path(SimpleKmsClientConstant.FIELD_VALID).isBoolean()) {
            throw protocol();
        }
        return node.path(SimpleKmsClientConstant.FIELD_VALID).booleanValue();
    }

    @Override
    public byte[] encrypt(String keyRef, byte[] plaintext, byte[] aad) {
        if (aad == null) {
            executor.validateBinaryValues(plaintext);
        } else {
            executor.validateBinaryValues(plaintext, aad);
        }
        Map<String, Object> body = map(
                SimpleKmsClientConstant.FIELD_KEY_REF, text(keyRef),
                SimpleKmsClientConstant.FIELD_PLAINTEXT, base64(plaintext));
        optional(body, SimpleKmsClientConstant.FIELD_AAD, aad == null ? null : base64(aad));
        return base64(execute(SimpleKmsClientConstant.RESOURCE_CRYPTO, SimpleKmsClientConstant.RESOURCE_ENVELOPES,
                HttpMethod.POST, null, body), SimpleKmsClientConstant.FIELD_ENVELOPE);
    }

    @Override
    public byte[] decrypt(byte[] envelope, byte[] aad) {
        if (aad == null) {
            executor.validateBinaryValues(envelope);
        } else {
            executor.validateBinaryValues(envelope, aad);
        }
        Map<String, Object> body = map(SimpleKmsClientConstant.FIELD_ENVELOPE, base64(envelope));
        optional(body, SimpleKmsClientConstant.FIELD_AAD, aad == null ? null : base64(aad));
        return base64(execute(SimpleKmsClientConstant.RESOURCE_CRYPTO, SimpleKmsClientConstant.RESOURCE_DECRYPTIONS,
                HttpMethod.POST, null, body), SimpleKmsClientConstant.FIELD_PLAINTEXT);
    }

    @Override
    public KmsPublicKey readPublicKey(String keyRef, Integer version) {
        UriComponentsBuilder builder = path(SimpleKmsClientConstant.RESOURCE_KEYS, keyRef,
                SimpleKmsClientConstant.RESOURCE_PUBLIC_KEY);
        query(builder, SimpleKmsClientConstant.FIELD_VERSION, version);
        return publicKey(executor.execute(uri(builder), HttpMethod.GET, null, null));
    }

    /**
     * 公钥列表协议直接返回数组，冻结结果集合以与其他 Client 集合返回保持一致。
     */
    @Override
    public List<KmsPublicKey> listPublicKeys(String keyRef) {
        JsonNode node = executePath(SimpleKmsClientConstant.RESOURCE_KEYS, keyRef,
                SimpleKmsClientConstant.RESOURCE_PUBLIC_KEYS, HttpMethod.GET, null, null);
        if (!node.isArray()) {
            throw protocol();
        }
        List<KmsPublicKey> publicKeys = new ArrayList<KmsPublicKey>();
        for (JsonNode item : node) {
            publicKeys.add(publicKey(item));
        }
        return Collections.unmodifiableList(publicKeys);
    }

    private JsonNode execute(String resource, HttpMethod method, Map<String, String> headers, Object body) {
        return executor.execute(uri(path(resource)), method, headers, body);
    }

    private JsonNode execute(String resource, String operation, HttpMethod method, Map<String, String> headers,
                             Object body) {
        return executor.execute(uri(path(resource, operation)), method, headers, body);
    }

    private JsonNode executePath(String resource, String keyRef, HttpMethod method, Map<String, String> headers,
                                 Object body) {
        return executor.execute(uri(path(resource, keyRef)), method, headers, body);
    }

    private JsonNode executePath(String resource, String keyRef, String child, HttpMethod method,
                                 Map<String, String> headers, Object body) {
        return executor.execute(uri(path(resource, keyRef, child)), method, headers, body);
    }

    private JsonNode executePath(String resource, String keyRef, String child, String childId, HttpMethod method,
                                 Map<String, String> headers, Object body) {
        return executor.execute(uri(path(resource, keyRef, child, childId)), method, headers, body);
    }

    /**
     * 将每个资源标识作为独立路径段追加，禁止通过字符串拼接让标识改变固定 API 路径结构。
     */
    private UriComponentsBuilder path(String... segments) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUri(baseUri);
        for (String segment : segments) {
            builder.pathSegment(text(segment));
        }
        return builder;
    }
}

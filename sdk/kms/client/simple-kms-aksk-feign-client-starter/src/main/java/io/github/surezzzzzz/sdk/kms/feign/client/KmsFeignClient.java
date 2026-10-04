package io.github.surezzzzzz.sdk.kms.feign.client;

import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient;
import io.github.surezzzzzz.sdk.kms.client.constant.SimpleKmsClientConstant;
import io.github.surezzzzzz.sdk.kms.feign.client.model.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * KMS 契约的 Feign 接口（认证由 aksk 底座元注解自动挂接）。
 *
 * <p>标注底座 {@code @AkskClientFeignClient}：宿主启用 {@code @EnableFeignClients} 扫描本包后，
 * Authorization 头由底座令牌链自动注入。目标地址取
 * {@code io.github.surezzzzzz.sdk.kms.client.base-url}（仅 origin），API 基路径由 core 契约常量固定。</p>
 *
 * <p>请求体为 Map：可选字段为 null 时请勿放入 Map（与契约省略语义一致）；
 * 响应为 wire DTO，时间字段为契约原文（UTC 毫秒字符串），二进制字段为无填充 Base64url 原文。</p>
 *
 * @author surezzzzzz
 */
@AkskClientFeignClient(name = "kms", url = "${" + SimpleKmsClientConstant.CONFIG_PREFIX + ".base-url}",
        path = SimpleKmsClientConstant.API_BASE_PATH)
public interface KmsFeignClient {

    /**
     * 创建密钥（幂等）。
     *
     * @param idempotencyKey 幂等键
     * @param body           {keyAlias, purpose, algorithm}
     * @return 密钥
     */
    @PostMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS)
    KmsKeyResponse createKey(@RequestHeader(SimpleKmsClientConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
                             @RequestBody Map<String, Object> body);

    /**
     * 查询单个密钥。
     *
     * @param keyRef 密钥引用
     * @return 密钥
     */
    @GetMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS + "/{keyRef}")
    KmsKeyResponse getKey(@PathVariable(SimpleKmsClientConstant.FIELD_KEY_REF) String keyRef);

    /**
     * 分页查询密钥（可选过滤参数缺省时请勿传 null 以外的空值）。
     *
     * @return 密钥分页
     */
    @GetMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS)
    KmsKeyPageResponse listKeys(@RequestParam(SimpleKmsClientConstant.FIELD_PAGE) Integer page,
                                @RequestParam(SimpleKmsClientConstant.FIELD_SIZE) Integer size,
                                @RequestParam(SimpleKmsClientConstant.QUERY_ALIAS) String alias,
                                @RequestParam(SimpleKmsClientConstant.FIELD_PURPOSE) String purpose,
                                @RequestParam(SimpleKmsClientConstant.FIELD_ALGORITHM) String algorithm,
                                @RequestParam(SimpleKmsClientConstant.FIELD_STATE) String state);

    /**
     * 变更密钥状态（幂等，乐观并发）。
     *
     * @param body {state, expectedRowVersion}
     * @return 密钥
     */
    @PatchMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS + "/{keyRef}/"
            + SimpleKmsClientConstant.RESOURCE_STATE)
    KmsKeyResponse changeKeyState(@RequestHeader(SimpleKmsClientConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
                                  @PathVariable(SimpleKmsClientConstant.FIELD_KEY_REF) String keyRef,
                                  @RequestBody Map<String, Object> body);

    /**
     * 轮换密钥（幂等，乐观并发）。
     *
     * @param body {expectedRowVersion}
     * @return 密钥
     */
    @PostMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS + "/{keyRef}/"
            + SimpleKmsClientConstant.RESOURCE_VERSIONS)
    KmsKeyResponse rotateKey(@RequestHeader(SimpleKmsClientConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
                             @PathVariable(SimpleKmsClientConstant.FIELD_KEY_REF) String keyRef,
                             @RequestBody Map<String, Object> body);

    /**
     * 安排销毁（幂等，闭窗口语义由 server 校验）。
     *
     * @param body {dueAt(UTC 毫秒字符串), expectedRowVersion}
     * @return 密钥
     */
    @PutMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS + "/{keyRef}/"
            + SimpleKmsClientConstant.RESOURCE_DESTRUCTION)
    KmsKeyResponse scheduleDestruction(
            @RequestHeader(SimpleKmsClientConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @PathVariable(SimpleKmsClientConstant.FIELD_KEY_REF) String keyRef,
            @RequestBody Map<String, Object> body);

    /**
     * 取消销毁（幂等，乐观并发）。
     *
     * @param body {expectedRowVersion}
     */
    @DeleteMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS + "/{keyRef}/"
            + SimpleKmsClientConstant.RESOURCE_DESTRUCTION)
    void cancelDestruction(@RequestHeader(SimpleKmsClientConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
                           @PathVariable(SimpleKmsClientConstant.FIELD_KEY_REF) String keyRef,
                           @RequestBody Map<String, Object> body);

    /**
     * 创建策略（幂等）。
     *
     * @param body {principalId, operation[, keyVersion, expiresAt]}
     * @return 策略
     */
    @PostMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS + "/{keyRef}/"
            + SimpleKmsClientConstant.RESOURCE_POLICIES)
    KmsPolicyResponse createPolicy(
            @RequestHeader(SimpleKmsClientConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @PathVariable(SimpleKmsClientConstant.FIELD_KEY_REF) String keyRef,
            @RequestBody Map<String, Object> body);

    /**
     * 查询密钥策略列表。
     *
     * @param keyRef 密钥引用
     * @return 策略列表
     */
    @GetMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS + "/{keyRef}/"
            + SimpleKmsClientConstant.RESOURCE_POLICIES)
    KmsPolicyListResponse listPolicies(@PathVariable(SimpleKmsClientConstant.FIELD_KEY_REF) String keyRef);

    /**
     * 撤销策略（幂等，乐观并发）。
     *
     * @param body {expectedRowVersion}
     */
    @DeleteMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS + "/{keyRef}/"
            + SimpleKmsClientConstant.RESOURCE_POLICIES + "/{policyId}")
    void revokePolicy(@RequestHeader(SimpleKmsClientConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
                      @PathVariable(SimpleKmsClientConstant.FIELD_KEY_REF) String keyRef,
                      @PathVariable(SimpleKmsClientConstant.FIELD_POLICY_ID) String policyId,
                      @RequestBody Map<String, Object> body);

    /**
     * 签名。
     *
     * @param body {keyRef, input(Base64url)[, version]}
     * @return 签名结果
     */
    @PostMapping("/" + SimpleKmsClientConstant.RESOURCE_CRYPTO + "/" + SimpleKmsClientConstant.RESOURCE_SIGNATURES)
    KmsSignResponse sign(@RequestBody Map<String, Object> body);

    /**
     * 验签。
     *
     * @param body {keyRef, input, signature[, version]}
     * @return 验签结果
     */
    @PostMapping("/" + SimpleKmsClientConstant.RESOURCE_CRYPTO + "/"
            + SimpleKmsClientConstant.RESOURCE_VERIFICATIONS)
    KmsVerifyResponse verify(@RequestBody Map<String, Object> body);

    /**
     * 信封加密。
     *
     * @param body {keyRef, plaintext(Base64url)[, aad]}
     * @return 信封
     */
    @PostMapping("/" + SimpleKmsClientConstant.RESOURCE_CRYPTO + "/"
            + SimpleKmsClientConstant.RESOURCE_ENVELOPES)
    KmsEnvelopeResponse encrypt(@RequestBody Map<String, Object> body);

    /**
     * 信封解密。
     *
     * @param body {envelope(Base64url)[, aad]}
     * @return 明文
     */
    @PostMapping("/" + SimpleKmsClientConstant.RESOURCE_CRYPTO + "/"
            + SimpleKmsClientConstant.RESOURCE_DECRYPTIONS)
    KmsPlaintextResponse decrypt(@RequestBody Map<String, Object> body);

    /**
     * 读取指定版本公钥（version 缺省传 null）。
     *
     * @param keyRef  密钥引用
     * @param version 版本号，null 表示当前激活版本
     * @return 公钥
     */
    @GetMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS + "/{keyRef}/"
            + SimpleKmsClientConstant.RESOURCE_PUBLIC_KEY)
    KmsPublicKeyResponse readPublicKey(@PathVariable(SimpleKmsClientConstant.FIELD_KEY_REF) String keyRef,
                                       @RequestParam(SimpleKmsClientConstant.FIELD_VERSION) Integer version);

    /**
     * 公钥列表（契约直接返回数组）。
     *
     * @param keyRef 密钥引用
     * @return 公钥列表
     */
    @GetMapping("/" + SimpleKmsClientConstant.RESOURCE_KEYS + "/{keyRef}/"
            + SimpleKmsClientConstant.RESOURCE_PUBLIC_KEYS)
    List<KmsPublicKeyResponse> listPublicKeys(@PathVariable(SimpleKmsClientConstant.FIELD_KEY_REF) String keyRef);
}

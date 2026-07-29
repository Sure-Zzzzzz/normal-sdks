package io.github.surezzzzzz.sdk.kms.client.client;

import io.github.surezzzzzz.sdk.kms.client.model.*;

import java.time.Instant;
import java.util.List;

/**
 * KMS 完整 HTTP 操作客户端。
 *
 * <p>面向需要管理逻辑密钥、精确策略和密码操作的调用方，所有返回类型均为 Client 自有模型，
 * 不依赖 Server/Core 领域对象。写操作的幂等键由调用方生成并负责保存；Client 不重试、重放或生成幂等键。</p>
 *
 * @author surezzzzzz
 */
public interface KmsClient {

    /**
     * 创建逻辑密钥。
     *
     * @param idempotencyKey 调用方提供的幂等键
     * @param keyAlias       逻辑密钥别名
     * @param purpose        密钥用途
     * @param algorithm      密码算法
     * @return 创建后的逻辑密钥
     */
    KmsKey createKey(String idempotencyKey, String keyAlias, String purpose, String algorithm);

    /**
     * 查询逻辑密钥。
     *
     * @param keyRef 逻辑密钥标识
     * @return 逻辑密钥
     */
    KmsKey getKey(String keyRef);

    /**
     * 分页查询逻辑密钥。
     *
     * @param page      页码
     * @param size      每页数量
     * @param alias     别名筛选条件
     * @param purpose   用途筛选条件
     * @param algorithm 算法筛选条件
     * @param state     状态筛选条件
     * @return 分页结果
     */
    KmsKeyPage listKeys(Integer page, Integer size, String alias, String purpose, String algorithm, String state);

    /**
     * 修改逻辑密钥状态。
     *
     * @param idempotencyKey     调用方提供的幂等键
     * @param keyRef             逻辑密钥标识
     * @param state              目标状态
     * @param expectedRowVersion 乐观锁预期行版本
     * @return 更新后的逻辑密钥
     */
    KmsKey changeKeyState(String idempotencyKey, String keyRef, String state, Long expectedRowVersion);

    /**
     * 轮换逻辑密钥版本。
     *
     * @param idempotencyKey     调用方提供的幂等键
     * @param keyRef             逻辑密钥标识
     * @param expectedRowVersion 乐观锁预期行版本
     * @return 更新后的逻辑密钥
     */
    KmsKey rotateKey(String idempotencyKey, String keyRef, Long expectedRowVersion);

    /**
     * 安排逻辑密钥销毁。
     *
     * @param idempotencyKey     调用方提供的幂等键
     * @param keyRef             逻辑密钥标识
     * @param destroyAfter       销毁时间
     * @param expectedRowVersion 乐观锁预期行版本
     * @return 更新后的逻辑密钥
     */
    KmsKey scheduleDestruction(String idempotencyKey, String keyRef, Instant destroyAfter, Long expectedRowVersion);

    /**
     * 取消逻辑密钥销毁安排。
     *
     * @param idempotencyKey     调用方提供的幂等键
     * @param keyRef             逻辑密钥标识
     * @param expectedRowVersion 预期行版本
     */
    void cancelDestruction(String idempotencyKey, String keyRef, Long expectedRowVersion);

    /**
     * 创建精确授权策略。
     *
     * @param idempotencyKey 调用方提供的幂等键
     * @param keyRef         逻辑密钥标识
     * @param principalId    被授权主体标识
     * @param keyVersion     限定目标密钥版本，可为空；为空时匹配该逻辑密钥的全部版本
     * @param operation      允许的操作
     * @param expiresAt      策略过期时间，可为空；为空时不设置策略到期时间
     * @return 创建后的策略
     */
    KmsPolicy createPolicy(String idempotencyKey, String keyRef, String principalId, Integer keyVersion,
                           String operation, Instant expiresAt);

    /**
     * 查询逻辑密钥的策略集合。
     *
     * @param keyRef 逻辑密钥标识
     * @return 不可变策略集合
     */
    List<KmsPolicy> listPolicies(String keyRef);

    /**
     * 撤销精确授权策略。
     *
     * @param idempotencyKey     调用方提供的幂等键
     * @param keyRef             逻辑密钥标识
     * @param policyId           策略标识
     * @param expectedRowVersion 预期行版本
     */
    void revokePolicy(String idempotencyKey, String keyRef, String policyId, Long expectedRowVersion);

    /**
     * 使用指定或当前版本进行签名。
     *
     * @param keyRef       逻辑密钥标识
     * @param version      指定版本，可为空；为空时由 KMS 选择当前可用版本
     * @param signingInput 待签名字节
     * @return 完整 HTTP 签名结果，含逻辑密钥标识与实际版本
     */
    KmsSignature sign(String keyRef, Integer version, byte[] signingInput);

    /**
     * 验证签名。
     *
     * @param keyRef       逻辑密钥标识
     * @param version      指定版本，可为空；为空时由 KMS 选择当前可用版本
     * @param signingInput 原始签名字节
     * @param signature    JOSE 签名字节
     * @return 签名有效时返回 {@code true}；{@code false} 是正常业务结果而非异常
     */
    boolean verify(String keyRef, Integer version, byte[] signingInput, byte[] signature);

    /**
     * 使用逻辑密钥加密数据。
     *
     * @param keyRef    逻辑密钥标识
     * @param plaintext 明文字节
     * @param aad       可选附加认证数据，传入时解密必须提供完全相同的字节
     * @return 完整版本化加密信封
     */
    byte[] encrypt(String keyRef, byte[] plaintext, byte[] aad);

    /**
     * 解密版本化加密信封。
     *
     * @param envelope KMS 返回的完整版本化加密信封，调用方不得自行拆分或重组
     * @param aad      可选附加认证数据
     * @return 明文字节
     */
    byte[] decrypt(byte[] envelope, byte[] aad);

    /**
     * 查询指定或当前版本的可发布公钥。
     *
     * @param keyRef  逻辑密钥标识
     * @param version 指定版本，可为空
     * @return 可发布公钥
     */
    KmsPublicKey readPublicKey(String keyRef, Integer version);

    /**
     * 查询逻辑密钥的全部可发布公钥。
     *
     * @param keyRef 逻辑密钥标识
     * @return 不可变公钥集合
     */
    List<KmsPublicKey> listPublicKeys(String keyRef);
}

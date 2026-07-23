package io.github.surezzzzzz.sdk.kms.core.constant;

/**
 * KMS Core 稳定错误码。
 *
 * <p>错误码与 {@link ErrorMessage} 一一对应，调用方应依赖错误码处理，不应解析中文消息。</p>
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 标识或请求参数不满足领域边界。
     */
    public static final String VALIDATION_FAILED = "KMS_CORE_001";
    /**
     * 主体 scope 或精确策略不允许执行操作。
     */
    public static final String AUTHORIZATION_DENIED = "KMS_CORE_002";
    /**
     * 已通过可见性校验的管理资源不存在。
     */
    public static final String RESOURCE_NOT_FOUND = "KMS_CORE_003";
    /**
     * 生命周期迁移或乐观锁版本冲突。
     */
    public static final String STATE_CONFLICT = "KMS_CORE_004";
    /**
     * 策略创建、撤销或并发约束冲突。
     */
    public static final String POLICY_CONFLICT = "KMS_CORE_005";
    /**
     * 同一幂等作用域的请求摘要发生变化。
     */
    public static final String IDEMPOTENCY_CONFLICT = "KMS_CORE_006";
    /**
     * 密钥、策略、审计或任务持久化失败。
     */
    public static final String PERSISTENCE_FAILED = "KMS_CORE_007";
    /**
     * 密文格式、认证、材料或密码学执行失败。
     */
    public static final String CRYPTOGRAPHIC_OPERATION_FAILED = "KMS_CORE_008";
    /**
     * 服务依赖或失败关闭的审计路径不可用。
     */
    public static final String SERVICE_UNAVAILABLE = "KMS_CORE_009";

    private ErrorCode() {
        throw new UnsupportedOperationException(SmartKmsCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}

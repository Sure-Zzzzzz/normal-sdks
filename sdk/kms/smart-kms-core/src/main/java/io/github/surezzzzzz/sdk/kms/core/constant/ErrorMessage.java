package io.github.surezzzzzz.sdk.kms.core.constant;

/**
 * KMS Core 错误信息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    public static final String VALIDATION_FAILED = "参数不合法";
    public static final String AUTHORIZATION_DENIED = "无权执行该操作";
    public static final String RESOURCE_NOT_FOUND = "资源不存在";
    public static final String STATE_CONFLICT = "资源状态冲突";
    public static final String POLICY_CONFLICT = "授权策略冲突";
    public static final String IDEMPOTENCY_CONFLICT = "幂等键与原始请求不一致";
    public static final String PERSISTENCE_FAILED = "持久化失败";
    public static final String CRYPTOGRAPHIC_OPERATION_FAILED = "密码学操作失败";
    public static final String SERVICE_UNAVAILABLE = "服务暂不可用";

    private ErrorMessage() {
        throw new UnsupportedOperationException(SmartKmsCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}

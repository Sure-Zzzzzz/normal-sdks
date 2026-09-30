package io.github.surezzzzzz.sdk.audit.iam.resource.constant;

/**
 * Simple IAM Resource Audit Listener 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleIamResourceAuditListenerConstant {

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.audit.iam.resource";

    /**
     * 默认异步执行器核心线程数。
     */
    public static final int DEFAULT_ASYNC_CORE_POOL_SIZE = 2;

    /**
     * 默认异步执行器队列容量。
     */
    public static final int DEFAULT_ASYNC_QUEUE_CAPACITY = 1000;

    private SimpleIamResourceAuditListenerConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}

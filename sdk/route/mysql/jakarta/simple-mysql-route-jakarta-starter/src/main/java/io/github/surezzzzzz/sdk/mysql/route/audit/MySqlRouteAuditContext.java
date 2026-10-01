package io.github.surezzzzzz.sdk.mysql.route.audit;

import lombok.Getter;

/**
 * 当前线程的 MySQL Route 审计补充上下文。
 *
 * @author surezzzzzz
 */
@Getter
public final class MySqlRouteAuditContext {

    private static final ThreadLocal<MySqlRouteAuditContext> CURRENT = new ThreadLocal<>();

    private final String subject;
    private final String capability;
    private final String requestId;
    private final String resourceDigest;

    /**
     * 创建仅包含脱敏信息的审计补充上下文。
     *
     * @param subject        操作主体标识
     * @param capability     已授权能力标识
     * @param requestId      请求标识
     * @param resourceDigest 调用方提供的小写十六进制 SHA-256 资源摘要；格式无效时使用 Route key 摘要
     */
    public MySqlRouteAuditContext(String subject, String capability, String requestId, String resourceDigest) {
        this.subject = subject;
        this.capability = capability;
        this.requestId = requestId;
        this.resourceDigest = resourceDigest;
    }

    /**
     * 获取当前线程的审计补充上下文。
     *
     * @return 当前审计上下文；不存在时返回 {@code null}
     */
    public static MySqlRouteAuditContext current() {
        return CURRENT.get();
    }

    /**
     * 在当前线程打开审计补充上下文作用域。
     *
     * @param context 待绑定的审计补充上下文，可为 {@code null}
     * @return 可自动恢复前序审计上下文的作用域
     */
    public static Scope open(MySqlRouteAuditContext context) {
        MySqlRouteAuditContext previous = CURRENT.get();
        CURRENT.set(context);
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final MySqlRouteAuditContext previous;
        private boolean closed;

        private Scope(MySqlRouteAuditContext previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}

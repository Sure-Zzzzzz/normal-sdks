package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test;

import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContext;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContextProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 测试用已建立线程上下文 Provider。
 *
 * @author surezzzzzz
 */
@Component
public class TestAuditContextProvider implements XffCaptureAuditContextProvider {

    /**
     * 测试请求 ID Header。
     */
    public static final String REQUEST_ID_HEADER = "X-Test-Request-ID";

    /**
     * 测试 Trace ID Header。
     */
    public static final String TRACE_ID_HEADER = "X-Test-Trace-ID";

    /**
     * 测试客户端标识 Header。
     */
    public static final String CLIENT_ID_HEADER = "X-Test-Client-ID";

    /**
     * 测试未声明扩展 Header。
     */
    public static final String UNMAPPED_EXTENSION_HEADER = "X-Test-Unmapped-Extension";

    /**
     * 可查询客户端标识扩展字段。
     */
    public static final String CLIENT_ID_EXTENSION = "clientId";

    /**
     * 未在模板声明的扩展字段。
     */
    public static final String UNMAPPED_EXTENSION = "unmappedExtension";

    private static final ThreadLocal<XffCaptureAuditContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 建立测试上下文。
     *
     * @param requestId  请求标识
     * @param traceId    链路标识
     * @param extensions 业务扩展字段
     */
    public static void open(String requestId, String traceId, Map<String, String> extensions) {
        CONTEXT_HOLDER.set(new XffCaptureAuditContext(requestId, traceId, extensions));
    }

    /**
     * 清理测试上下文。
     */
    public static void close() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 返回已建立的测试上下文。
     *
     * @return 测试上下文，未建立时返回 null
     */
    @Override
    public XffCaptureAuditContext currentContext() {
        return CONTEXT_HOLDER.get();
    }
}

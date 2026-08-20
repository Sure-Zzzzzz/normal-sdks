package io.github.surezzzzzz.sdk.audit.http.xff.context;

/**
 * XFF Capture 审计上下文 Provider。
 *
 * <p>Provider 只能读取当前线程中已经建立的请求上下文，不能反向调用业务 Service 查库。</p>
 *
 * @author surezzzzzz
 */
public interface XffCaptureAuditContextProvider {

    /**
     * 获取当前审计上下文。
     *
     * @return 不可变上下文，取不到时允许返回 null
     */
    XffCaptureAuditContext currentContext();
}

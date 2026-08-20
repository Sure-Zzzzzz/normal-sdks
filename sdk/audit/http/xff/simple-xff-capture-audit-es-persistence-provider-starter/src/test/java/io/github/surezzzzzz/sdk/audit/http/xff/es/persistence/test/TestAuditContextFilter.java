package io.github.surezzzzzz.sdk.audit.http.xff.es.persistence.test;

import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用 Capture 前置审计上下文 Filter。
 *
 * @author surezzzzzz
 */
public class TestAuditContextFilter extends OncePerRequestFilter {

    /**
     * 在 Capture Filter 前建立请求上下文，并在请求结束后清理。
     *
     * @param request     请求
     * @param response    响应
     * @param filterChain Filter 链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Map<String, String> extensions = new LinkedHashMap<>();
        String clientId = request.getHeader(TestAuditContextProvider.CLIENT_ID_HEADER);
        if (clientId != null) {
            extensions.put(TestAuditContextProvider.CLIENT_ID_EXTENSION, clientId);
        }
        String unmappedExtension = request.getHeader(
                TestAuditContextProvider.UNMAPPED_EXTENSION_HEADER);
        if (unmappedExtension != null) {
            extensions.put(TestAuditContextProvider.UNMAPPED_EXTENSION, unmappedExtension);
        }
        TestAuditContextProvider.open(
                request.getHeader(TestAuditContextProvider.REQUEST_ID_HEADER),
                request.getHeader(TestAuditContextProvider.TRACE_ID_HEADER), extensions
        );
        try {
            filterChain.doFilter(request, response);
        } finally {
            TestAuditContextProvider.close();
        }
    }
}

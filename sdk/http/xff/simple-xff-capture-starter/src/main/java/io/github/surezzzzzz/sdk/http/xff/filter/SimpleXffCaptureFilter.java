package io.github.surezzzzzz.sdk.http.xff.filter;

import io.github.surezzzzzz.sdk.http.xff.service.XffCaptureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * XFF 自动采集 Filter。
 *
 * <p>Filter 只负责触发统一采集服务，不在入口层复制解析规则。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class SimpleXffCaptureFilter extends OncePerRequestFilter {

    private final XffCaptureService xffCaptureService;

    /**
     * 采集当前请求后继续原 Filter 链。
     *
     * @param request     当前请求
     * @param response    当前响应
     * @param filterChain 原 Filter 链
     * @throws ServletException Servlet 链异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            xffCaptureService.capture(request);
        } catch (RuntimeException e) {
            log.warn("XFF 自动采集失败，原请求继续执行，异常类型=[{}]", e.getClass().getName());
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 异步分派不重复采集。
     *
     * @return true
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    /**
     * 错误分派不重复采集。
     *
     * @return true
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }
}

package io.github.surezzzzzz.sdk.http.xff.filter;

import io.github.surezzzzzz.sdk.http.xff.configuration.SimpleXffCaptureProperties;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureWebConstant;
import io.github.surezzzzzz.sdk.http.xff.service.XffCaptureService;
import io.github.surezzzzzz.sdk.http.xff.support.ReplayableRequestBodyWrapper;
import io.github.surezzzzzz.sdk.http.xff.support.RequestDataCapturePreparer;
import io.github.surezzzzzz.sdk.http.xff.support.RequestDataCaptureResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * XFF 自动采集 Filter。
 *
 * <p>Filter 只负责触发统一采集服务，不在入口层复制解析规则。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleXffCaptureFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final XffCaptureService xffCaptureService;
    private final RequestDataCapturePreparer requestDataCapturePreparer;

    private List<String> excludedPathPatterns = SimpleXffCaptureConstant.DEFAULT_EXCLUDED_PATH_PATTERNS;

    /**
     * 创建默认关闭请求数据采集的 XFF Filter。
     *
     * @param xffCaptureService XFF 采集服务
     */
    public SimpleXffCaptureFilter(XffCaptureService xffCaptureService) {
        this(xffCaptureService, SimpleXffCaptureConstant.DEFAULT_EXCLUDED_PATH_PATTERNS,
                new RequestDataCapturePreparer(new SimpleXffCaptureProperties()));
    }

    /**
     * 创建带路径过滤清单的 XFF 自动采集 Filter。
     *
     * @param xffCaptureService    XFF 采集服务
     * @param excludedPathPatterns 不自动采集的 Ant 路径模式
     */
    public SimpleXffCaptureFilter(XffCaptureService xffCaptureService, Collection<String> excludedPathPatterns) {
        this(xffCaptureService, excludedPathPatterns,
                new RequestDataCapturePreparer(new SimpleXffCaptureProperties()));
    }

    /**
     * 创建带请求数据准备器的 XFF Filter。
     *
     * @param xffCaptureService          XFF 采集服务
     * @param excludedPathPatterns       不自动采集的 Ant 路径模式
     * @param requestDataCapturePreparer 请求数据准备器
     */
    public SimpleXffCaptureFilter(XffCaptureService xffCaptureService,
                                  Collection<String> excludedPathPatterns,
                                  RequestDataCapturePreparer requestDataCapturePreparer) {
        this.xffCaptureService = xffCaptureService;
        this.excludedPathPatterns = normalizeExcludedPathPatterns(excludedPathPatterns);
        this.requestDataCapturePreparer = requestDataCapturePreparer;
    }

    private static List<String> normalizeExcludedPathPatterns(Collection<String> pathPatterns) {
        if (pathPatterns == null || pathPatterns.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalizedPathPatterns = new ArrayList<String>();
        for (String pathPattern : pathPatterns) {
            if (!StringUtils.hasText(pathPattern)) {
                continue;
            }
            String normalizedPathPattern = pathPattern.trim();
            if (!normalizedPathPattern.startsWith(SimpleXffCaptureWebConstant.URL_PATH_SEPARATOR)) {
                normalizedPathPattern = SimpleXffCaptureWebConstant.URL_PATH_SEPARATOR + normalizedPathPattern;
            }
            normalizedPathPatterns.add(normalizedPathPattern);
        }
        return Collections.unmodifiableList(normalizedPathPatterns);
    }

    private static String applicationPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestPath.equals(contextPath)) {
            return SimpleXffCaptureWebConstant.URL_PATH_SEPARATOR;
        }
        if (StringUtils.hasText(contextPath)
                && requestPath.startsWith(contextPath + SimpleXffCaptureWebConstant.URL_PATH_SEPARATOR)) {
            return requestPath.substring(contextPath.length());
        }
        return requestPath;
    }

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
        HttpServletRequest captureRequest = request;
        try {
            try {
                RequestDataCaptureResult captureResult = requestDataCapturePreparer.prepare(request);
                captureRequest = captureResult.getRequest();
                captureRequest.setAttribute(SimpleXffCaptureConstant.REQUEST_ATTRIBUTE_REQUEST_DATA_SNAPSHOT,
                        captureResult.getSnapshot());
            } catch (RuntimeException e) {
                log.warn("请求数据准备失败，保留原始 XFF 自动采集", e);
            }
            try {
                xffCaptureService.capture(captureRequest);
            } catch (RuntimeException e) {
                log.warn("XFF 自动采集失败，原请求继续执行", e);
            }
            filterChain.doFilter(captureRequest, response);
        } finally {
            if (captureRequest instanceof ReplayableRequestBodyWrapper) {
                ((ReplayableRequestBodyWrapper) captureRequest).deleteReplayFile();
            }
        }
    }

    /**
     * 判断请求是否命中自动采集排除清单。
     *
     * @param request 当前请求
     * @return true 表示跳过自动采集
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = applicationPath(request);
        for (String excludedPathPattern : excludedPathPatterns) {
            if (PATH_MATCHER.match(excludedPathPattern, requestPath)) {
                return true;
            }
        }
        return false;
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

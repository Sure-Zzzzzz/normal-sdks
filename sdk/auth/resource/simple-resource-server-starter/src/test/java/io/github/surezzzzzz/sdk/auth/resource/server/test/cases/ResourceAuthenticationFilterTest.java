package io.github.surezzzzzz.sdk.auth.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.auth.resource.server.filter.ResourceAuthenticationFilter;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceServerEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 资源认证过滤器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class ResourceAuthenticationFilterTest {

    /**
     * 验证可替换认证引擎返回非法结果时安全拒绝并清理上下文。
     *
     * @throws Exception 过滤器调用异常
     */
    @Test
    void shouldRejectInvalidCustomEngineResultAndClearSecurityContext() throws Exception {
        ResourceServerEngine engine = request -> null;
        ResourceAuthenticationFilter filter = new ResourceAuthenticationFilter(engine,
                Collections.singletonList("/api/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("stale", "stale"));

        filter.doFilter(request, response, new UnsupportedFilterChain());

        log.info("非法Engine结果状态: {}", response.getStatus());
        assertEquals(Integer.valueOf(401), Integer.valueOf(response.getStatus()), "非法Engine结果必须按未认证拒绝");
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "认证上下文必须在请求结束后清理");
    }

    /**
     * 验证主体绑定不一致时安全拒绝并清理上下文。
     *
     * @throws Exception 过滤器调用异常
     */
    @Test
    void shouldRejectMismatchedAuthenticatedResultAndClearSecurityContext() throws Exception {
        ResourceAuthenticationSourceId sourceId = new ResourceAuthenticationSourceId("iam");
        ResourceServerEngine engine = request -> ResourceAuthenticationResult.authenticated(
                new VerifiedResourcePrincipal(sourceId, ResourceSubjectType.HUMAN, "subject-a"), authorization("subject-b"));
        ResourceAuthenticationFilter filter = new ResourceAuthenticationFilter(engine,
                Collections.singletonList("/api/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("stale", "stale"));

        filter.doFilter(request, response, new UnsupportedFilterChain());

        log.info("主体绑定不一致状态: {}", response.getStatus());
        assertEquals(Integer.valueOf(401), Integer.valueOf(response.getStatus()), "主体绑定不一致必须按未认证拒绝");
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "认证上下文必须在请求结束后清理");
    }

    /**
     * 验证认证成功时仅在过滤器链内可见已验证上下文，并在结束后清理。
     *
     * @throws Exception 过滤器调用异常
     */
    @Test
    void shouldExposeVerifiedContextOnlyDuringSuccessfulProtectedRequest() throws Exception {
        ResourceAuthenticationSourceId sourceId = new ResourceAuthenticationSourceId("iam");
        ResourceServerEngine engine = request -> ResourceAuthenticationResult.authenticated(
                new VerifiedResourcePrincipal(sourceId, ResourceSubjectType.HUMAN, "subject-a"),
                authorization("subject-a"));
        ResourceAuthenticationFilter filter = new ResourceAuthenticationFilter(engine,
                Collections.singletonList("/api/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new FilterChain() {
            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                assertEquals(VerifiedResourceContext.class, authentication.getPrincipal().getClass(),
                        "过滤器链内必须只有已验证上下文");
                assertEquals("anonymous", authentication.getCredentials(), "认证态不得暴露Bearer凭据");
            }
        });

        assertEquals(Integer.valueOf(200), Integer.valueOf(response.getStatus()), "认证成功必须继续过滤器链");
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "成功请求结束后必须清理认证上下文");
    }

    /**
     * 验证公开路径不会调用认证引擎或清理宿主上下文。
     *
     * @throws Exception 过滤器调用异常
     */
    @Test
    void shouldBypassAuthenticationForUnprotectedRequest() throws Exception {
        ResourceServerEngine engine = request -> {
            throw new AssertionError("非受保护路径不得调用认证引擎");
        };
        ResourceAuthenticationFilter filter = new ResourceAuthenticationFilter(engine,
                Collections.singletonList("/api/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();
        UsernamePasswordAuthenticationToken hostAuthentication = new UsernamePasswordAuthenticationToken("host", "host");
        SecurityContextHolder.getContext().setAuthentication(hostAuthentication);

        try {
            filter.doFilter(request, response, new FilterChain() {
                @Override
                public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
                    assertEquals(hostAuthentication, SecurityContextHolder.getContext().getAuthentication(),
                            "非受保护路径不得改写宿主认证上下文");
                }
            });
            assertEquals(Integer.valueOf(200), Integer.valueOf(response.getStatus()), "非受保护路径必须继续过滤器链");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private ApplicationAuthorizationContext authorization(String subjectId) {
        Instant now = Instant.now();
        return new ApplicationAuthorizationContext(SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION, ApplicationAuthorizationSubjectType.HUMAN, subjectId,
                "app-a", true, Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonList("read"), null, 1L, "manifest-a", "digest-a", now.minusSeconds(1L),
                now.plusSeconds(60L));
    }

    /**
     * 不应被调用的过滤器链。
     */
    private static final class UnsupportedFilterChain implements FilterChain {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            throw new AssertionError("非法认证结果不得继续过滤器链");
        }
    }
}

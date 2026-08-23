package io.github.surezzzzzz.sdk.auth.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.auth.resource.server.event.ResourceAccessEvent;
import io.github.surezzzzzz.sdk.auth.resource.server.filter.ResourceAuthenticationFilter;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceServerEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
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

    /**
     * 验证认证成功后发布不含凭据的公共访问事件。
     *
     * @throws Exception 过滤器调用异常
     */
    @Test
    void shouldPublishSanitizedAccessEventAfterAuthentication() throws Exception {
        ResourceAuthenticationSourceId sourceId = new ResourceAuthenticationSourceId("aksk");
        ResourceServerEngine engine = request -> ResourceAuthenticationResult.authenticated(
                new VerifiedResourcePrincipal(sourceId, ResourceSubjectType.SERVICE, "service-a"),
                serviceAuthorization("service-a"));
        CapturingEventPublisher eventPublisher = new CapturingEventPublisher();
        ResourceAuthenticationFilter filter = new ResourceAuthenticationFilter(engine,
                Collections.singletonList("/api/**"), eventPublisher);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("User-Agent", "agent-summary");

        filter.doFilter(request, new MockHttpServletResponse(), new FilterChain() {
            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
            }
        });

        ResourceAccessEvent event = eventPublisher.event;
        log.info("公共访问事件来源: {}", event.getAuthenticationSourceId());
        assertEquals("aksk", event.getAuthenticationSourceId(), "事件必须记录认证来源");
        assertEquals(ResourceSubjectType.SERVICE, event.getSubjectType(), "事件必须记录已验证主体类型");
        assertEquals("service-a", event.getSubjectId(), "事件必须记录已验证主体标识");
        assertEquals("app-a", event.getApplicationCode(), "事件必须记录已授权应用");
        assertEquals("/api/orders", event.getRequestUri(), "事件必须记录请求路径摘要");
        assertEquals("GET", event.getHttpMethod(), "事件必须记录请求方法摘要");
    }

    /**
     * 验证事件观察失败不改变认证结果。
     *
     * @throws Exception 过滤器调用异常
     */
    @Test
    void shouldContinueWhenAccessEventPublisherFails() throws Exception {
        ResourceAuthenticationSourceId sourceId = new ResourceAuthenticationSourceId("iam");
        ResourceServerEngine engine = request -> ResourceAuthenticationResult.authenticated(
                new VerifiedResourcePrincipal(sourceId, ResourceSubjectType.HUMAN, "subject-a"),
                authorization("subject-a"));
        ResourceAuthenticationFilter filter = new ResourceAuthenticationFilter(engine,
                Collections.singletonList("/api/**"), event -> {
            throw new IllegalStateException("event failure");
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new FilterChain() {
            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
            }
        });

        log.info("事件发布失败状态: {}", response.getStatus());
        assertEquals(Integer.valueOf(200), Integer.valueOf(response.getStatus()), "事件失败不能改变认证成功结果");
    }

    private ApplicationAuthorizationContext authorization(String subjectId) {
        Instant now = Instant.now();
        return new ApplicationAuthorizationContext(SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION, ApplicationAuthorizationSubjectType.HUMAN, subjectId,
                "app-a", true, Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonList("read"), null, 1L, "manifest-a", "digest-a", now.minusSeconds(1L),
                now.plusSeconds(60L));
    }

    private ApplicationAuthorizationContext serviceAuthorization(String subjectId) {
        Instant now = Instant.now();
        return new ApplicationAuthorizationContext(SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION, ApplicationAuthorizationSubjectType.SERVICE, subjectId,
                "app-a", true, Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.singletonList("read"), null, 1L, "manifest-a", "digest-a", now.minusSeconds(1L),
                now.plusSeconds(60L));
    }

    /**
     * 捕获公共访问事件的发布器。
     */
    private static final class CapturingEventPublisher implements ApplicationEventPublisher {

        private ResourceAccessEvent event;

        @Override
        public void publishEvent(ApplicationEvent event) {
            this.event = (ResourceAccessEvent) event;
        }

        @Override
        public void publishEvent(Object event) {
            this.event = (ResourceAccessEvent) event;
        }
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

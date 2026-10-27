package io.github.surezzzzzz.sdk.feedback.server.configuration;

import io.github.surezzzzzz.sdk.auth.resource.server.filter.ResourceAuthenticationFilter;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ResourceServerEngine;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.controller.SubmissionController;
import io.github.surezzzzzz.sdk.feedback.server.exception.FeedbackConfigurationException;
import io.github.surezzzzzz.sdk.feedback.server.service.FeedbackPrincipalResolver;
import io.github.surezzzzzz.sdk.feedback.server.service.FeedbackResourceServerPrincipalResolver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.Filter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 资源认证桥的实际安全链覆盖检查。
 *
 * @author surezzzzzz
 */
@Slf4j
class FeedbackAssemblyCheckTest {

    private final FeedbackResourceServerPrincipalResolver bridge = new FeedbackResourceServerPrincipalResolver();
    private final Collection<String> feedbackPaths = Arrays.asList("/api/feedback/submissions",
            "/api/feedback/me/submissions/1", "/api/feedback/applications");

    @Test
    void testDisabledResourceServerFails() {
        FilterChainProxy noResourceChain = new FilterChainProxy(Collections.<SecurityFilterChain>emptyList());
        assertIncomplete(noResourceChain, feedbackPaths);
    }

    @Test
    void testEmptyProtectedPathsFails() {
        FilterChainProxy unprotected = chain("/api/feedback/**", Collections.<String>emptyList());
        assertIncomplete(unprotected, feedbackPaths);
    }

    @Test
    void testWrongProtectedPathsFail() {
        FilterChainProxy wrongPath = chain("/api/feedback/**", Collections.singletonList("/api/other/**"));
        assertIncomplete(wrongPath, feedbackPaths);
    }

    @Test
    void testPartialProtectedPathsFail() {
        FilterChainProxy partial = chain("/api/feedback/**",
                Collections.singletonList("/api/feedback/submissions/**"));
        assertIncomplete(partial, feedbackPaths);
    }

    @Test
    void testEarlierChainWithoutResourceFilterFails() {
        SecurityFilterChain earlier = new DefaultSecurityFilterChain(new AntPathRequestMatcher("/api/feedback/**"));
        SecurityFilterChain resource = new DefaultSecurityFilterChain(new AntPathRequestMatcher("/api/feedback/**"),
                resourceFilter(Collections.singletonList("/api/feedback/**")));
        FilterChainProxy shadowed = new FilterChainProxy(Arrays.asList(earlier, resource));
        assertIncomplete(shadowed, feedbackPaths);
    }

    @Test
    void testCorrectProtectedPathsPass() {
        FilterChainProxy protectedChain = chain("/api/feedback/**",
                Collections.singletonList("/api/feedback/**"));
        log.info("资源认证链覆盖反馈提交、本人查询和应用管理端点");
        assertDoesNotThrow(() -> SimpleFeedbackServerAutoConfiguration.validateAssembly(bridge, protectedChain,
                feedbackPaths));
    }

    @Test
    void testFeedbackPathsFollowMvcMappings() {
        RequestMappingHandlerMapping handlerMapping = mock(RequestMappingHandlerMapping.class);
        HandlerMethod feedbackHandler = mock(HandlerMethod.class);
        HandlerMethod unrelatedHandler = mock(HandlerMethod.class);
        doReturn(SubmissionController.class).when(feedbackHandler).getBeanType();
        doReturn(Object.class).when(unrelatedHandler).getBeanType();
        Map<RequestMappingInfo, HandlerMethod> mappings = new LinkedHashMap<RequestMappingInfo, HandlerMethod>();
        mappings.put(RequestMappingInfo.paths("/custom/feedback/submissions/{id}").build(), feedbackHandler);
        mappings.put(RequestMappingInfo.paths("/unrelated/{id}").build(), unrelatedHandler);
        when(handlerMapping.getHandlerMethods()).thenReturn(mappings);
        log.info("MVC 路由映射包含反馈详情和无关端点");
        assertEquals(Collections.singleton("/custom/feedback/submissions/1"),
                SimpleFeedbackServerAutoConfiguration.feedbackPaths(handlerMapping));
    }

    @Test
    void testHostResolverSkipsResourceChainCheck() {
        FeedbackPrincipalResolver hostResolver = mock(FeedbackPrincipalResolver.class);
        ApplicationContext context = mock(ApplicationContext.class);
        assertDoesNotThrow(() -> SimpleFeedbackServerAutoConfiguration.validateAssembly(hostResolver, context));
        verifyNoInteractions(context);
    }

    private void assertIncomplete(FilterChainProxy filterChain, Collection<String> paths) {
        FeedbackConfigurationException exception = assertThrows(FeedbackConfigurationException.class,
                () -> SimpleFeedbackServerAutoConfiguration.validateAssembly(bridge, filterChain, paths));
        log.info("资源认证链不完整时启动拒绝，错误码={}", exception.getErrorCode());
        assertEquals(ErrorCode.COLLABORATION_ASSEMBLY_INCOMPLETE, exception.getErrorCode());
    }

    private FilterChainProxy chain(String matcher, Collection<String> protectedPaths) {
        Filter filter = resourceFilter(protectedPaths);
        return new FilterChainProxy(new DefaultSecurityFilterChain(new AntPathRequestMatcher(matcher), filter));
    }

    private Filter resourceFilter(Collection<String> protectedPaths) {
        return new ResourceAuthenticationFilter(mock(ResourceServerEngine.class), protectedPaths);
    }
}

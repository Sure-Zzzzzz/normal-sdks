package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.filter.IamSessionValidationFilter;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamUserDetailsService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamUserDetailsSupport;
import io.github.surezzzzzz.sdk.auth.iam.server.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import javax.servlet.http.HttpSession;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

/**
 * 会话校验过滤器权限热刷新测试（纯 mock + spring-test Mock servlet 对象）。
 *
 * <p>锁定热刷新三态：版本一致不动、版本不一致（或旧登录态缺失快照）就地重建
 * 登录态权限并写回会话、用户已不存在时按会话失效清理。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class IamSessionValidationFilterHotRefreshTest {

    private static final Long USER_ID = 10L;
    private static final String USERNAME = "operator";
    private static final String SESSION_ID = "session-1";

    @Mock
    private SessionService sessionService;

    @Mock
    private IamUserRepository userRepository;

    @Mock
    private IamUserDetailsService userDetailsService;

    private IamSessionValidationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new IamSessionValidationFilter(sessionService, userRepository, userDetailsService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldKeepAuthenticationWhenPermissionVersionMatches() throws Exception {
        MockHttpSession servletSession = new MockHttpSession();
        servletSession.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_PERMISSION_VERSION, 3L);
        loginWithAuthorities("iam:user:api");
        stubActiveSession(servletSession);
        stubUserVersion(3L);

        filter.doFilter(requestWith(servletSession), new MockHttpServletResponse(), new MockFilterChain());

        verify(userDetailsService, never()).loadUserByUsername(USERNAME);
        assertEquals(Collections.singletonList("iam:user:api"), currentAuthorityNames());
    }

    @Test
    void shouldHotRefreshWhenPermissionVersionChanged() throws Exception {
        MockHttpSession servletSession = new MockHttpSession();
        servletSession.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_PERMISSION_VERSION, 3L);
        loginWithAuthorities("iam:user:api");
        stubActiveSession(servletSession);
        stubUserVersion(4L);
        UserDetails fresh = IamUserDetailsSupport.of(user(USERNAME),
                Collections.singletonList(new SimpleGrantedAuthority("iam:role:api")));
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(fresh);

        filter.doFilter(requestWith(servletSession), new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(Collections.singletonList("iam:role:api"), currentAuthorityNames());
        assertEquals(4L, servletSession.getAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_PERMISSION_VERSION));
        Object stored = servletSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertEquals(Collections.singletonList("iam:role:api"), authorityNamesOf(stored));
    }

    @Test
    void shouldHotRefreshLegacySessionWithoutVersionSnapshot() throws Exception {
        MockHttpSession servletSession = new MockHttpSession();
        loginWithAuthorities("iam:user:api");
        stubActiveSession(servletSession);
        stubUserVersion(1L);
        UserDetails fresh = IamUserDetailsSupport.of(user(USERNAME), Collections.emptyList());
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(fresh);

        filter.doFilter(requestWith(servletSession), new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(Collections.emptyList(), currentAuthorityNames());
        assertEquals(1L, servletSession.getAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_PERMISSION_VERSION));
    }

    @Test
    void shouldInvalidateSessionWhenUserNoLongerExists() throws Exception {
        MockHttpSession servletSession = new MockHttpSession();
        servletSession.setAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_PERMISSION_VERSION, 3L);
        servletSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, "stale");
        loginWithAuthorities("iam:user:api");
        stubActiveSession(servletSession);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        filter.doFilter(requestWith(servletSession), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(servletSession.getAttribute(SimpleIamServerConstant.SESSION_ATTRIBUTE_PERMISSION_VERSION));
        assertNull(servletSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void loginWithAuthorities(String... authorityNames) {
        IamUserDetailsSupport details = IamUserDetailsSupport.of(user(USERNAME),
                java.util.Arrays.stream(authorityNames)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private void stubActiveSession(HttpSession servletSession) {
        IamSessionEntity iamSession = new IamSessionEntity();
        iamSession.setId(SESSION_ID);
        iamSession.setUserId(USER_ID);
        iamSession.setUsername(USERNAME);
        when(sessionService.getActiveBinding(servletSession)).thenReturn(iamSession);
    }

    private void stubUserVersion(Long version) {
        IamUserEntity user = user(USERNAME);
        user.setPermissionVersion(version);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private IamUserEntity user(String username) {
        IamUserEntity user = new IamUserEntity();
        user.setId(USER_ID);
        user.setUsername(username);
        user.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        return user;
    }

    private MockHttpServletRequest requestWith(HttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }

    private java.util.List<String> currentAuthorityNames() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(Object::toString).collect(Collectors.toList());
    }

    private java.util.List<String> authorityNamesOf(Object securityContext) {
        org.springframework.security.core.context.SecurityContext context =
                (org.springframework.security.core.context.SecurityContext) securityContext;
        return context.getAuthentication().getAuthorities().stream()
                .map(Object::toString).collect(Collectors.toList());
    }
}

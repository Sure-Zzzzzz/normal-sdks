package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.configuration.SimpleXffCaptureProperties;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import io.github.surezzzzzz.sdk.http.xff.support.RequestDataRuleMatcher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求数据方法与 URI 规则测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class RequestDataRuleMatcherTest {

    @Test
    void shouldMatchMethodAndApplicationPathWithoutQueryString() {
        RequestDataRuleMatcher matcher = new RequestDataRuleMatcher(Collections.singletonList(
                rule("/api/orders/**", "POST")), Collections.<SimpleXffCaptureProperties.RequestDataRule>emptyList());
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/gateway/api/orders/100");
        request.setContextPath("/gateway");
        request.setQueryString("secret=value");

        log.info("方法 URI 规则匹配：method={}，uri={}，contextPath={}，matched={}",
                request.getMethod(), request.getRequestURI(), request.getContextPath(), matcher.matches(request));
        assertTrue(matcher.matches(request), "POST 规则应匹配 context path 后的应用 URI");
        assertFalse(matcher.matches(new MockHttpServletRequest("GET", "/gateway/api/orders/100")),
                "GET 不应误命中 POST 规则集合中的对应方法");
    }

    @Test
    void shouldSupportAllMethodRuleAndRejectInvalidDefinitions() {
        RequestDataRuleMatcher matcher = new RequestDataRuleMatcher(
                Collections.singletonList(rule("/api/**", "ALL")),
                Collections.<SimpleXffCaptureProperties.RequestDataRule>emptyList());
        assertTrue(matcher.matches(new MockHttpServletRequest("PUT", "/api/item")));
        assertTrue(matcher.matches(new MockHttpServletRequest("PATCH", "/api/item")));
        assertTrue(matcher.matches(new MockHttpServletRequest("DELETE", "/api/item")));

        log.info("ALL 方法规则覆盖 PUT/PATCH/DELETE");
        assertThrows(XffCaptureValidationException.class,
                () -> new RequestDataRuleMatcher(Collections.singletonList(rule("/api/**", "INVALID")), Collections.<SimpleXffCaptureProperties.RequestDataRule>emptyList()));
        assertThrows(XffCaptureValidationException.class,
                () -> new RequestDataRuleMatcher(Collections.singletonList(rule("/api/**", "HEAD")), Collections.<SimpleXffCaptureProperties.RequestDataRule>emptyList()));
        assertThrows(XffCaptureValidationException.class,
                () -> new RequestDataRuleMatcher(Collections.singletonList(rule(" ", "GET")),
                        Collections.<SimpleXffCaptureProperties.RequestDataRule>emptyList()));
        assertThrows(XffCaptureValidationException.class,
                () -> new RequestDataRuleMatcher(Arrays.asList(
                        rule("/api/**", "GET"), rule("/api/**", "GET")),
                        Collections.<SimpleXffCaptureProperties.RequestDataRule>emptyList()));
    }

    @Test
    void shouldGiveBlacklistPriorityOverBroadWhitelist() {
        RequestDataRuleMatcher matcher = new RequestDataRuleMatcher(
                Collections.singletonList(rule("/api/orders/**", "GET")),
                Collections.singletonList(rule("/api/orders/xxx", "GET")));

        log.info("黑名单优先规则：宽白名单命中时精确黑名单应拒绝");
        assertFalse(matcher.matches(new MockHttpServletRequest("GET", "/api/orders/xxx")));
        assertTrue(matcher.matches(new MockHttpServletRequest("GET", "/api/orders/100")));
    }

    @Test
    void shouldNotMatchWhenRulesAreEmpty() {
        RequestDataRuleMatcher matcher = new RequestDataRuleMatcher(
                Collections.<SimpleXffCaptureProperties.RequestDataRule>emptyList(),
                Collections.<SimpleXffCaptureProperties.RequestDataRule>emptyList());

        log.info("空规则集合不应命中请求数据采集");
        assertFalse(matcher.matches(new MockHttpServletRequest("GET", "/api/item")));
    }

    private SimpleXffCaptureProperties.RequestDataRule rule(String pathPattern, String method) {
        SimpleXffCaptureProperties.RequestDataRule rule = new SimpleXffCaptureProperties.RequestDataRule();
        rule.setPathPattern(pathPattern);
        rule.setMethod(method);
        return rule;
    }
}

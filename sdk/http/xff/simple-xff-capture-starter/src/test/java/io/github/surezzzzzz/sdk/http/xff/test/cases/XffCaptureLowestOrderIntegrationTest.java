package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.test.SimpleXffCaptureTestApplication;
import io.github.surezzzzzz.sdk.http.xff.test.support.TestXffCaptureEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.net.InetAddress;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Capture 最低优先级 Filter 真实 HTTP 集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "io.github.surezzzzzz.sdk.http.xff.capture.order=2147483647")
@Import(XffCaptureSecurityTestConfiguration.class)
@ExtendWith(SpringBoot279SecurityTestCondition.class)
class XffCaptureLowestOrderIntegrationTest {

    private static final int CAPTURE_LOWEST_ORDER = Integer.MAX_VALUE;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestXffCaptureEventListener eventListener;

    @Autowired
    private FilterRegistrationBean<?> simpleXffCaptureFilterRegistration;

    @Autowired
    @Qualifier("securityFilterChainRegistration")
    private AbstractFilterRegistrationBean<?> securityFilterChainRegistration;

    @Autowired
    private XffSecurityRequestObservation securityRequestObservation;

    @BeforeEach
    void setUp() {
        eventListener.clear();
    }

    @Test
    void shouldCaptureAllowedRealRequestAfterAllSecurityFilters() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/xff-security-view", String.class);
        SecurityRequestView securityView = securityRequestObservation.getView();
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("最低优先级真实放行请求：captureOrder={}，securityOrder={}，status={}，"
                        + "securityRequestType={}，securityRequestIdentity={}，securityXffPresent={}，"
                        + "remoteAddrPresent={}，eventPresent={}，eventRawCount={}，eventCount={}",
                simpleXffCaptureFilterRegistration.getOrder(),
                securityFilterChainRegistration.getOrder(), response.getStatusCodeValue(),
                securityView.getType(), securityView.getIdentity(), securityView.isXffPresent(),
                securityView.getRemoteAddr() != null, event.getXffChain().isPresent(),
                event.getXffChain().getRawList().size(), eventListener.snapshot().size());
        assertEquals(CAPTURE_LOWEST_ORDER, simpleXffCaptureFilterRegistration.getOrder(),
                "Capture Filter 应使用最低优先级配置");
        assertTrue(simpleXffCaptureFilterRegistration.getOrder()
                        > securityFilterChainRegistration.getOrder(),
                "最低优先级 Capture 应在真实 Security Filter Chain 后执行");
        assertEquals(HttpStatus.OK, response.getStatusCode(), "真实放行请求应返回 200");
        assertNotNull(securityView, "真实 Security 链应处理请求");
        assertFalse(securityView.isXffPresent(), "未携带 XFF 的真实请求不应凭空出现 XFF");
        assertEquals("headerPresent=false|present=false|remoteAddr="
                        + securityView.getRemoteAddr(), response.getBody(),
                "Controller 应返回真实 Header、Capture 和 remoteAddr 事实");
        assertEquals(1, eventListener.snapshot().size(), "放行请求应只发布一个事件");
        assertFalse(event.getXffChain().isPresent(), "remoteAddr 不得回填为 XFF");
        assertEquals(Collections.emptyList(), event.getXffChain().getRawList(),
                "真实请求无 XFF 时事件链必须为空");
        assertNotNull(securityView.getRemoteAddr(), "应使用容器真实提供的 remoteAddr");
        assertEquals(securityView.getRemoteAddr(), event.getApplicationRawRemoteAddress(),
                "事件应独立记录真实 remoteAddr");
    }

    @Test
    void shouldCaptureRuntimeXffHeaderFromRealHttpRequestAfterSecurityFilters() throws Exception {
        String runtimeXff = InetAddress.getLocalHost().getHostAddress();
        HttpHeaders headers = new HttpHeaders();
        headers.set(SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR, runtimeXff);

        ResponseEntity<String> response = restTemplate.exchange(
                "/xff-security-view", HttpMethod.GET,
                new HttpEntity<Void>(headers), String.class);
        SecurityRequestView securityView = securityRequestObservation.getView();
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("最低优先级真实 HTTP XFF 请求：captureOrder={}，securityOrder={}，status={}，"
                        + "runtimeXff={}，securityXffPresent={}，eventCount={}",
                simpleXffCaptureFilterRegistration.getOrder(),
                securityFilterChainRegistration.getOrder(), response.getStatusCodeValue(),
                runtimeXff, securityView.isXffPresent(), eventListener.snapshot().size());
        log.info("完整 Capture 事件：eventId={}，occurredAt={}，requestMethod={}，requestUri={}，"
                        + "applicationRawRemoteAddress={}",
                event.getEventId(), event.getOccurredAt(), event.getRequestMethod(),
                event.getRequestUri(), event.getApplicationRawRemoteAddress());
        log.info("完整 XFF 快照：present={}，rawHeaderList={}，rawList={}",
                event.getXffChain().isPresent(), event.getXffChain().getRawHeaderList(),
                event.getXffChain().getRawList());
        log.info("完整转发 Header 快照：host={}，xRealIp={}，xForwardedHost={}，"
                        + "xForwardedPort={}，xForwardedProto={}",
                event.getForwardedContext().getHost().getRawValueList(),
                event.getForwardedContext().getXRealIp().getRawValueList(),
                event.getForwardedContext().getXForwardedHost().getRawValueList(),
                event.getForwardedContext().getXForwardedPort().getRawValueList(),
                event.getForwardedContext().getXForwardedProto().getRawValueList());
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "携带运行时 XFF 的真实 HTTP 请求应返回 200");
        assertTrue(securityView.isXffPresent(),
                "真实 Security 链应看到 HTTP 客户端发送的 XFF");
        assertEquals("headerPresent=true|present=true|remoteAddr="
                        + securityView.getRemoteAddr(), response.getBody(),
                "Controller 应看到真实 HTTP 请求中的 XFF");
        assertEquals(1, eventListener.snapshot().size(),
                "真实 HTTP 请求应只发布一个事件");
        assertTrue(event.getXffChain().isPresent(),
                "最低优先级 Capture 应采集真实 HTTP 请求中的 XFF");
        assertEquals(Collections.singletonList(runtimeXff),
                event.getXffChain().getRawList(),
                "事件 XFF 应等于真实 HTTP Header 的运行时值");
    }

    @Test
    void shouldPreserveRequestParametersAfterLowestOrderCapture() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/xff-security-parameters?single=first&multiple=one&multiple=two", String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("最低优先级参数请求：status={}，controllerBody={}，eventCount={}，requestUri={}",
                response.getStatusCodeValue(), response.getBody(), eventListener.snapshot().size(),
                event.getRequestUri());
        assertEquals(HttpStatus.OK, response.getStatusCode(), "参数请求应正常响应");
        assertEquals("single=first|multiple=[one, two]", response.getBody(),
                "Capture Filter 不得影响单值或重复 requestParam 绑定");
        assertEquals(1, eventListener.snapshot().size(), "参数请求应只发布一个事件");
        assertEquals("/xff-security-parameters", event.getRequestUri(),
                "Capture 事件 URI 不应包含 requestParam");
    }

    @Test
    void shouldPreserveRequestBodyAfterLowestOrderCapture() {
        String requestBody = "{\"message\":\"capture-body\",\"items\":[\"one\",\"two\"]}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/xff-security-body", HttpMethod.POST,
                new HttpEntity<String>(requestBody, headers), String.class);
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("最低优先级 Body 请求：status={}，controllerBody={}，eventCount={}，requestMethod={}，requestUri={}",
                response.getStatusCodeValue(), response.getBody(), eventListener.snapshot().size(),
                event.getRequestMethod(), event.getRequestUri());
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Body 请求应正常响应");
        assertEquals(requestBody, response.getBody(), "Capture Filter 不得读取或破坏 requestBody");
        assertEquals(1, eventListener.snapshot().size(), "Body 请求应只发布一个事件");
        assertEquals("POST", event.getRequestMethod(), "事件应保留请求方法");
        assertEquals("/xff-security-body", event.getRequestUri(), "事件应保留请求 URI");
    }

    @Test
    void shouldCaptureAuthenticatedRealRequestAfterSecurityFilters() {
        TestRestTemplate authenticatedClient = restTemplate.withBasicAuth(
                "xff-test-user", "xff-test-password");
        ResponseEntity<String> response = authenticatedClient.getForEntity(
                "/xff-security-authenticated", String.class);
        SecurityRequestView securityView = securityRequestObservation.getView();
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("最低优先级真实认证请求：captureOrder={}，securityOrder={}，status={}，"
                        + "securityRequestType={}，securityRequestIdentity={}，securityXffPresent={}，"
                        + "remoteAddrPresent={}，eventPresent={}，eventCount={}",
                simpleXffCaptureFilterRegistration.getOrder(),
                securityFilterChainRegistration.getOrder(), response.getStatusCodeValue(),
                securityView.getType(), securityView.getIdentity(), securityView.isXffPresent(),
                securityView.getRemoteAddr() != null, event.getXffChain().isPresent(),
                eventListener.snapshot().size());
        assertEquals(HttpStatus.OK, response.getStatusCode(), "认证通过请求应返回 200");
        assertFalse(securityView.isXffPresent(), "认证过程不得生成 XFF");
        assertEquals("headerPresent=false|present=false|remoteAddr="
                + securityView.getRemoteAddr(), response.getBody());
        assertEquals(1, eventListener.snapshot().size(), "认证通过请求应只发布一个事件");
        assertFalse(event.getXffChain().isPresent(), "认证请求的 remoteAddr 不得进入 XFF");
    }

    @Test
    void shouldNotCaptureWhenSecurityShortCircuitsUnauthenticatedRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/xff-security-authenticated", String.class);

        log.info("最低优先级真实未认证短路请求：captureOrder={}，securityOrder={}，status={}，"
                        + "securityObserved={}，eventCount={}",
                simpleXffCaptureFilterRegistration.getOrder(),
                securityFilterChainRegistration.getOrder(), response.getStatusCodeValue(),
                securityRequestObservation.getView() != null, eventListener.snapshot().size());
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
                "未认证请求应由真实 Security 链返回 401");
        assertEquals(0, eventListener.snapshot().size(),
                "Security 短路在 Capture 之前时不应伪造或发布 Capture 事件");
    }

    @Test
    void shouldNotCaptureWhenSecurityShortCircuitsForbiddenRequest() {
        TestRestTemplate authenticatedClient = restTemplate.withBasicAuth(
                "xff-test-user", "xff-test-password");
        ResponseEntity<String> response = authenticatedClient.getForEntity(
                "/xff-security-forbidden", String.class);

        log.info("最低优先级真实无权短路请求：captureOrder={}，securityOrder={}，status={}，"
                        + "securityObserved={}，eventCount={}",
                simpleXffCaptureFilterRegistration.getOrder(),
                securityFilterChainRegistration.getOrder(), response.getStatusCodeValue(),
                securityRequestObservation.getView() != null, eventListener.snapshot().size());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "无权请求应由真实 Security 链返回 403");
        assertEquals(0, eventListener.snapshot().size(),
                "Security 短路在 Capture 之前时不应伪造或发布 Capture 事件");
    }
}

package io.github.surezzzzzz.sdk.http.xff.test.cases;

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
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Capture 排在真实 Spring Security 之后的集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "io.github.surezzzzzz.sdk.http.xff.capture.order=-99")
@Import(XffCaptureSecurityTestConfiguration.class)
@ExtendWith(SpringBoot279SecurityTestCondition.class)
class XffCaptureAfterSecurityOrderIntegrationTest {

    private static final int CAPTURE_ORDER_AFTER_SECURITY = -99;

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
    void shouldKeepXffWhenCaptureRunsAfterSecurityChain() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/xff-security-view", String.class);
        SecurityRequestView securityView = securityRequestObservation.getView();
        XffCaptureEvent event = eventListener.snapshot().get(0);

        log.info("真实后置 Security/Capture 链路：captureOrder={}，securityOrder={}，"
                        + "响应状态={}，securityRequestType={}，securityRequestIdentity={}，"
                        + "securityXffPresent={}，remoteAddrPresent={}，eventPresent={}，"
                        + "eventRawCount={}，eventCount={}",
                simpleXffCaptureFilterRegistration.getOrder(),
                securityFilterChainRegistration.getOrder(),
                response.getStatusCodeValue(), securityView.getType(),
                securityView.getIdentity(), securityView.isXffPresent(),
                securityView.getRemoteAddr() != null, event.getXffChain().isPresent(),
                event.getXffChain().getRawList().size(), eventListener.snapshot().size());
        assertEquals(CAPTURE_ORDER_AFTER_SECURITY,
                simpleXffCaptureFilterRegistration.getOrder(),
                "测试配置应使 Capture Filter 紧随 Security Filter Chain");
        assertEquals(-100, securityFilterChainRegistration.getOrder(),
                "Spring Boot 2.7 Security Filter Chain 默认注册顺序应为 -100");
        assertTrue(simpleXffCaptureFilterRegistration.getOrder()
                        > securityFilterChainRegistration.getOrder(),
                "Capture Filter 应在真实 Security Filter Chain 之后执行");
        assertEquals(200, response.getStatusCodeValue(), "允许访问的真实 Security 请求应正常响应");
        assertNotNull(securityView, "真实 Security Filter Chain 应处理该 HTTP 请求");
        assertFalse(securityView.isXffPresent(),
                "未向真实 HTTP 请求注入 XFF 时 Security 链后不应出现 XFF");
        assertEquals("headerPresent=false|present=false|remoteAddr="
                        + securityView.getRemoteAddr(),
                response.getBody(),
                "Controller 应返回真实 Header、Capture 和 remoteAddr 事实");
        assertEquals(1, eventListener.snapshot().size(), "一次真实 HTTP 请求只能发布一个事件");
        assertFalse(event.getXffChain().isPresent(),
                "排在 Security 后也不能把 remoteAddr 当作 XFF");
        assertEquals(Collections.emptyList(), event.getXffChain().getRawList(),
                "审计事件 XFF 链必须保持为空");
        assertNotNull(securityView.getRemoteAddr(),
                "测试请求应保留容器实际提供的 remoteAddr");
        assertEquals(securityView.getRemoteAddr(), event.getApplicationRawRemoteAddress(),
                "审计事件应独立记录真实 remoteAddr");
    }
}

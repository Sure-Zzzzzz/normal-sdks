package io.github.surezzzzzz.sdk.http.xff.test.cases;

import io.github.surezzzzzz.sdk.http.xff.constant.SimpleXffCaptureConstant;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.test.SimpleXffCaptureTestApplication;
import io.github.surezzzzzz.sdk.http.xff.test.support.TestXffCaptureEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spring Boot Forwarded Header 策略真实 HTTP 集成测试。
 *
 * @author surezzzzzz
 */
abstract class AbstractXffCaptureForwardHeadersStrategyIntegrationTest {

    static final String CLIENT_ADDRESS = "198.51.100.10";
    static final String INTERNAL_PROXY_ADDRESS = "10.0.0.1";
    static final String XFF_VALUE = CLIENT_ADDRESS + ", " + INTERNAL_PROXY_ADDRESS;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TestXffCaptureEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener.clear();
    }

    void assertRequestView(boolean forwardedHeadersRemainVisible) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR, XFF_VALUE);

        ResponseEntity<String> response = restTemplate.exchange("/xff-forward-headers-strategy",
                HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
        List<XffCaptureEvent> events = eventListener.snapshot();

        assertEquals(200, response.getStatusCodeValue(), "Controller 应正常响应");
        assertEquals(1, events.size(), "真实请求只应发布一个事件");
        String[] responseView = response.getBody().split("\\|", 2);
        XffCaptureEvent event = events.get(0);
        assertEquals(responseView[0], event.getApplicationRawRemoteAddress(),
                "Capture remoteAddr 必须与 Controller 看到的 Servlet 视图一致");

        if (forwardedHeadersRemainVisible) {
            assertEquals(XFF_VALUE, responseView[1], "该策略下 XFF 应保留在 Servlet 视图中");
            assertTrue(event.getXffChain().isPresent(), "Capture 应采集 Servlet 视图中的 XFF");
            assertEquals(Collections.singletonList(XFF_VALUE), event.getXffChain().getRawHeaderList(),
                    "Capture 应保留 Servlet 视图中的原始 Header 边界");
            assertEquals(Arrays.asList(CLIENT_ADDRESS, INTERNAL_PROXY_ADDRESS),
                    event.getXffChain().getRawList(), "Capture 应保留 Servlet 视图中的拆分链");
        } else {
            assertEquals("<null>", responseView[1], "该策略下 XFF 应不在 Servlet 视图中");
            assertFalse(event.getXffChain().isPresent(),
                    "Capture 不得恢复 Servlet 视图中不存在的 XFF");
            assertEquals(Collections.emptyList(), event.getXffChain().getRawHeaderList(),
                    "原始 Header 边界应反映 Servlet 视图事实");
            assertEquals(Collections.emptyList(), event.getXffChain().getRawList(),
                    "拆分链应反映 Servlet 视图事实");
        }
    }

    void assertFrameworkRequestView() {
        assertRequestView(isFrameworkForwardedHeadersVisible());
    }

    private boolean isFrameworkForwardedHeadersVisible() {
        String bootVersion = SpringBootVersion.getVersion();
        return "2.2.13.RELEASE".equals(bootVersion)
                || "2.3.12.RELEASE".equals(bootVersion);
    }

    @RestController
    static class ForwardHeadersStrategyController {

        @GetMapping("/xff-forward-headers-strategy")
        String requestView(javax.servlet.http.HttpServletRequest request) {
            String xff = request.getHeader(SimpleXffCaptureConstant.HEADER_X_FORWARDED_FOR);
            return request.getRemoteAddr() + "|" + (xff == null ? "<null>" : xff);
        }
    }
}

@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.forward-headers-strategy=none",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
        })
@Import(AbstractXffCaptureForwardHeadersStrategyIntegrationTest.ForwardHeadersStrategyController.class)
class XffCaptureForwardHeadersNoneIntegrationTest
        extends AbstractXffCaptureForwardHeadersStrategyIntegrationTest {

    @Test
    void shouldKeepForwardedHeadersVisible() {
        assertRequestView(true);
    }
}

@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.forward-headers-strategy=native",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
        })
@Import(AbstractXffCaptureForwardHeadersStrategyIntegrationTest.ForwardHeadersStrategyController.class)
class XffCaptureForwardHeadersNativeIntegrationTest
        extends AbstractXffCaptureForwardHeadersStrategyIntegrationTest {

    @Test
    void shouldUseContainerNativeForwardedHeaderHandling() {
        assertRequestView(false);
    }
}

@SpringBootTest(classes = SimpleXffCaptureTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.forward-headers-strategy=framework",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
        })
@Import(AbstractXffCaptureForwardHeadersStrategyIntegrationTest.ForwardHeadersStrategyController.class)
class XffCaptureForwardHeadersFrameworkIntegrationTest
        extends AbstractXffCaptureForwardHeadersStrategyIntegrationTest {

    @Test
    void shouldUseFrameworkForwardedHeaderHandling() {
        assertFrameworkRequestView();
    }
}

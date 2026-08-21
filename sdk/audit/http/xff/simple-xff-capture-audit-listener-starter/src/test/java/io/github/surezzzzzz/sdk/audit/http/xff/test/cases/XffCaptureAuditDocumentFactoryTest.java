package io.github.surezzzzzz.sdk.audit.http.xff.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.configuration.SimpleXffCaptureAuditListenerProperties;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContext;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContextProvider;
import io.github.surezzzzzz.sdk.audit.http.xff.factory.XffCaptureAuditDocumentFactory;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.http.xff.core.constant.SimpleXffCaptureCoreConstant;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.core.model.ForwardedContext;
import io.github.surezzzzzz.sdk.http.xff.core.model.HeaderValueSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffCaptureSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffChain;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XFF Capture 审计文档 Factory 测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(OutputCaptureExtension.class)
class XffCaptureAuditDocumentFactoryTest {

    @Test
    void shouldProjectFinalFieldsWithNormalizedDeduplicatedIps() {
        SimpleXffCaptureAuditListenerProperties properties = properties(null);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "factory-test-service");
        XffCaptureAuditContextProvider provider = () ->
                new XffCaptureAuditContext(" request-1 ", " trace-1 ");
        XffCaptureAuditDocumentFactory factory =
                new XffCaptureAuditDocumentFactory(properties, environment, Optional.of(provider));
        XffCaptureEvent event = event();

        XffCaptureAuditDocument document = factory.create(event);

        log.info("Factory 文档：applicationName={}，xffIpCount={}，publicIpCount={}",
                document.getApplicationName(), document.getXffIpList().size(),
                document.getPublicIpList().size());
        assertEquals("event-1", document.getEventId(), "eventId 应保留");
        assertEquals("2026-08-19T00:00:00.123Z", document.getCapturedTime(),
                "capturedTime 应为 UTC date_time");
        assertEquals("factory-test-service", document.getApplicationName(),
                "应回退 spring.application.name");
        assertEquals("request-1", document.getRequestId(), "requestId 应规范化");
        assertEquals("trace-1", document.getTraceId(), "traceId 应规范化");
        assertEquals("POST", document.getRequestMethod(), "HTTP 方法应保留");
        assertEquals("/api/test", document.getRequestUri(), "URI 应保留");
        assertEquals(Collections.singletonList("api.example.test"), document.getHostList(),
                "Host 应原样保留");
        assertTrue(document.isXffPresent(), "XFF present 应保留");
        assertEquals(Arrays.asList("8.8.8.8", "10.0.0.1", "unknown", "", "8.8.8.8",
                        "2001:4860:4860:0:0:0:0:8888"),
                document.getXffRawList(), "原始 XFF 逻辑链不得去重或丢脏值");
        assertEquals(Arrays.asList("8.8.8.8", "10.0.0.1", "2001:4860:4860::8888"),
                document.getXffIpList(), "合法 IP 应规范化并按首次出现顺序去重");
        assertEquals(Arrays.asList("8.8.8.8", "2001:4860:4860::8888"),
                document.getPublicIpList(), "公网列表应为合法 IP 公网子集");
        assertEquals("10.20.30.40", document.getApplicationRawRemoteAddress(),
                "应用原始远端地址应保留");
        assertEquals("10.20.30.40", document.getApplicationRemoteIp(),
                "合法应用远端地址应写入 IP 投影");
        assertEquals(SimpleXffCaptureCoreConstant.IP_CLASSIFICATION_VERSION,
                document.getClassificationVersion(), "分类版本应准确");
    }

    @Test
    void shouldSnapshotContextExtensionsBeforeAsyncHandoff() {
        Map<String, String> extensions = new LinkedHashMap<>();
        extensions.put(" clientId ", " client-1 ");
        XffCaptureAuditContextProvider provider = () ->
                new XffCaptureAuditContext("request-1", "trace-1", extensions);
        XffCaptureAuditDocumentFactory factory = new XffCaptureAuditDocumentFactory(
                properties("test-service"), new MockEnvironment(), Optional.of(provider));

        XffCaptureAuditDocument document = factory.create(event());
        extensions.put("changed", "value");

        log.info("Factory 扩展字段键：{}", document.getExtensions().keySet());
        assertEquals(Collections.singletonMap("clientId", "client-1"), document.getExtensions(),
                "异步交接前应完成扩展字段快照");
        assertThrows(UnsupportedOperationException.class,
                () -> document.getExtensions().put("another", "value"),
                "文档扩展字段必须不可修改");
        assertFalse(document.toString().contains("client-1"),
                "文档安全字符串不应泄漏扩展值");
    }

    @Test
    void shouldPreferExplicitApplicationNameAndAllowMissingContext() {
        SimpleXffCaptureAuditListenerProperties properties = properties(" explicit-service ");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "spring-service");
        XffCaptureAuditDocumentFactory factory =
                new XffCaptureAuditDocumentFactory(properties, environment, Optional.<XffCaptureAuditContextProvider>empty());

        XffCaptureAuditDocument document = factory.create(event());

        log.info("显式应用名：{}", document.getApplicationName());
        assertEquals("explicit-service", document.getApplicationName(),
                "显式 applicationName 应优先");
        assertNull(document.getRequestId(), "没有 Provider 时 requestId 应为空");
        assertNull(document.getTraceId(), "没有 Provider 时 traceId 应为空");
        assertTrue(document.getExtensions().isEmpty(), "没有 Provider 时扩展字段应为空");
    }

    @Test
    void shouldIsolateProviderFailureAndKeepNetworkFacts(CapturedOutput output) {
        SimpleXffCaptureAuditListenerProperties properties = properties("test-service");
        XffCaptureAuditContextProvider provider = () -> {
            throw new RuntimeException("context-provider-failure-marker");
        };
        XffCaptureAuditDocumentFactory factory = new XffCaptureAuditDocumentFactory(
                properties, new MockEnvironment(), Optional.of(provider));

        XffCaptureAuditDocument document = assertDoesNotThrow(() -> factory.create(event()),
                "Provider 失败不能丢失网络事实审计");

        log.info("Provider 失败后 XFF IP 数量：{}，异常堆栈输出长度：{}",
                document.getXffIpList().size(), output.getOut().length());
        assertTrue(output.getOut().contains("context-provider-failure-marker"),
                "Context Provider 异常必须进入完整堆栈日志");
        assertNull(document.getRequestId(), "Provider 失败时 requestId 应为空");
        assertNull(document.getTraceId(), "Provider 失败时 traceId 应为空");
        assertTrue(document.getExtensions().isEmpty(), "Provider 失败时扩展字段不能投影");
        assertFalse(document.getXffIpList().isEmpty(), "网络事实仍应生成类型化 IP");
    }

    @Test
    void shouldKeepInvalidApplicationRemoteAddressOnlyAsRawValue() {
        XffCaptureEvent source = event();
        XffCaptureEvent invalidRemote = new XffCaptureEvent(source.getEventId(), source.getOccurredAt(),
                source.getRequestMethod(), source.getRequestUri(), "non-ip-value", source.getSnapshot());
        XffCaptureAuditDocumentFactory factory = new XffCaptureAuditDocumentFactory(
                properties("test-service"), new MockEnvironment(), Optional.<XffCaptureAuditContextProvider>empty());

        XffCaptureAuditDocument document = factory.create(invalidRemote);

        log.info("非法应用远端地址投影：raw={}，ip={}",
                document.getApplicationRawRemoteAddress(), document.getApplicationRemoteIp());
        assertEquals("non-ip-value", document.getApplicationRawRemoteAddress(),
                "原始远端地址不得丢失");
        assertNull(document.getApplicationRemoteIp(), "非法值不能进入 ES ip 字段");
    }

    private SimpleXffCaptureAuditListenerProperties properties(String applicationName) {
        SimpleXffCaptureAuditListenerProperties properties = new SimpleXffCaptureAuditListenerProperties();
        properties.setApplicationName(applicationName);
        return properties;
    }

    private XffCaptureEvent event() {
        XffChain chain = new XffChain(true,
                Collections.singletonList("8.8.8.8, 10.0.0.1, unknown, , 8.8.8.8, 2001:4860:4860:0:0:0:0:8888"),
                Arrays.asList("8.8.8.8", "10.0.0.1", "unknown", "", "8.8.8.8",
                        "2001:4860:4860:0:0:0:0:8888"));
        HeaderValueSnapshot host = new HeaderValueSnapshot(true,
                Collections.singletonList("api.example.test"));
        HeaderValueSnapshot absent = new HeaderValueSnapshot(false, Collections.<String>emptyList());
        ForwardedContext context = new ForwardedContext(host, absent, absent, absent, absent);
        return new XffCaptureEvent("event-1", Instant.parse("2026-08-19T00:00:00.123Z"),
                "POST", "/api/test", "10.20.30.40", new XffCaptureSnapshot(chain, context));
    }
}

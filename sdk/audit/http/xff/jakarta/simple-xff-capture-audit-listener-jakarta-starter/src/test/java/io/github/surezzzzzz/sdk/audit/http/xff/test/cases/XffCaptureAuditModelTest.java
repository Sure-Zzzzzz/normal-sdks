package io.github.surezzzzzz.sdk.audit.http.xff.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.configuration.SimpleXffCaptureAuditListenerProperties;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.ErrorCode;
import io.github.surezzzzzz.sdk.audit.http.xff.context.XffCaptureAuditContext;
import io.github.surezzzzzz.sdk.audit.http.xff.exception.XffCaptureAuditValidationException;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import io.github.surezzzzzz.sdk.audit.http.xff.support.XffCaptureAuditApplicationNameHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XFF Capture 审计模型和配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class XffCaptureAuditModelTest {

    @Test
    void shouldDefensivelyCopyListsAndHideSensitiveToString() {
        List<String> hostList = new ArrayList<>(Collections.singletonList("secret.example.test"));
        List<String> rawList = new ArrayList<>(Collections.singletonList("8.8.8.8"));
        XffCaptureAuditDocument document = document(hostList, rawList,
                Collections.singletonList("8.8.8.8"), Collections.singletonList("8.8.8.8"));
        hostList.clear();
        rawList.clear();

        log.info("文档安全字符串：{}", document);
        assertEquals(Collections.singletonList("secret.example.test"), document.getHostList(),
                "Host 应防御性复制");
        assertEquals(Collections.singletonList("8.8.8.8"), document.getXffRawList(),
                "XFF 原始链应防御性复制");
        assertThrows(UnsupportedOperationException.class,
                () -> document.getPublicIpList().add("1.1.1.1"), "IP 列表应不可修改");
        assertFalse(document.toString().contains("secret.example.test"), "toString 不应泄漏 Host");
        assertFalse(document.toString().contains("8.8.8.8"), "toString 不应泄漏 IP");
        assertFalse(document.toString().contains("/api/test"), "toString 不应泄漏 URI");
    }

    @Test
    void shouldRejectInvalidDocumentStates() {
        XffCaptureAuditValidationException absentState = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> document(false, Collections.singletonList("8.8.8.8"),
                        Collections.singletonList("8.8.8.8"), Collections.singletonList("8.8.8.8")),
                "XFF absent 与非空原始链冲突时应拒绝");
        XffCaptureAuditValidationException presentState = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> document(true, Collections.<String>emptyList(),
                        Collections.<String>emptyList(), Collections.<String>emptyList()),
                "XFF present 与空原始链冲突时应拒绝");
        XffCaptureAuditValidationException subsetState = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> document(true, Collections.singletonList("8.8.8.8"),
                        Collections.singletonList("10.0.0.1"), Collections.singletonList("8.8.8.8")),
                "公网列表不是全部 IP 子集时应拒绝");
        XffCaptureAuditValidationException invalidIpState = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> document(true, Collections.singletonList("unknown"),
                        Collections.singletonList("unknown"), Collections.<String>emptyList()),
                "非法值进入 IP 字段时应拒绝");
        XffCaptureAuditValidationException nonNormalizedIpState = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> document(true, Collections.singletonList("2001:0db8:0:0:0:0:0:1"),
                        Collections.singletonList("2001:0db8:0:0:0:0:0:1"), Collections.<String>emptyList()),
                "未规范化 IP 进入 IP 字段时应拒绝");

        log.info("文档非法状态错误码：absent={}, present={}, subset={}, invalidIp={}, nonNormalizedIp={}",
                absentState.getErrorCode(), presentState.getErrorCode(), subsetState.getErrorCode(),
                invalidIpState.getErrorCode(), nonNormalizedIpState.getErrorCode());
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, absentState.getErrorCode(),
                "absent 状态错误码应准确");
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, presentState.getErrorCode(),
                "present 状态错误码应准确");
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, subsetState.getErrorCode(),
                "子集状态错误码应准确");
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, invalidIpState.getErrorCode(),
                "非法 IP 状态错误码应准确");
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, nonNormalizedIpState.getErrorCode(),
                "未规范化 IP 状态错误码应准确");
    }

    @Test
    void shouldNormalizeOptionalAuditContextWithoutLeakingToString() {
        XffCaptureAuditContext context = new XffCaptureAuditContext(" request-secret ", " trace-secret ");
        XffCaptureAuditContext empty = new XffCaptureAuditContext(" ", null);

        log.info("上下文安全字符串：{}", context);
        assertEquals("request-secret", context.getRequestId(), "requestId 应 trim");
        assertEquals("trace-secret", context.getTraceId(), "traceId 应 trim");
        assertNull(empty.getRequestId(), "空白 requestId 应规范化为 null");
        assertNull(empty.getTraceId(), "null traceId 应保持 null");
        assertFalse(context.toString().contains("request-secret"), "toString 不应泄漏 requestId");
        assertFalse(context.toString().contains("trace-secret"), "toString 不应泄漏 traceId");
    }

    @Test
    void shouldDefensivelyCopyAndValidateAuditContextExtensions() {
        Map<String, String> extensions = new LinkedHashMap<>();
        extensions.put(" clientId ", " client-1 ");
        XffCaptureAuditContext context = new XffCaptureAuditContext(
                " request-secret ", " trace-secret ", extensions);
        extensions.put("changed", "value");

        log.info("上下文扩展字段键：{}", context.getExtensions().keySet());
        assertEquals(Collections.singletonMap("clientId", "client-1"), context.getExtensions(),
                "上下文扩展字段应 trim 并完成防御性复制");
        assertThrows(UnsupportedOperationException.class,
                () -> context.getExtensions().put("another", "value"),
                "上下文扩展字段必须不可修改");
        assertFalse(context.toString().contains("client-1"),
                "toString 不应泄漏上下文扩展值");

        XffCaptureAuditValidationException blankKey = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> new XffCaptureAuditContext(null, null,
                        Collections.singletonMap(" ", "value")),
                "空白扩展键应拒绝");
        XffCaptureAuditValidationException blankValue = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> new XffCaptureAuditContext(null, null,
                        Collections.singletonMap("clientId", " ")),
                "空白扩展值应拒绝");
        Map<String, String> duplicateKeys = new LinkedHashMap<>();
        duplicateKeys.put("clientId", "one");
        duplicateKeys.put(" clientId ", "two");
        XffCaptureAuditValidationException duplicated = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> new XffCaptureAuditContext(null, null, duplicateKeys),
                "规范化后的重复扩展键应拒绝");

        log.info("上下文扩展非法错误码：blankKey={}，blankValue={}，duplicated={}",
                blankKey.getErrorCode(), blankValue.getErrorCode(), duplicated.getErrorCode());
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, blankKey.getErrorCode(),
                "空白扩展键错误码应准确");
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, blankValue.getErrorCode(),
                "空白扩展值错误码应准确");
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, duplicated.getErrorCode(),
                "重复扩展键错误码应准确");
    }

    @Test
    void shouldValidateAllExecutorBoundaries() {
        SimpleXffCaptureAuditListenerProperties properties = validProperties();
        properties.validate();

        SimpleXffCaptureAuditListenerProperties.Executor executor = properties.getExecutor();
        executor.setCoreSize(0);
        assertConfigInvalid(properties, "coreSize");
        executor.setCoreSize(2);
        executor.setMaxSize(1);
        assertConfigInvalid(properties, "maxSize");
        executor.setMaxSize(4);
        executor.setQueueCapacity(0);
        assertConfigInvalid(properties, "queueCapacity");
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(-1);
        assertConfigInvalid(properties, "keepAliveSeconds");
        executor.setKeepAliveSeconds(0);
        executor.setAwaitTerminationSeconds(0);
        assertConfigInvalid(properties, "awaitTerminationSeconds");

        log.info("执行器配置边界全部验证完成，最终 awaitTerminationSeconds={}",
                executor.getAwaitTerminationSeconds());
        assertEquals(0, executor.getAwaitTerminationSeconds(),
                "最后一个非法边界输入应保留为 0 并已被校验拒绝");
    }

    @Test
    void shouldResolveApplicationNameWithExplicitPrecedenceAndFailWhenMissing() {
        SimpleXffCaptureAuditListenerProperties explicit = validProperties();
        explicit.setApplicationName(" explicit-service ");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "spring-service");
        String explicitName = XffCaptureAuditApplicationNameHelper.resolve(explicit, environment);

        SimpleXffCaptureAuditListenerProperties fallback = validProperties();
        String fallbackName = XffCaptureAuditApplicationNameHelper.resolve(fallback, environment);
        XffCaptureAuditValidationException missing = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> XffCaptureAuditApplicationNameHelper.resolve(fallback, new MockEnvironment()),
                "应用名称全部缺失时应启动失败");

        log.info("应用名称解析：explicit={}，fallback={}，missingCode={}",
                explicitName, fallbackName, missing.getErrorCode());
        assertEquals("explicit-service", explicitName, "显式应用名应优先");
        assertEquals("spring-service", fallbackName, "应回退 Spring 应用名");
        assertEquals(ErrorCode.REQUIRED_VALUE_MISSING, missing.getErrorCode(),
                "缺失应用名错误码应准确");
    }

    private void assertConfigInvalid(SimpleXffCaptureAuditListenerProperties properties, String field) {
        XffCaptureAuditValidationException exception = assertThrows(
                XffCaptureAuditValidationException.class, properties::validate,
                field + " 非法时应抛配置异常");
        log.info("配置非法：field={}，errorCode={}", field, exception.getErrorCode());
        assertEquals(ErrorCode.CONFIG_VALUE_INVALID, exception.getErrorCode(),
                field + " 错误码应准确");
    }

    private SimpleXffCaptureAuditListenerProperties validProperties() {
        SimpleXffCaptureAuditListenerProperties properties = new SimpleXffCaptureAuditListenerProperties();
        return properties;
    }

    private XffCaptureAuditDocument document(List<String> hostList, List<String> rawList,
                                             List<String> ipList, List<String> publicIpList) {
        return new XffCaptureAuditDocument("event-1", "2026-08-19T00:00:00.000Z", "test-service",
                "request-secret", "trace-secret", "GET", "/api/test", hostList,
                true, rawList, ipList, publicIpList, "10.0.0.1", "10.0.0.1",
                "iana-2025-10-09");
    }

    private XffCaptureAuditDocument document(boolean present, List<String> rawList,
                                             List<String> ipList, List<String> publicIpList) {
        return new XffCaptureAuditDocument("event-1", "2026-08-19T00:00:00.000Z", "test-service",
                null, null, "GET", "/api/test", Arrays.asList("api.example.test"),
                present, rawList, ipList, publicIpList, null, null,
                "iana-2025-10-09");
    }
}

package io.github.surezzzzzz.sdk.audit.http.xff.test.cases;

import io.github.surezzzzzz.sdk.audit.http.xff.constant.ErrorCode;
import io.github.surezzzzzz.sdk.audit.http.xff.exception.XffCaptureAuditValidationException;
import io.github.surezzzzzz.sdk.audit.http.xff.model.XffCaptureAuditDocument;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XFF Capture 审计文档测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class XffCaptureAuditDocumentTest {

    @Test
    void shouldDefensivelyCopyLists() {
        List<String> hosts = new ArrayList<>(Collections.singletonList("test.example"));
        List<String> raw = new ArrayList<>(Collections.singletonList("8.8.8.8"));
        List<String> ips = new ArrayList<>(Collections.singletonList("8.8.8.8"));
        List<String> publicIps = new ArrayList<>(Collections.singletonList("8.8.8.8"));
        XffCaptureAuditDocument document = new XffCaptureAuditDocument("event-1",
                "2026-08-20T00:00:00.000Z", "test-service", null, null, "GET", "/test",
                hosts, true, raw, ips, publicIps, "10.0.0.1", "10.0.0.1", "iana-2025-10-09");
        hosts.add("changed.example");
        raw.set(0, "changed");

        log.info("文档集合快照：hostList={}，xffRawList={}", document.getHostList(), document.getXffRawList());
        assertEquals(Collections.singletonList("test.example"), document.getHostList(), "Host 必须完成防御性复制");
        assertEquals(Collections.singletonList("8.8.8.8"), document.getXffRawList(), "原始 XFF 必须完成防御性复制");
        assertThrows(UnsupportedOperationException.class,
                () -> document.getXffIpList().add("1.1.1.1"), "文档集合必须不可修改");
    }

    @Test
    void shouldDefensivelyCopyAndNormalizeExtensions() {
        Map<String, String> extensions = new LinkedHashMap<>();
        extensions.put(" clientId ", " client-1 ");
        XffCaptureAuditDocument document = new XffCaptureAuditDocument("event-extension",
                "2026-08-20T00:00:00.000Z", "test-service", null, null, "GET", "/test",
                Collections.singletonList("test.example"), true,
                Collections.singletonList("8.8.8.8"), Collections.singletonList("8.8.8.8"),
                Collections.singletonList("8.8.8.8"), "10.0.0.1", "10.0.0.1", "iana-2025-10-09",
                extensions);
        extensions.put("changed", "value");

        log.info("扩展字段快照键：{}", document.getExtensions().keySet());
        assertEquals(Collections.singletonMap("clientId", "client-1"), document.getExtensions(),
                "extensions 必须 trim 并完成防御性复制");
        assertThrows(UnsupportedOperationException.class,
                () -> document.getExtensions().put("another", "value"),
                "extensions 必须不可修改");
        assertFalse(document.toString().contains("client-1"),
                "toString 不应泄漏业务扩展值");
    }

    @Test
    void shouldUseEmptyExtensionsForLegacyConstructor() {
        XffCaptureAuditDocument document = document(true, Collections.singletonList("8.8.8.8"),
                Collections.singletonList("8.8.8.8"), Collections.singletonList("8.8.8.8"));

        assertTrue(document.getExtensions().isEmpty(), "旧构造器应默认空 extensions");
    }

    @Test
    void shouldRejectNullCollectionElement() {
        assertStateError(() -> document(true, Collections.singletonList("8.8.8.8"),
                Arrays.asList("8.8.8.8", null), Collections.singletonList("8.8.8.8")));
    }

    @Test
    void shouldRejectInvalidExtensions() {
        assertStateError(() -> documentWithExtensions(Collections.singletonMap(" ", "value")));
        assertStateError(() -> documentWithExtensions(Collections.singletonMap("clientId", " ")));
        Map<String, String> duplicateKeys = new LinkedHashMap<>();
        duplicateKeys.put("clientId", "one");
        duplicateKeys.put(" clientId ", "two");
        assertStateError(() -> documentWithExtensions(duplicateKeys));
    }

    @Test
    void shouldRejectAbsentXffWithAnyProjection() {
        XffCaptureAuditValidationException exception = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> document(false, Collections.<String>emptyList(),
                        Collections.singletonList("8.8.8.8"), Collections.singletonList("8.8.8.8")));

        log.info("XFF 缺失状态异常：errorCode={}，message={}", exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("xffIpList"));
    }

    @Test
    void shouldRejectPresentXffWithoutRawValues() {
        XffCaptureAuditValidationException exception = assertThrows(
                XffCaptureAuditValidationException.class,
                () -> document(true, Collections.<String>emptyList(),
                        Collections.<String>emptyList(), Collections.<String>emptyList()));

        log.info("XFF 存在但原始值为空的异常：errorCode={}，message={}",
                exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("xffRawList"));
    }

    @Test
    void shouldRejectInvalidTimeVersionAndIpProjection() {
        assertStateError(() -> document(false, Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                "2026-08-20T00:00:00.000+08:00", "iana-2025-10-09"));
        assertStateError(() -> document(false, Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                "2026-08-20T00:00:00.000Z", "iana-invalid"));
        assertStateError(() -> document(true, Collections.singletonList("10.0.0.1"),
                Collections.singletonList("10.0.0.1"), Collections.singletonList("10.0.0.1"),
                "2026-08-20T00:00:00.000Z", "iana-2025-10-09"));
        assertStateError(() -> document(true, Collections.singletonList("8.8.8.8"),
                Collections.singletonList("8.8.8.8"), Collections.singletonList("10.0.0.1"),
                "2026-08-20T00:00:00.000Z", "iana-2025-10-09"));
        assertStateError(() -> document(true, Collections.singletonList("8.8.8.8"),
                Arrays.asList("8.8.8.8", "8.8.8.8"), Collections.singletonList("8.8.8.8"),
                "2026-08-20T00:00:00.000Z", "iana-2025-10-09"));
        assertStateError(() -> document(true, Collections.singletonList("8.8.8.8"),
                Collections.singletonList("8.8.8.8"), Collections.singletonList("8.8.8.8"),
                "2026-08-20T00:00:00.000Z", "iana-2025-10-09",
                "2001:0db8:0:0:0:0:0:1"));
    }

    private void assertStateError(Runnable action) {
        XffCaptureAuditValidationException exception = assertThrows(
                XffCaptureAuditValidationException.class, action::run);
        log.info("非法文档状态异常：errorCode={}，message={}",
                exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID, exception.getErrorCode());
    }

    private XffCaptureAuditDocument document(boolean xffPresent, List<String> raw,
                                             List<String> ips, List<String> publicIps) {
        return document(xffPresent, raw, ips, publicIps,
                "2026-08-20T00:00:00.000Z", "iana-2025-10-09");
    }

    private XffCaptureAuditDocument document(boolean xffPresent, List<String> raw,
                                             List<String> ips, List<String> publicIps,
                                             String capturedTime, String classificationVersion) {
        return new XffCaptureAuditDocument("event-state", capturedTime, "test-service",
                null, null, "GET", "/test", Collections.singletonList("test.example"),
                xffPresent, raw, ips, publicIps, "10.0.0.1", "10.0.0.1", classificationVersion);
    }

    private XffCaptureAuditDocument document(boolean xffPresent, List<String> raw,
                                             List<String> ips, List<String> publicIps,
                                             String capturedTime, String classificationVersion,
                                             String applicationRemoteIp) {
        return new XffCaptureAuditDocument("event-state", capturedTime, "test-service",
                null, null, "GET", "/test", Collections.singletonList("test.example"),
                xffPresent, raw, ips, publicIps, "10.0.0.1", applicationRemoteIp,
                classificationVersion);
    }

    private XffCaptureAuditDocument documentWithExtensions(Map<String, String> extensions) {
        return new XffCaptureAuditDocument("event-extension", "2026-08-20T00:00:00.000Z",
                "test-service", null, null, "GET", "/test",
                Collections.singletonList("test.example"), true,
                Collections.singletonList("8.8.8.8"), Collections.singletonList("8.8.8.8"),
                Collections.singletonList("8.8.8.8"), "10.0.0.1", "10.0.0.1",
                "iana-2025-10-09", extensions);
    }
}

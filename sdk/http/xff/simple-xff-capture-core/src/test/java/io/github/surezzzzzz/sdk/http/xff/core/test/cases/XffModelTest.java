package io.github.surezzzzzz.sdk.http.xff.core.test.cases;

import io.github.surezzzzzz.sdk.http.xff.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.http.xff.core.constant.XffIpScope;
import io.github.surezzzzzz.sdk.http.xff.core.constant.XffIpVersion;
import io.github.surezzzzzz.sdk.http.xff.core.event.XffCaptureEvent;
import io.github.surezzzzzz.sdk.http.xff.core.exception.SimpleXffCaptureCoreException;
import io.github.surezzzzzz.sdk.http.xff.core.exception.XffCaptureValidationException;
import io.github.surezzzzzz.sdk.http.xff.core.model.*;
import io.github.surezzzzzz.sdk.http.xff.core.support.XffAddressHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XFF 公共模型契约测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class XffModelTest {

    @Test
    void shouldDefensivelyCopySourceLists() {
        List<String> rawHeaderList = new ArrayList<>(Collections.singletonList("192.0.2.1"));
        List<String> rawList = new ArrayList<>(Collections.singletonList("192.0.2.1"));
        XffChain chain = new XffChain(true, rawHeaderList, rawList);
        rawHeaderList.set(0, "changed");
        rawList.clear();

        log.info("防御性复制结果：rawSize={}, valueSize={}",
                chain.getRawHeaderList().size(), chain.getRawList().size());
        assertEquals(Collections.singletonList("192.0.2.1"), chain.getRawHeaderList(),
                "原始列表后续修改不应影响快照");
        assertEquals(Collections.singletonList("192.0.2.1"), chain.getRawList(),
                "值列表后续修改不应影响快照");
    }

    @Test
    void shouldPreserveValueSemanticsAcrossPublicModels() {
        Instant occurredAt = Instant.ofEpochMilli(1700000000000L);
        XffChain firstChain = new XffChain(false, Collections.<String>emptyList(), Collections.<String>emptyList());
        XffChain secondChain = new XffChain(false, Collections.<String>emptyList(), Collections.<String>emptyList());
        ForwardedContext firstContext = emptyForwardedContext();
        ForwardedContext secondContext = emptyForwardedContext();
        XffCaptureSnapshot firstSnapshot = new XffCaptureSnapshot(firstChain, firstContext);
        XffCaptureSnapshot secondSnapshot = new XffCaptureSnapshot(secondChain, secondContext);
        XffCaptureEvent firstEvent = new XffCaptureEvent("event-1", occurredAt, "GET", "/resource/item",
                "127.0.0.1", firstSnapshot);
        XffCaptureEvent secondEvent = new XffCaptureEvent("event-1", occurredAt, "GET", "/resource/item",
                "127.0.0.1", secondSnapshot);
        XffAddressInfo firstAddress = XffAddressHelper.classify("8.8.8.8");
        XffAddressInfo secondAddress = XffAddressHelper.classify("8.8.8.8");

        log.info("验证公共模型值语义：eventEqual={}，addressEqual={}",
                firstEvent.equals(secondEvent), firstAddress.equals(secondAddress));
        assertEquals(firstChain, secondChain, "XFF 链相同字段应相等");
        assertEquals(firstChain.hashCode(), secondChain.hashCode(), "XFF 链相等对象 hashCode 应一致");
        assertEquals(firstContext, secondContext, "转发上下文相同字段应相等");
        assertEquals(firstContext.hashCode(), secondContext.hashCode(), "转发上下文 hashCode 应一致");
        assertEquals(firstSnapshot, secondSnapshot, "完整快照相同字段应相等");
        assertEquals(firstSnapshot.hashCode(), secondSnapshot.hashCode(), "完整快照 hashCode 应一致");
        assertEquals(firstEvent, secondEvent, "事件相同字段应相等");
        assertEquals(firstEvent.hashCode(), secondEvent.hashCode(), "事件 hashCode 应一致");
        assertEquals(firstAddress, secondAddress, "地址分类结果相同字段应相等");
        assertEquals(firstAddress.hashCode(), secondAddress.hashCode(), "地址分类结果 hashCode 应一致");
    }

    @Test
    void shouldHandleEnumBoundaryValuesAndIsolateCodeArrays() {
        String[] scopeCodes = XffIpScope.getAllCodes();
        String[] versionCodes = XffIpVersion.getAllCodes();
        scopeCodes[0] = "changed";
        versionCodes[0] = "changed";

        log.info("验证枚举边界：scopeInvalid={}，versionInvalid={}",
                XffIpScope.fromCode("private"), XffIpVersion.fromCode("ipv4"));
        assertNull(XffIpScope.fromCode(null), "范围 null code 应返回 null");
        assertNull(XffIpScope.fromCode("private"), "范围 code 应区分大小写");
        assertFalse(XffIpScope.isValid("unknown"), "未知范围 code 不应有效");
        assertEquals("PUBLIC", XffIpScope.getAllCodes()[0], "范围 code 数组不应被外部修改污染");
        assertNull(XffIpVersion.fromCode(null), "版本 null code 应返回 null");
        assertNull(XffIpVersion.fromCode("ipv4"), "版本 code 应区分大小写");
        assertFalse(XffIpVersion.isValid("unknown"), "未知版本 code 不应有效");
        assertEquals("IPV4", XffIpVersion.getAllCodes()[0], "版本 code 数组不应被外部修改污染");
    }

    @Test
    void shouldPreserveExceptionCodeMessageAndCause() {
        Throwable cause = new IllegalStateException("cause");
        SimpleXffCaptureCoreException coreException = new SimpleXffCaptureCoreException(
                "CORE_TEST", "core message", cause);
        XffCaptureValidationException validationException = new XffCaptureValidationException(
                ErrorCode.REQUIRED_VALUE_MISSING, "validation message", cause);

        log.info("验证异常契约：coreCode={}，validationCode={}",
                coreException.getErrorCode(), validationException.getErrorCode());
        assertEquals("CORE_TEST", coreException.getErrorCode(), "Core 异常错误码应保留");
        assertEquals("core message", coreException.getMessage(), "Core 异常消息应保留");
        assertSame(cause, coreException.getCause(), "Core 异常原因链应保留");
        assertEquals(ErrorCode.REQUIRED_VALUE_MISSING, validationException.getErrorCode(),
                "校验异常错误码应保留");
        assertEquals("validation message", validationException.getMessage(), "校验异常消息应保留");
        assertSame(cause, validationException.getCause(), "校验异常原因链应保留");
    }

    @Test
    void shouldRejectInvalidChainStatesWithModuleErrorCode() {
        log.info("验证 XFF 快照非法状态");
        XffCaptureValidationException absentException = assertThrows(XffCaptureValidationException.class,
                () -> new XffChain(false, Collections.singletonList("value"), Collections.singletonList("value")),
                "Header 不存在时非空列表应被拒绝");
        XffCaptureValidationException presentException = assertThrows(XffCaptureValidationException.class,
                () -> new XffChain(true, Collections.<String>emptyList(), Collections.<String>emptyList()),
                "Header 存在时空列表应被拒绝");
        XffCaptureValidationException nullElementException = assertThrows(XffCaptureValidationException.class,
                () -> new XffChain(true, Collections.singletonList(null), Collections.singletonList("value")),
                "列表 null 元素应被拒绝");

        log.info("非法状态错误码：absent={}, present={}, nullElement={}",
                absentException.getErrorCode(), presentException.getErrorCode(), nullElementException.getErrorCode());
        assertEquals(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID, absentException.getErrorCode(),
                "Header 缺失状态错误码应准确");
        assertEquals(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID, presentException.getErrorCode(),
                "Header 存在状态错误码应准确");
        assertEquals(ErrorCode.REQUIRED_VALUE_MISSING, nullElementException.getErrorCode(),
                "null 元素错误码应准确");
    }

    @Test
    void shouldExposeCompleteEventFactsWithoutLeakingSensitiveToString() {
        Instant occurredAt = Instant.ofEpochMilli(1700000000000L);
        XffChain chain = new XffChain(true, Collections.singletonList("192.0.2.1"),
                Collections.singletonList("192.0.2.1"));
        HeaderValueSnapshot host = new HeaderValueSnapshot(true,
                Collections.singletonList("secret-host.example.test"));
        HeaderValueSnapshot absent = new HeaderValueSnapshot(false, Collections.<String>emptyList());
        ForwardedContext forwardedContext = new ForwardedContext(host, absent, absent, absent, absent);
        XffCaptureEvent event = new XffCaptureEvent("event-1", occurredAt, "GET",
                "/resource/secret-id", "127.0.0.1", new XffCaptureSnapshot(chain, forwardedContext));

        log.info("事件安全字符串：{}", event);
        assertEquals("event-1", event.getEventId(), "事件 ID 应完整保留");
        assertEquals(occurredAt, event.getOccurredAt(), "事件时间应完整保留");
        assertEquals("GET", event.getRequestMethod(), "HTTP 方法应完整保留");
        assertEquals("/resource/secret-id", event.getRequestUri(), "请求 URI 应完整保留");
        assertEquals("127.0.0.1", event.getApplicationRawRemoteAddress(), "应用可见原始远端地址应完整保留");
        assertSame(chain, event.getXffChain(), "事件应持有同一不可变 XFF 快照");
        assertSame(forwardedContext, event.getForwardedContext(), "事件应持有同一不可变转发上下文");
        assertFalse(event.toString().contains("secret-id"), "toString 不应泄漏 URI");
        assertFalse(event.toString().contains("127.0.0.1"), "toString 不应泄漏应用可见远端地址");
        assertFalse(event.toString().contains("192.0.2.1"), "toString 不应泄漏 XFF 链");
        assertFalse(event.toString().contains("secret-host"), "toString 不应泄漏转发 Header");
    }

    @Test
    void shouldRejectInvalidEventFields() {
        XffChain chain = new XffChain(false, Collections.<String>emptyList(), Collections.<String>emptyList());
        log.info("验证事件必填字段契约");

        ForwardedContext context = emptyForwardedContext();
        assertValidation(() -> new XffCaptureEvent(null, Instant.now(), "GET", "/", null, new XffCaptureSnapshot(chain, context)),
                "eventId");
        assertValidation(() -> new XffCaptureEvent("event", null, "GET", "/", null, new XffCaptureSnapshot(chain, context)),
                "occurredAt");
        assertValidation(() -> new XffCaptureEvent("event", Instant.now(), " ", "/", null, new XffCaptureSnapshot(chain, context)),
                "requestMethod");
        assertValidation(() -> new XffCaptureEvent("event", Instant.now(), "GET", " ", null, new XffCaptureSnapshot(chain, context)),
                "requestUri");
        assertValidation(() -> new XffCaptureEvent("event", Instant.now(), "GET", "/", null, null),
                "snapshot");
    }

    @Test
    void shouldKeepForwardedHeaderSnapshotsImmutableAndExplicit() {
        List<String> hostValues = new ArrayList<>(Collections.singletonList("service.example.test"));
        HeaderValueSnapshot host = new HeaderValueSnapshot(true, hostValues);
        HeaderValueSnapshot absent = new HeaderValueSnapshot(false, Collections.<String>emptyList());
        ForwardedContext context = new ForwardedContext(host, absent, absent, absent, absent);
        XffChain chain = new XffChain(false, Collections.<String>emptyList(), Collections.<String>emptyList());
        XffCaptureSnapshot snapshot = new XffCaptureSnapshot(chain, context);
        hostValues.clear();

        log.info("入口转发上下文：hostPresent={}，xRealIpPresent={}",
                context.getHost().isPresent(), context.getXRealIp().isPresent());
        assertEquals(Collections.singletonList("service.example.test"), context.getHost().getRawValueList(),
                "Header 快照应防御性复制原始列表");
        assertFalse(context.getXRealIp().isPresent(), "未出现的 Header 应显式记录 absent");
        assertSame(chain, snapshot.getXffChain(), "完整快照应持有 XFF 链");
        assertSame(context, snapshot.getForwardedContext(), "完整快照应持有转发上下文");
        assertThrows(UnsupportedOperationException.class,
                () -> context.getHost().getRawValueList().add("changed"),
                "Header 原始值列表应不可修改");
        assertFalse(context.toString().contains("service.example.test"),
                "转发上下文 toString 不应泄漏 Header 内容");
    }

    @Test
    void shouldRejectInvalidForwardedModels() {
        HeaderValueSnapshot absent = new HeaderValueSnapshot(false, Collections.<String>emptyList());
        XffChain chain = new XffChain(false, Collections.<String>emptyList(), Collections.<String>emptyList());

        XffCaptureValidationException absentState = assertThrows(XffCaptureValidationException.class,
                () -> new HeaderValueSnapshot(false, Collections.singletonList("value")),
                "Header absent 但有值时应拒绝");
        XffCaptureValidationException presentState = assertThrows(XffCaptureValidationException.class,
                () -> new HeaderValueSnapshot(true, Collections.<String>emptyList()),
                "Header present 但无值时应拒绝");
        XffCaptureValidationException nullContextValue = assertThrows(XffCaptureValidationException.class,
                () -> new ForwardedContext(null, absent, absent, absent, absent),
                "转发上下文任一快照为空时应拒绝");
        XffCaptureValidationException nullSnapshotChain = assertThrows(XffCaptureValidationException.class,
                () -> new XffCaptureSnapshot(null, emptyForwardedContext()),
                "完整快照缺少 XFF 链时应拒绝");
        XffCaptureValidationException nullSnapshotContext = assertThrows(XffCaptureValidationException.class,
                () -> new XffCaptureSnapshot(chain, null),
                "完整快照缺少转发上下文时应拒绝");

        log.info("转发模型非法状态错误码：absent={}, present={}, context={}, chain={}, snapshot={}",
                absentState.getErrorCode(), presentState.getErrorCode(), nullContextValue.getErrorCode(),
                nullSnapshotChain.getErrorCode(), nullSnapshotContext.getErrorCode());
        assertEquals(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID, absentState.getErrorCode(),
                "absent 状态错误码应准确");
        assertEquals(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID, presentState.getErrorCode(),
                "present 状态错误码应准确");
        assertEquals(ErrorCode.REQUIRED_VALUE_MISSING, nullContextValue.getErrorCode(),
                "转发上下文字段缺失错误码应准确");
        assertEquals(ErrorCode.REQUIRED_VALUE_MISSING, nullSnapshotChain.getErrorCode(),
                "完整快照 XFF 链缺失错误码应准确");
        assertEquals(ErrorCode.REQUIRED_VALUE_MISSING, nullSnapshotContext.getErrorCode(),
                "完整快照转发上下文缺失错误码应准确");
    }

    @Test
    void shouldRejectInconsistentAddressInfoState() {
        XffCaptureValidationException validWithoutIp = assertThrows(XffCaptureValidationException.class,
                () -> new XffAddressInfo(true, null, XffIpVersion.IPV4, XffIpScope.PUBLIC),
                "合法标记缺少规范化 IP 时应拒绝");
        XffCaptureValidationException invalidWithIp = assertThrows(XffCaptureValidationException.class,
                () -> new XffAddressInfo(false, "8.8.8.8", XffIpVersion.IPV4, XffIpScope.INVALID),
                "非法标记携带 IP 字段时应拒绝");

        log.info("地址分类状态异常错误码：validWithoutIp={}，invalidWithIp={}",
                validWithoutIp.getErrorCode(), invalidWithIp.getErrorCode());
        assertEquals(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID, validWithoutIp.getErrorCode(),
                "合法状态不一致错误码应准确");
        assertEquals(ErrorCode.CAPTURE_SNAPSHOT_STATE_INVALID, invalidWithIp.getErrorCode(),
                "非法状态不一致错误码应准确");
    }

    private ForwardedContext emptyForwardedContext() {
        HeaderValueSnapshot absent = new HeaderValueSnapshot(false, Collections.<String>emptyList());
        return new ForwardedContext(absent, absent, absent, absent, absent);
    }

    private void assertValidation(Runnable action, String field) {
        XffCaptureValidationException exception = assertThrows(XffCaptureValidationException.class, action::run,
                field + " 非法时应抛模块校验异常");
        log.info("字段校验失败：field={}, errorCode={}", field, exception.getErrorCode());
        assertEquals(ErrorCode.REQUIRED_VALUE_MISSING, exception.getErrorCode(), field + " 错误码应准确");
        assertTrue(exception.getMessage().contains(field), field + " 错误消息应包含字段名");
    }
}

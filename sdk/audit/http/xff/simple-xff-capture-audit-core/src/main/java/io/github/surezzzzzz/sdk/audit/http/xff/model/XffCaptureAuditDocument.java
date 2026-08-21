package io.github.surezzzzzz.sdk.audit.http.xff.model;

import io.github.surezzzzzz.sdk.audit.http.xff.constant.ErrorCode;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.audit.http.xff.constant.SimpleXffCaptureAuditConstant;
import io.github.surezzzzzz.sdk.audit.http.xff.exception.XffCaptureAuditValidationException;
import io.github.surezzzzzz.sdk.http.xff.core.constant.SimpleXffCaptureCoreConstant;
import io.github.surezzzzzz.sdk.http.xff.core.constant.XffIpScope;
import io.github.surezzzzzz.sdk.http.xff.core.model.RequestDataSnapshot;
import io.github.surezzzzzz.sdk.http.xff.core.model.XffAddressInfo;
import io.github.surezzzzzz.sdk.http.xff.core.support.XffAddressHelper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * XFF Capture 最小审计文档。
 *
 * <p>原始 XFF 链与类型化 IP 投影分开保存；无效值不会进入 IP 字段。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = {"requestId", "traceId", "requestUri", "hostList", "xffRawList",
        "xffIpList", "publicIpList", "applicationRawRemoteAddress", "applicationRemoteIp",
        "extensions", "requestData"})
public final class XffCaptureAuditDocument {

    private final String eventId;
    private final String capturedTime;
    private final String applicationName;
    private final String requestId;
    private final String traceId;
    private final String requestMethod;
    private final String requestUri;
    private final List<String> hostList;
    private final boolean xffPresent;
    private final List<String> xffRawList;
    private final List<String> xffIpList;
    private final List<String> publicIpList;
    private final String applicationRawRemoteAddress;
    private final String applicationRemoteIp;
    private final String classificationVersion;
    private final Map<String, String> extensions;
    private final RequestDataSnapshot requestData;

    /**
     * 创建不可变审计文档。
     *
     * @param eventId                     事件标识
     * @param capturedTime                捕获时间
     * @param applicationName             应用名称
     * @param requestId                   请求标识
     * @param traceId                     链路标识
     * @param requestMethod               请求方法
     * @param requestUri                  请求 URI
     * @param hostList                    Host 列表
     * @param xffPresent                  是否存在 XFF
     * @param xffRawList                  原始 XFF 列表
     * @param xffIpList                   规范化 XFF IP 列表
     * @param publicIpList                公网 IP 列表
     * @param applicationRawRemoteAddress 应用原始远端地址
     * @param applicationRemoteIp         应用远端 IP
     * @param classificationVersion       分类版本
     */
    public XffCaptureAuditDocument(String eventId, String capturedTime, String applicationName,
                                   String requestId, String traceId, String requestMethod,
                                   String requestUri, List<String> hostList, boolean xffPresent,
                                   List<String> xffRawList, List<String> xffIpList,
                                   List<String> publicIpList, String applicationRawRemoteAddress,
                                   String applicationRemoteIp, String classificationVersion) {
        this(eventId, capturedTime, applicationName, requestId, traceId, requestMethod, requestUri,
                hostList, xffPresent, xffRawList, xffIpList, publicIpList, applicationRawRemoteAddress,
                applicationRemoteIp, classificationVersion, Collections.<String, String>emptyMap(),
                RequestDataSnapshot.disabled());
    }

    /**
     * 创建带业务扩展的不可变审计文档。
     *
     * @param eventId                     事件标识
     * @param capturedTime                捕获时间
     * @param applicationName             应用名称
     * @param requestId                   请求标识
     * @param traceId                     链路标识
     * @param requestMethod               请求方法
     * @param requestUri                  请求 URI
     * @param hostList                    Host 列表
     * @param xffPresent                  是否存在 XFF
     * @param xffRawList                  原始 XFF 列表
     * @param xffIpList                   规范化 XFF IP 列表
     * @param publicIpList                公网 IP 列表
     * @param applicationRawRemoteAddress 应用原始远端地址
     * @param applicationRemoteIp         应用远端 IP
     * @param classificationVersion       分类版本
     * @param extensions                  业务扩展字段
     */
    public XffCaptureAuditDocument(String eventId, String capturedTime, String applicationName,
                                   String requestId, String traceId, String requestMethod,
                                   String requestUri, List<String> hostList, boolean xffPresent,
                                   List<String> xffRawList, List<String> xffIpList,
                                   List<String> publicIpList, String applicationRawRemoteAddress,
                                   String applicationRemoteIp, String classificationVersion,
                                   Map<String, String> extensions) {
        this(eventId, capturedTime, applicationName, requestId, traceId, requestMethod, requestUri,
                hostList, xffPresent, xffRawList, xffIpList, publicIpList, applicationRawRemoteAddress,
                applicationRemoteIp, classificationVersion, extensions, RequestDataSnapshot.disabled());
    }

    /**
     * 创建包含请求数据的不可变审计文档。
     *
     * @param eventId                     事件标识
     * @param capturedTime                捕获时间
     * @param applicationName             应用名称
     * @param requestId                   请求标识
     * @param traceId                     链路标识
     * @param requestMethod               请求方法
     * @param requestUri                  请求 URI
     * @param hostList                    Host 列表
     * @param xffPresent                  是否存在 XFF
     * @param xffRawList                  原始 XFF 列表
     * @param xffIpList                   规范化 XFF IP 列表
     * @param publicIpList                公网 IP 列表
     * @param applicationRawRemoteAddress 应用原始远端地址
     * @param applicationRemoteIp         应用远端 IP
     * @param classificationVersion       分类版本
     * @param extensions                  业务扩展字段
     * @param requestData                 请求数据快照
     */
    public XffCaptureAuditDocument(String eventId, String capturedTime, String applicationName,
                                   String requestId, String traceId, String requestMethod,
                                   String requestUri, List<String> hostList, boolean xffPresent,
                                   List<String> xffRawList, List<String> xffIpList,
                                   List<String> publicIpList, String applicationRawRemoteAddress,
                                   String applicationRemoteIp, String classificationVersion,
                                   Map<String, String> extensions, RequestDataSnapshot requestData) {
        this.eventId = requireText(eventId, SimpleXffCaptureAuditConstant.FIELD_EVENT_ID);
        this.capturedTime = requireText(capturedTime, SimpleXffCaptureAuditConstant.FIELD_CAPTURED_TIME);
        this.applicationName = requireText(applicationName, SimpleXffCaptureAuditConstant.FIELD_APPLICATION_NAME);
        this.requestId = normalize(requestId);
        this.traceId = normalize(traceId);
        this.requestMethod = requireText(requestMethod, SimpleXffCaptureAuditConstant.FIELD_REQUEST_METHOD);
        this.requestUri = requireText(requestUri, SimpleXffCaptureAuditConstant.FIELD_REQUEST_URI);
        this.hostList = immutableCopy(hostList, SimpleXffCaptureAuditConstant.FIELD_HOST_LIST);
        this.xffPresent = xffPresent;
        this.xffRawList = immutableCopy(xffRawList, SimpleXffCaptureAuditConstant.FIELD_XFF_RAW_LIST);
        this.xffIpList = immutableCopy(xffIpList, SimpleXffCaptureAuditConstant.FIELD_XFF_IP_LIST);
        this.publicIpList = immutableCopy(publicIpList, SimpleXffCaptureAuditConstant.FIELD_PUBLIC_IP_LIST);
        this.applicationRawRemoteAddress = applicationRawRemoteAddress;
        this.applicationRemoteIp = normalize(applicationRemoteIp);
        this.classificationVersion = requireText(classificationVersion,
                SimpleXffCaptureAuditConstant.FIELD_CLASSIFICATION_VERSION);
        this.extensions = immutableExtensionCopy(extensions);
        if (requestData == null) {
            throw new XffCaptureAuditValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, "requestData"));
        }
        this.requestData = requestData;
        validateState();
    }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new XffCaptureAuditValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, name));
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> immutableCopy(List<String> source, String name) {
        if (source == null) {
            throw new XffCaptureAuditValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING, name));
        }
        List<String> copy = new ArrayList<>(source.size());
        for (String value : source) {
            if (value == null) {
                throw new XffCaptureAuditValidationException(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID,
                        String.format(ErrorMessage.AUDIT_DOCUMENT_STATE_INVALID,
                                String.format(
                                        SimpleXffCaptureAuditConstant
                                                .DETAIL_COLLECTION_NULL_ELEMENT,
                                        name)));
            }
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }

    private static Map<String, String> immutableExtensionCopy(Map<String, String> source) {
        if (source == null) {
            throw new XffCaptureAuditValidationException(ErrorCode.REQUIRED_VALUE_MISSING,
                    String.format(ErrorMessage.REQUIRED_VALUE_MISSING,
                            SimpleXffCaptureAuditConstant.FIELD_EXTENSIONS));
        }
        Map<String, String> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = normalize(entry.getKey());
            String value = normalize(entry.getValue());
            if (key == null || value == null) {
                throw new XffCaptureAuditValidationException(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID,
                        String.format(ErrorMessage.AUDIT_DOCUMENT_STATE_INVALID,
                                String.format(SimpleXffCaptureAuditConstant.DETAIL_EXTENSION_TEXT_INVALID,
                                        key == null ? "键" : key)));
            }
            if (copy.containsKey(key)) {
                throw new XffCaptureAuditValidationException(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID,
                        String.format(ErrorMessage.AUDIT_DOCUMENT_STATE_INVALID,
                                String.format(
                                        SimpleXffCaptureAuditConstant
                                                .DETAIL_EXTENSION_DUPLICATED_KEY,
                                        key)));
            }
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private void validateState() {
        if (!xffPresent && (!xffRawList.isEmpty() || !xffIpList.isEmpty() || !publicIpList.isEmpty())) {
            throw stateException(SimpleXffCaptureAuditConstant.DETAIL_XFF_ABSENT_STATE_INVALID);
        }
        if (xffPresent && xffRawList.isEmpty()) {
            throw stateException(SimpleXffCaptureAuditConstant.DETAIL_XFF_PRESENT_STATE_INVALID);
        }
        if (!xffIpList.containsAll(publicIpList)) {
            throw stateException(SimpleXffCaptureAuditConstant.DETAIL_PUBLIC_IP_SUBSET_INVALID);
        }
        validateCapturedTime();
        validateClassificationVersion();
        validateIpList(xffIpList, SimpleXffCaptureAuditConstant.FIELD_XFF_IP_LIST, false);
        validateIpList(publicIpList, SimpleXffCaptureAuditConstant.FIELD_PUBLIC_IP_LIST, true);
        if (applicationRemoteIp != null) {
            validateNormalizedIp(applicationRemoteIp, SimpleXffCaptureAuditConstant.FIELD_APPLICATION_REMOTE_IP);
        }
    }

    private void validateCapturedTime() {
        try {
            Instant parsed = Instant.parse(capturedTime);
            if (!capturedTime.endsWith("Z") || parsed == null) {
                throw stateException(SimpleXffCaptureAuditConstant.DETAIL_CAPTURED_TIME_INVALID);
            }
        } catch (DateTimeParseException e) {
            throw stateException(SimpleXffCaptureAuditConstant.DETAIL_CAPTURED_TIME_INVALID);
        }
    }

    private void validateClassificationVersion() {
        if (!SimpleXffCaptureCoreConstant.IP_CLASSIFICATION_VERSION.equals(classificationVersion)) {
            throw stateException(SimpleXffCaptureAuditConstant.DETAIL_CLASSIFICATION_VERSION_INVALID);
        }
    }

    private void validateIpList(List<String> ipList, String field, boolean publicOnly) {
        if (new LinkedHashSet<>(ipList).size() != ipList.size()) {
            throw stateException(String.format(SimpleXffCaptureAuditConstant.DETAIL_IP_LIST_DUPLICATED, field));
        }
        for (String ip : ipList) {
            XffAddressInfo info = validateNormalizedIp(ip, field);
            if (publicOnly && info.getScope() != XffIpScope.PUBLIC) {
                throw stateException(SimpleXffCaptureAuditConstant.DETAIL_PUBLIC_IP_SCOPE_INVALID);
            }
        }
    }

    private XffAddressInfo validateNormalizedIp(String ip, String field) {
        XffAddressInfo addressInfo = XffAddressHelper.classify(ip);
        if (!addressInfo.isIpLiteral() || !ip.equals(addressInfo.getNormalizedIp())) {
            throw stateException(String.format(SimpleXffCaptureAuditConstant.DETAIL_NORMALIZED_IP_INVALID, field));
        }
        return addressInfo;
    }

    private XffCaptureAuditValidationException stateException(String detail) {
        return new XffCaptureAuditValidationException(ErrorCode.AUDIT_DOCUMENT_STATE_INVALID,
                String.format(ErrorMessage.AUDIT_DOCUMENT_STATE_INVALID, detail));
    }
}

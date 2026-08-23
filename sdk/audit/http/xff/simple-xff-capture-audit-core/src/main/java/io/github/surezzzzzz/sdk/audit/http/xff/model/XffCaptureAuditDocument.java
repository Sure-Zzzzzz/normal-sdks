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
 * XFF Capture 不可变审计文档。
 *
 * <p>完整构造器直接保存 Capture Event 暴露的原始 Header 事实，并将原始 XFF 链与类型化
 * IP 投影分开保存；无效值不会进入 IP 字段。旧构造器保留用于兼容历史调用方，新增原始
 * Header 字段在旧构造路径中使用空列表。</p>
 *
 * @author surezzzzzz
 */
@Getter
@EqualsAndHashCode
@ToString(exclude = {"requestId", "traceId", "requestUri", "hostList", "xffRawHeaderList",
        "xffRawList", "xRealIpList", "xForwardedHostList", "xForwardedPortList",
        "xForwardedProtoList", "xffIpList", "publicIpList", "applicationRawRemoteAddress",
        "applicationRemoteIp", "extensions", "requestData"})
public final class XffCaptureAuditDocument {

    /**
     * 事件唯一标识。
     */
    private final String eventId;

    /**
     * 捕获时间，必须为 UTC ISO-8601 格式。
     */
    private final String capturedTime;

    /**
     * 产生审计记录的应用名称。
     */
    private final String applicationName;

    /**
     * 可选请求标识。
     */
    private final String requestId;

    /**
     * 可选链路标识。
     */
    private final String traceId;

    /**
     * HTTP 请求方法。
     */
    private final String requestMethod;

    /**
     * 不含查询参数的请求 URI。
     */
    private final String requestUri;

    /**
     * Capture Event 中的原始 Host Header 值列表。
     */
    private final List<String> hostList;

    /**
     * Capture Event 中是否存在 XFF Header。
     */
    private final boolean xffPresent;

    /**
     * Capture Event 中的原始 XFF Header 值列表，保留同名 Header 边界与枚举顺序。
     * 完整投影中该列表与 {@link #xffPresent} 保持一致；旧构造器为兼容历史契约写入空列表。
     */
    private final List<String> xffRawHeaderList;

    /**
     * 将原始 XFF Header 值按逗号拆分后的有序值链。
     */
    private final List<String> xffRawList;

    /**
     * Capture Event 中的 X-Real-IP 原始值列表，不参与 XFF 链推断。
     */
    private final List<String> xRealIpList;

    /**
     * Capture Event 中的 X-Forwarded-Host 原始值列表，不与 Host 互相回填。
     */
    private final List<String> xForwardedHostList;

    /**
     * Capture Event 中的 X-Forwarded-Port 原始值列表，不作端口解析或校验。
     */
    private final List<String> xForwardedPortList;

    /**
     * Capture Event 中的 X-Forwarded-Proto 原始值列表，不作协议推断或校验。
     */
    private final List<String> xForwardedProtoList;

    /**
     * 规范化后的合法 XFF IP 列表。
     */
    private final List<String> xffIpList;

    /**
     * XFF IP 列表中的公网 IP 子集。
     */
    private final List<String> publicIpList;

    /**
     * 应用看到的原始远端地址，不用于恢复 XFF 或推断请求者身份。
     */
    private final String applicationRawRemoteAddress;

    /**
     * 规范化后的合法应用远端 IP，可为空。
     */
    private final String applicationRemoteIp;

    /**
     * IP 分类规则版本。
     */
    private final String classificationVersion;

    /**
     * 业务扩展字段，键和值均已完成规范化。
     */
    private final Map<String, String> extensions;

    /**
     * Capture Event 中的不可变请求数据快照。
     */
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
        this(eventId, capturedTime, applicationName, requestId, traceId, requestMethod, requestUri,
                hostList, xffPresent, Collections.<String>emptyList(), xffRawList,
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Collections.<String>emptyList(), Collections.<String>emptyList(), xffIpList,
                publicIpList, applicationRawRemoteAddress, applicationRemoteIp,
                classificationVersion, extensions, requestData, false);
    }

    /**
     * 创建完整投影 Capture Event 事实的不可变审计文档。
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
     * @param xffRawHeaderList            原始 XFF Header 值列表
     * @param xffRawList                  拆分后的原始 XFF 列表
     * @param xRealIpList                 X-Real-IP 原始值列表
     * @param xForwardedHostList          X-Forwarded-Host 原始值列表
     * @param xForwardedPortList          X-Forwarded-Port 原始值列表
     * @param xForwardedProtoList         X-Forwarded-Proto 原始值列表
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
                                   List<String> xffRawHeaderList, List<String> xffRawList,
                                   List<String> xRealIpList, List<String> xForwardedHostList,
                                   List<String> xForwardedPortList, List<String> xForwardedProtoList,
                                   List<String> xffIpList, List<String> publicIpList,
                                   String applicationRawRemoteAddress, String applicationRemoteIp,
                                   String classificationVersion, Map<String, String> extensions,
                                   RequestDataSnapshot requestData) {
        this(eventId, capturedTime, applicationName, requestId, traceId, requestMethod, requestUri,
                hostList, xffPresent, xffRawHeaderList, xffRawList, xRealIpList,
                xForwardedHostList, xForwardedPortList, xForwardedProtoList, xffIpList,
                publicIpList, applicationRawRemoteAddress, applicationRemoteIp,
                classificationVersion, extensions, requestData, true);
    }

    private XffCaptureAuditDocument(String eventId, String capturedTime, String applicationName,
                                    String requestId, String traceId, String requestMethod,
                                    String requestUri, List<String> hostList, boolean xffPresent,
                                    List<String> xffRawHeaderList, List<String> xffRawList,
                                    List<String> xRealIpList, List<String> xForwardedHostList,
                                    List<String> xForwardedPortList, List<String> xForwardedProtoList,
                                    List<String> xffIpList, List<String> publicIpList,
                                    String applicationRawRemoteAddress, String applicationRemoteIp,
                                    String classificationVersion, Map<String, String> extensions,
                                    RequestDataSnapshot requestData, boolean completeProjection) {
        this.eventId = requireText(eventId, SimpleXffCaptureAuditConstant.FIELD_EVENT_ID);
        this.capturedTime = requireText(capturedTime, SimpleXffCaptureAuditConstant.FIELD_CAPTURED_TIME);
        this.applicationName = requireText(applicationName, SimpleXffCaptureAuditConstant.FIELD_APPLICATION_NAME);
        this.requestId = normalize(requestId);
        this.traceId = normalize(traceId);
        this.requestMethod = requireText(requestMethod, SimpleXffCaptureAuditConstant.FIELD_REQUEST_METHOD);
        this.requestUri = requireText(requestUri, SimpleXffCaptureAuditConstant.FIELD_REQUEST_URI);
        this.hostList = immutableCopy(hostList, SimpleXffCaptureAuditConstant.FIELD_HOST_LIST);
        this.xffPresent = xffPresent;
        this.xffRawHeaderList = immutableCopy(xffRawHeaderList,
                SimpleXffCaptureAuditConstant.FIELD_XFF_RAW_HEADER_LIST);
        this.xffRawList = immutableCopy(xffRawList, SimpleXffCaptureAuditConstant.FIELD_XFF_RAW_LIST);
        this.xRealIpList = immutableCopy(xRealIpList, SimpleXffCaptureAuditConstant.FIELD_X_REAL_IP_LIST);
        this.xForwardedHostList = immutableCopy(xForwardedHostList,
                SimpleXffCaptureAuditConstant.FIELD_X_FORWARDED_HOST_LIST);
        this.xForwardedPortList = immutableCopy(xForwardedPortList,
                SimpleXffCaptureAuditConstant.FIELD_X_FORWARDED_PORT_LIST);
        this.xForwardedProtoList = immutableCopy(xForwardedProtoList,
                SimpleXffCaptureAuditConstant.FIELD_X_FORWARDED_PROTO_LIST);
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
        validateState(completeProjection);
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

    private void validateState(boolean completeProjection) {
        if (!xffPresent && (!xffRawList.isEmpty() || !xffIpList.isEmpty() || !publicIpList.isEmpty()
                || (completeProjection && !xffRawHeaderList.isEmpty()))) {
            throw stateException(SimpleXffCaptureAuditConstant.DETAIL_XFF_ABSENT_STATE_INVALID);
        }
        if (xffPresent && xffRawList.isEmpty()) {
            throw stateException(SimpleXffCaptureAuditConstant.DETAIL_XFF_PRESENT_STATE_INVALID);
        }
        if (completeProjection && xffPresent != !xffRawHeaderList.isEmpty()) {
            throw stateException(SimpleXffCaptureAuditConstant.DETAIL_COMPLETE_XFF_RAW_HEADER_LIST_INVALID);
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

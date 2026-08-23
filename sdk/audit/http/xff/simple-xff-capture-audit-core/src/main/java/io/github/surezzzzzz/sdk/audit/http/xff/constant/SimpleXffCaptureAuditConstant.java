package io.github.surezzzzzz.sdk.audit.http.xff.constant;

/**
 * Simple XFF Capture Audit Core 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleXffCaptureAuditConstant {

    /**
     * 应用名称字段名。
     */
    public static final String FIELD_APPLICATION_NAME = "applicationName";
    /**
     * 事件标识字段名。
     */
    public static final String FIELD_EVENT_ID = "eventId";
    /**
     * 捕获时间字段名。
     */
    public static final String FIELD_CAPTURED_TIME = "capturedTime";
    /**
     * 请求方法字段名。
     */
    public static final String FIELD_REQUEST_METHOD = "requestMethod";
    /**
     * 请求 URI 字段名。
     */
    public static final String FIELD_REQUEST_URI = "requestUri";
    /**
     * Host 列表字段名。
     */
    public static final String FIELD_HOST_LIST = "hostList";
    /**
     * XFF 原始 Header 列表字段名。
     */
    public static final String FIELD_XFF_RAW_HEADER_LIST = "xffRawHeaderList";
    /**
     * XFF 原始列表字段名。
     */
    public static final String FIELD_XFF_RAW_LIST = "xffRawList";
    /**
     * X-Real-IP 列表字段名。
     */
    public static final String FIELD_X_REAL_IP_LIST = "xRealIpList";
    /**
     * X-Forwarded-Host 列表字段名。
     */
    public static final String FIELD_X_FORWARDED_HOST_LIST = "xForwardedHostList";
    /**
     * X-Forwarded-Port 列表字段名。
     */
    public static final String FIELD_X_FORWARDED_PORT_LIST = "xForwardedPortList";
    /**
     * X-Forwarded-Proto 列表字段名。
     */
    public static final String FIELD_X_FORWARDED_PROTO_LIST = "xForwardedProtoList";
    /**
     * XFF IP 列表字段名。
     */
    public static final String FIELD_XFF_IP_LIST = "xffIpList";
    /**
     * 公网 IP 列表字段名。
     */
    public static final String FIELD_PUBLIC_IP_LIST = "publicIpList";
    /**
     * 分类版本字段名。
     */
    public static final String FIELD_CLASSIFICATION_VERSION = "classificationVersion";
    /**
     * 请求标识字段名。
     */
    public static final String FIELD_REQUEST_ID = "requestId";
    /**
     * 链路标识字段名。
     */
    public static final String FIELD_TRACE_ID = "traceId";
    /**
     * 应用原始远端地址字段名。
     */
    public static final String FIELD_APPLICATION_RAW_REMOTE_ADDRESS = "applicationRawRemoteAddress";
    /**
     * 应用远端 IP 字段名。
     */
    public static final String FIELD_APPLICATION_REMOTE_IP = "applicationRemoteIp";
    /**
     * XFF 存在标识字段名。
     */
    public static final String FIELD_XFF_PRESENT = "xffPresent";
    /**
     * 业务扩展字段名。
     */
    public static final String FIELD_EXTENSIONS = "extensions";
    /**
     * 业务扩展键值非法详情模板。
     */
    public static final String DETAIL_EXTENSION_TEXT_INVALID =
            "extensions 的 %s 和值必须是非空文本";
    /**
     * 业务扩展重复键非法详情模板。
     */
    public static final String DETAIL_EXTENSION_DUPLICATED_KEY =
            "extensions 不能包含规范化后的重复键：%s";
    /**
     * XFF 缺失状态非法详情。
     */
    public static final String DETAIL_XFF_ABSENT_STATE_INVALID =
            "XFF Header 不存在时 xffRawHeaderList、xffRawList、xffIpList 和 publicIpList 必须为空";
    /**
     * XFF 存在状态非法详情。
     */
    public static final String DETAIL_XFF_PRESENT_STATE_INVALID =
            "XFF Header 存在时 xffRawList 必须非空";
    /**
     * 完整投影 XFF 原始 Header 缺失详情。
     */
    public static final String DETAIL_COMPLETE_XFF_RAW_HEADER_LIST_INVALID =
            "完整 XFF 投影时 xffRawHeaderList 必须与 XFF Header 存在状态一致";
    /**
     * 公网列表子集关系非法详情。
     */
    public static final String DETAIL_PUBLIC_IP_SUBSET_INVALID =
            "publicIpList 必须是 xffIpList 的子集";
    /**
     * 规范化 IP 非法详情模板。
     */
    public static final String DETAIL_NORMALIZED_IP_INVALID = "%s 必须只包含规范化合法 IP";
    /**
     * 集合包含空元素详情模板。
     */
    public static final String DETAIL_COLLECTION_NULL_ELEMENT = "%s 不能包含 null";
    /**
     * IP 列表重复详情模板。
     */
    public static final String DETAIL_IP_LIST_DUPLICATED = "%s 不能包含重复 IP";
    /**
     * 公网列表范围非法详情。
     */
    public static final String DETAIL_PUBLIC_IP_SCOPE_INVALID = "publicIpList 只能包含 PUBLIC 地址";
    /**
     * 捕获时间非法详情。
     */
    public static final String DETAIL_CAPTURED_TIME_INVALID = "capturedTime 必须是 UTC ISO-8601 date_time";
    /**
     * 分类版本非法详情。
     */
    public static final String DETAIL_CLASSIFICATION_VERSION_INVALID = "classificationVersion 必须与 Core 分类版本一致";

    private SimpleXffCaptureAuditConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}

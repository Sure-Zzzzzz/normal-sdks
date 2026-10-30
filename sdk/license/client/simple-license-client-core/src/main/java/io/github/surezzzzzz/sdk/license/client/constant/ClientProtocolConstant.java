package io.github.surezzzzzz.sdk.license.client.constant;

/**
 * 判定响应的安全协议错误消息，不拼接响应内容。
 *
 * @author surezzzzzz
 */
public final class ClientProtocolConstant {

    public static final long SAFE_INTEGER_MAX = 9007199254740991L;
    public static final long EPOCH_SECONDS_MAX = 253402300799L;
    public static final int RESERVATION_REFERENCES_MAX = 256;
    public static final int TRUST_MATERIAL_PARTS_MAX = 256;
    public static final int IDENTIFIER_MAX_LENGTH = 128;
    public static final int SELECTION_SIZE_DEFAULT = 20;
    public static final int SELECTION_SIZE_MAX = 100;

    public static final String EMPTY_RESPONSE = "空判定响应";
    public static final String INVALID_FIELD = "判定响应字段缺失或类型非法：";
    public static final String UNKNOWN_FIELD = "判定响应含未知字段";
    public static final String INVALID_PHASE = "判定响应商业阶段非法";
    public static final String INVALID_REASON = "判定响应原因分类非法";
    public static final String INVALID_OUTCOME = "判定结论与阶段或原因分类不一致";
    public static final String INVALID_BINDING = "判定响应对象绑定非法或与请求不一致";

    private ClientProtocolConstant() {
    }
}

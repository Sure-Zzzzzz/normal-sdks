package io.github.surezzzzzz.sdk.license.server.constant;

/**
 * 履约关联的稳定约束与错误消息。
 *
 * @author surezzzzzz
 */
public final class FulfillmentConstant {

    public static final String GRANT_ID_PREFIX = "gr-";
    public static final String INVALID_REFERENCE = "履约编号及授权映射不能为空或超过长度上限";
    public static final String AMBIGUOUS_GRANT = "同一履约关联多个授权，需人工核对";
    public static final String BROKEN_ASSOCIATION = "履约关联授权不存在或业务引用不一致，需人工核对";
    public static final int REFERENCE_MAX_LENGTH = 128;

    private FulfillmentConstant() {
    }
}

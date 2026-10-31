package io.github.surezzzzzz.sdk.license.server.constant;

/**
 * 部署激活的可信调用者绑定与设备能力边界。
 *
 * @author surezzzzzz
 */
public final class ActivationConstant {

    public static final String CALLER_BINDING = "callerBinding";
    public static final String SOURCE_ID = "sourceId";
    public static final String SUBJECT_ID = "subjectId";
    public static final String INVALID_CALLER_BINDING = "callerBinding必须为非空sourceId/subjectId组成的服务身份对象";
    public static final String CALLER_REQUIRED = "在线和混合部署激活必须明确登记获准调用的服务身份";
    public static final String INVALID_VERSION = "expectedRowVersion必须为非负整数";
    public static final String INVALID_GENERATION = "激活代数已达上限，禁止换机";
    public static final String TERMINAL_ACTIVATION = "已替换或吊销的激活不能再次换机或改绑调用者";
    public static final String DEVICE_NONE_ONLY = "当前仅支持NONE设备政策，不能用自报设备摘要代替可信设备证明";

    private ActivationConstant() {
    }
}

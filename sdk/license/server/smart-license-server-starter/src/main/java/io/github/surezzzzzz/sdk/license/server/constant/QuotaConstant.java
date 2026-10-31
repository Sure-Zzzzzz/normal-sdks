package io.github.surezzzzzz.sdk.license.server.constant;

/**
 * 在线额度的状态、模型字段与安全错误消息。
 *
 * @author surezzzzzz
 */
public final class QuotaConstant {

    public static final String CONCURRENT = "CONCURRENT";
    public static final String CONSUMPTION = "CONSUMPTION";
    public static final String ACTIVE = "ACTIVE";
    public static final String RELEASED = "RELEASED";
    public static final String EXPIRED = "EXPIRED";
    public static final String ONLINE = "ONLINE";
    public static final String TOTAL = "TOTAL";
    public static final String FIXED_WINDOW = "FIXED_WINDOW";
    public static final String HUMAN = "HUMAN";
    public static final String SERVICE = "SERVICE";
    public static final String IAM_PREFIX = "iam:";
    public static final String AKSK_PREFIX = "aksk:";
    public static final String INVALID_INPUT = "额度操作参数无效";
    public static final String INVALID_POLICY = "额度指标、种类或在线执行政策不匹配";
    public static final String UNUSABLE = "授权当前不可使用额度";
    public static final String UNAVAILABLE = "额度业务依赖不可用";
    public static final String REPLAY_MISMATCH = "同一业务操作不能改变数量或真实使用对象";
    public static final String NOT_OWNER = "额度操作不属于当前认证主体";
    public static final String MISSING = "额度操作不存在";
    public static final String CONSUMPTION_FINAL = "累计扣减不可释放或续期";
    public static final String ENDED = "额度预占已经结束或围栏已变化";
    public static final String RELEASE_FAILED = "额度释放围栏不允许当前数量";
    public static final String INSUFFICIENT = "INSUFFICIENT";
    public static final String NO_BALANCE = "NO_BALANCE_ROW";
    public static final String OK = "OK";
    public static final String RESERVATION_PREFIX = "qr-";
    public static final String BALANCE_PREFIX = "qb-";
    public static final int ID_LENGTH = 128;
    public static final int SUBJECT_LENGTH = 160;
    public static final int OPERATION_LENGTH = 160;
    public static final long NO_WINDOW = 0L;

    private QuotaConstant() {
    }
}

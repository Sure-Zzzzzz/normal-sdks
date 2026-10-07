package io.github.surezzzzzz.sdk.auth.iam.server.support;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;

import java.util.regex.Pattern;

/**
 * 手机号 E.164 规范化：绑定挑战/登录挑战/忘记密码挑战/管理员登记全入口统一走本类。
 * 接受形态仅两种：国内 11 位裸号（补 +86）或完整 +E.164；其余（空格/横线/0086 前缀）一律拒绝，
 * 不做清洗宽容——uk_phone 依赖形态一致。
 *
 * @author surezzzzzz
 */
public final class PhoneNormalizationHelper {

    /**
     * 国内手机号：1 开头共 11 位
     */
    private static final Pattern DOMESTIC_PHONE = Pattern.compile("^1\\d{10}$");

    /**
     * E.164 国际形态：+ 开头，总长 8-16 位数字（含国家码，上限 15+容差）
     */
    private static final Pattern E164_PHONE = Pattern.compile("^\\+?[1-9]\\d{6,15}$");

    /**
     * 国内区号前缀（补齐用）
     */
    private static final String DOMESTIC_REGION_PREFIX = "+86";

    private PhoneNormalizationHelper() {
        throw new UnsupportedOperationException("帮助类不能实例化");
    }

    /**
     * 校验并规范化为 E.164 形态。
     *
     * @param raw 原始输入
     * @return E.164 形态手机号（如 +8613800000000）
     * @throws SimpleIamServerException 格式非法（400 面）
     */
    public static String normalize(String raw) {
        if (raw == null) {
            throw invalidPhone(null);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw invalidPhone(raw);
        }
        if (DOMESTIC_PHONE.matcher(trimmed).matches()) {
            return DOMESTIC_REGION_PREFIX + trimmed;
        }
        if (trimmed.startsWith("+") && E164_PHONE.matcher(trimmed).matches()) {
            return trimmed;
        }
        throw invalidPhone(raw);
    }

    /**
     * 判断是否为合法可受理形态（不抛异常版，入口快速分流用）。
     *
     * @param raw 原始输入
     * @return true 可规范化
     */
    public static boolean acceptable(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        return DOMESTIC_PHONE.matcher(trimmed).matches()
                || (trimmed.startsWith("+") && E164_PHONE.matcher(trimmed).matches());
    }

    /**
     * 资料面归一：admin 登记/更新用户的手机号属资料而非凭证，三态语义——
     * null=不动（透传 null）；trim 后空串=清空（归 null，对齐解绑双置 NULL 语义，
     * 防空串落库撞 uk_phone 唯一索引）；非空=normalize（非法格式 400）。
     *
     * @param raw 原始输入
     * @return null（不动/清空）或 E.164 形态手机号
     * @throws SimpleIamServerException 非空且格式非法（400 面）
     */
    public static String normalizeForProfile(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return normalize(trimmed);
    }

    private static SimpleIamServerException invalidPhone(String raw) {
        return new SimpleIamServerException(ErrorCode.VALIDATION_FAILED,
                "手机号格式非法：仅接受国内 11 位手机号或完整 +E.164 形态");
    }
}

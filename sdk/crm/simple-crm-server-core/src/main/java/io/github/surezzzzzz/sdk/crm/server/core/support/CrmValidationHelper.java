package io.github.surezzzzzz.sdk.crm.server.core.support;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/**
 * CRM 公共输入校验工具。
 *
 * @author surezzzzzz
 */
public final class CrmValidationHelper {

    private CrmValidationHelper() {
    }

    /**
     * 校验并返回必填文本。
     *
     * @param value 待校验的值
     * @param field 校验失败时使用的字段名称
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw CrmException.validation(field);
        }
        return value.trim();
    }

    /**
     * 规范化可选文本。
     *
     * @param value 待校验的值
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static String optional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 校验并返回必填对象。
     *
     * @param value 待校验的值
     * @param field 校验失败时使用的字段名称
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static <T> T requiredObject(T value, String field) {
        if (value == null) {
            throw CrmException.validation(field);
        }
        return value;
    }

    /**
     * 校验并返回正版本号。
     *
     * @param value 待校验的值
     * @param field 校验失败时使用的字段名称
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static long positiveVersion(long value, String field) {
        if (value < 1L) {
            throw CrmException.validation(field);
        }
        return value;
    }

    /**
     * 校验并返回正版本号。
     *
     * @param value 待校验的值
     * @param field 校验失败时使用的字段名称
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static int positiveVersion(int value, String field) {
        if (value < 1) {
            throw CrmException.validation(field);
        }
        return value;
    }

    /**
     * 校验并返回正数。
     *
     * @param value 待校验的值
     * @param field 校验失败时使用的字段名称
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static BigDecimal positiveDecimal(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw CrmException.validation(field);
        }
        return value;
    }

    /**
     * 校验并返回非负数。
     *
     * @param value 待校验的值
     * @param field 校验失败时使用的字段名称
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static BigDecimal nonNegativeDecimal(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw CrmException.validation(field);
        }
        return value;
    }

    /**
     * 校验并规范化 ISO-4217 货币代码。
     *
     * @param value 待校验的值
     * @param field 校验失败时使用的字段名称
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static String currency(String value, String field) {
        String currency = required(value, field).toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw CrmException.validation(field);
        }
        return currency;
    }

    /**
     * 校验货币精度并返回规范金额。
     *
     * @param value    待校验的值
     * @param currency ISO-4217 货币代码
     * @param field    校验失败时使用的字段名称
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static BigDecimal monetaryAmount(BigDecimal value, String currency, String field) {
        BigDecimal amount = nonNegativeDecimal(value, field);
        int fractionDigits = Currency.getInstance(currency).getDefaultFractionDigits();
        if (fractionDigits < 0 || amount.scale() > fractionDigits) {
            throw CrmException.validation(field);
        }
        return amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
    }

    /**
     * 校验并返回 SHA-256 十六进制摘要。
     *
     * @param value 待校验的值
     * @param field 校验失败时使用的字段名称
     * @return 处理后的领域事实或校验结果。
     *
     */
    public static String sha256(String value, String field) {
        String hash = required(value, field);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw CrmException.validation(field);
        }
        return hash;
    }
}

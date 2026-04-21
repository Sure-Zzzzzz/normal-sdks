package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 索引日期工具
 * 提供索引路由相关的日期解析和字符串处理方法
 *
 * @author surezzzzzz
 */
public final class IndexDateHelper {

    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

    private IndexDateHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 解析日期字符串（支持 ISO 日期时间格式和纯日期格式）
     *
     * @param dateStr 日期字符串（如 "2025-12-18" 或 "2025-12-18T00:00:00"）
     * @return LocalDate
     */
    public static LocalDate parseDate(String dateStr) {
        try {
            if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr).toLocalDate();
            }
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new SimpleElasticsearchSearchException(ErrorCode.DATE_PATTERN_REQUIRED,
                    "Invalid date format: " + dateStr, e);
        }
    }

    /**
     * 提取索引前缀（去掉末尾通配符）
     * 例如：test_log_* → test_log_
     *
     * @param indexPattern 索引名称或通配符模式
     * @return 索引前缀
     */
    public static String extractIndexPrefix(String indexPattern) {
        if (indexPattern.endsWith(SimpleElasticsearchSearchConstant.WILDCARD_STAR)) {
            return indexPattern.substring(0, indexPattern.length() - 1);
        }
        return indexPattern;
    }

    /**
     * 提取日期格式中的分隔符
     * 例如：yyyy.MM.dd → "."
     *
     * @param datePattern 日期格式
     * @return 分隔符，无分隔符时返回空字符串
     */
    public static String extractSeparator(String datePattern) {
        Matcher matcher = SEPARATOR_PATTERN.matcher(datePattern);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    /**
     * 构建月级别的日期格式（去掉日部分）
     * 例如：yyyy.MM.dd → yyyy.MM
     *
     * @param datePattern 原始日期格式
     * @return 月级别格式
     */
    public static String buildMonthPattern(String datePattern) {
        return datePattern.replaceAll("[dD]+[^a-zA-Z]*$", "").replaceAll("[^a-zA-Z0-9]+$", "");
    }

    /**
     * 构建年级别的日期格式（去掉月日部分）
     * 例如：yyyy.MM.dd → yyyy
     *
     * @param datePattern 原始日期格式
     * @return 年级别格式
     */
    public static String buildYearPattern(String datePattern) {
        return datePattern.replaceAll("[MmDd]+.*$", "").replaceAll("[^a-zA-Z0-9]+$", "");
    }
}

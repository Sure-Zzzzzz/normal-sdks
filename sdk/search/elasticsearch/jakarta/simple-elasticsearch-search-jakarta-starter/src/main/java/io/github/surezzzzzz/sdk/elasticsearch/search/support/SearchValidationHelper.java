package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchProtocolConstant.*;

/**
 * 统一参数/协议边界，不保留原始异常原因链。
 */
public final class SearchValidationHelper {
    private SearchValidationHelper() {
    }

    /**
     * 构造固定参数错误。
     */
    public static SimpleElasticsearchSearchException invalid() {
        return new SimpleElasticsearchSearchException(ErrorCode.REQUEST_INVALID, ErrorMessage.REQUEST_INVALID, HTTP_BAD_REQUEST);
    }

    /**
     * 构造固定配置错误。
     */
    public static SimpleElasticsearchSearchException config() {
        return new SimpleElasticsearchSearchException(ErrorCode.CONFIG_INVALID, ErrorMessage.CONFIG_INVALID, HTTP_INTERNAL_SERVER_ERROR);
    }

    /**
     * 构造固定字段能力错误。
     */
    public static SimpleElasticsearchSearchException field() {
        return new SimpleElasticsearchSearchException(ErrorCode.FIELD_INVALID, ErrorMessage.FIELD_INVALID, HTTP_BAD_REQUEST);
    }

    /**
     * 构造固定游标错误。
     */
    public static SimpleElasticsearchSearchException cursor() {
        return new SimpleElasticsearchSearchException(ErrorCode.CURSOR_INVALID, ErrorMessage.CURSOR_INVALID, HTTP_BAD_REQUEST);
    }

    /**
     * 构造固定响应协议错误。
     */
    public static SimpleElasticsearchSearchException protocol() {
        return new SimpleElasticsearchSearchException(ErrorCode.RESPONSE_INVALID, ErrorMessage.RESPONSE_INVALID, HTTP_BAD_GATEWAY);
    }

    /**
     * 判断文本是否包含非空白内容。
     */
    public static boolean text(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 严格读取协议对象，缺失或类型错误立即失败。
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map)) throw protocol();
        return (Map<String, Object>) value;
    }

    /**
     * 严格读取非负整数，拒绝溢出、小数和非有限值。
     */
    public static long number(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number)) throw protocol();
        try {
            long integer = new BigDecimal(raw.toString()).longValueExact();
            if (integer < 0) throw protocol();
            return integer;
        } catch (ArithmeticException | NumberFormatException error) {
            throw protocol();
        }
    }

    /**
     * 创建单键协议对象。
     */
    public static Map<String, Object> node(String key, Object value) {
        return Collections.singletonMap(key, value);
    }

    /**
     * 校验索引表达式，拒绝路径注入和不合法名称。
     */
    public static void requireIndex(String index) {
        if (!text(index) || index.length() > MAX_INDEX_LENGTH || !index.matches(REGEX_INDEX) || index.equals(DOT) || index.equals(PARENT_DOT) || index.startsWith(INDEX_DISALLOWED_DASH_PREFIX) || index.startsWith(INDEX_DISALLOWED_UNDERSCORE_PREFIX))
            throw invalid();
    }

    /**
     * 判断表达式是否包含受支持通配符。
     */
    public static boolean wildcard(String index) {
        return index.indexOf(CHAR_WILDCARD_STAR) >= 0 || index.indexOf(CHAR_WILDCARD_SINGLE) >= 0;
    }

    /**
     * 读取通配符之前的固定前缀，用于归属证明。
     */
    public static String prefix(String pattern) {
        int end = pattern.length();
        for (char ch : new char[]{CHAR_WILDCARD_STAR, CHAR_WILDCARD_SINGLE}) {
            int at = pattern.indexOf(ch);
            if (at >= 0) end = Math.min(end, at);
        }
        return pattern.substring(0, end);
    }

    /**
     * 按索引通配规则完整匹配目标。
     */
    public static boolean matches(String pattern, String value) {
        StringBuilder regex = new StringBuilder();
        for (char ch : pattern.toCharArray()) {
            if (ch == CHAR_WILDCARD_STAR) regex.append(REGEX_MATCH_ANY);
            else if (ch == CHAR_WILDCARD_SINGLE) regex.append(CHAR_DOT);
            else regex.append(java.util.regex.Pattern.quote(String.valueOf(ch)));
        }
        return value.matches(regex.toString());
    }
}

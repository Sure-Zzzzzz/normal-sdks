package io.github.surezzzzzz.sdk.expression.condition.parser.keyword;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.configuration.ConditionExpressionParserProperties;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.TimeRange;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 时间范围关键字映射
 * 提供默认的关键字到时间范围枚举的映射，支持用户自定义扩展
 *
 * @author surezzzzzz
 */
@Slf4j
@ConditionExpressionParserComponent
public class TimeRangeKeywords {

    private static final Map<String, TimeRange> DEFAULT_KEYWORDS = new HashMap<>();

    static {
        // 分钟级
        register("近5分钟", TimeRange.LAST_5_MINUTES);
        register("近10分钟", TimeRange.LAST_10_MINUTES);
        register("近15分钟", TimeRange.LAST_15_MINUTES);
        register("近30分钟", TimeRange.LAST_30_MINUTES);

        // 小时级
        register("近1小时", TimeRange.LAST_1_HOUR);
        register("近6小时", TimeRange.LAST_6_HOURS);
        register("近12小时", TimeRange.LAST_12_HOURS);
        register("近24小时", TimeRange.LAST_24_HOURS);

        // 天级
        register("近1天", TimeRange.LAST_1_DAY);
        register("近3天", TimeRange.LAST_3_DAYS);
        register("近7天", TimeRange.LAST_7_DAYS);

        // 周级
        register("近1周", TimeRange.LAST_1_WEEK);
        register("近2周", TimeRange.LAST_2_WEEKS);

        // 月级（重点：匹配业务场景）
        register("近1个月", TimeRange.LAST_1_MONTH);
        register("一个月", TimeRange.LAST_1_MONTH);  // 别名
        register("近2个月", TimeRange.LAST_2_MONTHS);
        register("近3个月", TimeRange.LAST_3_MONTHS);  // 数字版
        register("近三个月", TimeRange.LAST_3_MONTHS);
        register("三个月", TimeRange.LAST_3_MONTHS);  // 别名
        register("近6个月", TimeRange.LAST_6_MONTHS);  // 数字版
        register("近半年", TimeRange.LAST_6_MONTHS);
        register("半年", TimeRange.LAST_6_MONTHS);  // 别名

        // 年级
        register("近1年", TimeRange.LAST_1_YEAR);
        register("一年", TimeRange.LAST_1_YEAR);  // 别名
        register("近2年", TimeRange.LAST_2_YEARS);
        register("近3年", TimeRange.LAST_3_YEARS);

        // 相对时间点
        register("今天", TimeRange.TODAY);
        register("昨天", TimeRange.YESTERDAY);
        register("前天", TimeRange.DAY_BEFORE_YESTERDAY);
        register("本周", TimeRange.THIS_WEEK);
        register("上周", TimeRange.LAST_WEEK);
        register("本月", TimeRange.THIS_MONTH);
        register("上月", TimeRange.PREVIOUS_MONTH);
        register("本季度", TimeRange.THIS_QUARTER);
        register("上季度", TimeRange.LAST_QUARTER);
        register("今年", TimeRange.THIS_YEAR);
        register("去年", TimeRange.LAST_YEAR);
    }

    private static void register(String keyword, TimeRange timeRange) {
        DEFAULT_KEYWORDS.put(keyword, timeRange);
    }

    private final Map<String, TimeRange> keywordMap;

    public TimeRangeKeywords(ConditionExpressionParserProperties properties) {
        // 复制默认映射
        this.keywordMap = new HashMap<>(DEFAULT_KEYWORDS);

        // Merge 用户自定义映射
        if (properties.getCustomTimeRanges() != null) {
            properties.getCustomTimeRanges().forEach((keyword, timeRangeName) -> {
                try {
                    TimeRange timeRange = TimeRange.valueOf(timeRangeName);
                    keywordMap.put(keyword, timeRange);
                    log.info("注册自定义时间范围: {} -> {}", keyword, timeRange);
                } catch (IllegalArgumentException e) {
                    log.warn("无效的时间范围配置: {} -> {}，已忽略", keyword, timeRangeName);
                }
            });
        }
    }

    /**
     * 根据关键字获取时间范围枚举
     *
     * @param keyword 关键字
     * @return 时间范围枚举，如果不存在则返回 null
     */
    public TimeRange fromKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keywordMap.get(keyword);
    }

    /**
     * 判断是否为时间范围关键字
     *
     * @param keyword 关键字
     * @return true 如果是时间范围关键字
     */
    public boolean isKeyword(String keyword) {
        return keyword != null && keywordMap.containsKey(keyword);
    }

    /**
     * 获取所有关键字映射（只读）
     *
     * @return 关键字映射的副本
     */
    public Map<String, TimeRange> getAllKeywords() {
        return new HashMap<>(keywordMap);
    }
}

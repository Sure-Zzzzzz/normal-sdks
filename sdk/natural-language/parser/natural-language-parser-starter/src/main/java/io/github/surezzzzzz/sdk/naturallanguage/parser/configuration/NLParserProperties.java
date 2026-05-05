package io.github.surezzzzzz.sdk.naturallanguage.parser.configuration;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.NLParserConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Natural Language Parser Properties
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(NLParserConstant.CONFIG_PREFIX)
public class NLParserProperties {

    /**
     * 是否启用
     */
    private boolean enable = true;

    /**
     * 解析器参数
     */
    private ParserConfig parser = new ParserConfig();

    /**
     * 关键字扩展配置
     */
    private KeywordsConfig keywords = new KeywordsConfig();

    @Data
    public static class ParserConfig {

        /**
         * 字段向前查找最大距离
         */
        private int maxFieldLookaheadDistance = NLParserConstant.MAX_FIELD_LOOKAHEAD_DISTANCE;

        /**
         * 字段向后查找最大距离
         */
        private int maxFieldLookbehindDistance = NLParserConstant.MAX_FIELD_LOOKBEHIND_DISTANCE;
    }

    @Data
    public static class KeywordsConfig {

        /**
         * 操作符关键字扩展：key = OperatorType.code，value = 追加的关键字列表
         */
        private Map<String, List<String>> operators = new HashMap<>();

        /**
         * 聚合关键字扩展：key = AggType.code，value = 追加的关键字列表
         */
        private Map<String, List<String>> aggregations = new HashMap<>();

        /**
         * 折叠关键字扩展
         */
        private List<String> collapse = new ArrayList<>();

        /**
         * 时间范围关键字扩展：key = TimeRange.code，value = 追加的关键字列表
         */
        private Map<String, List<String>> timeRanges = new HashMap<>();

        /**
         * 逻辑关键字扩展：key = LogicType.code，value = 追加的关键字列表
         */
        private Map<String, List<String>> logic = new HashMap<>();

        /**
         * 排序关键字扩展：key = SortOrder.code，value = 追加的关键字列表
         */
        private Map<String, List<String>> sort = new HashMap<>();

        /**
         * 分页关键字扩展
         */
        private List<String> pagination = new ArrayList<>();

        /**
         * 介词扩展
         */
        private List<String> prepositions = new ArrayList<>();

        /**
         * 停用词扩展
         */
        private List<String> stopWords = new ArrayList<>();
    }
}

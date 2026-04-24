package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 表达式提示信息响应
 * 供前端自动补全使用
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpressionHintsResponse {

    private List<FieldHint> fields;
    private List<OperatorHint> operators;
    private List<String> timeRanges;
    private ValueRules valueRules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldHint {
        /**
         * ES 字段名
         */
        private String name;
        /**
         * 字段中文标签列表（来自 field-mapping 配置，第一个为主标签）
         */
        private List<String> label;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OperatorHint {
        private String op;
        private String description;
        private String chinese;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueRules {
        private boolean stringNeedsQuote;
        private List<String> supportedQuotes;
        private List<String> booleanKeywords;
        private boolean numberNoQuote;
    }
}

package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 解析结果收集器
 * 各 Parser 写入自己负责的字段，NLParser 据此组装最终 Intent
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {

    /**
     * 索引/表名提示
     */
    private String indexHint;

    /**
     * 查询条件
     */
    private ConditionIntent condition;

    /**
     * 聚合定义列表
     */
    private List<AggregationIntent> aggregations;

    /**
     * 排序列表
     */
    private List<SortIntent> sorts;

    /**
     * 分页
     */
    private PaginationIntent pagination;

    /**
     * 日期范围
     */
    private DateRangeIntent dateRange;

    /**
     * 字段折叠
     */
    private CollapseIntent collapse;

    /**
     * 字段投影
     */
    private List<String> fieldProjections;

    /**
     * 扩展数据 — 自定义 Parser 写入，IntentTranslator 可读取
     */
    private Map<String, Object> ext;
}

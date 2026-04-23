package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 高级表达式语法校验结果
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpressionValidationResult {

    /**
     * 是否合法
     */
    private boolean valid;

    /**
     * 错误信息（valid=true 时为 null）
     */
    private String errorMessage;

    /**
     * 出错的字符列位置，-1 表示未知（valid=true 时为 -1）
     */
    private int errorPosition;
}

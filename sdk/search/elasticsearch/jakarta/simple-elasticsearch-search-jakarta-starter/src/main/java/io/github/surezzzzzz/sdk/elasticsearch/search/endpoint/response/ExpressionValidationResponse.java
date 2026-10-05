package io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response;

import lombok.Value;

/**
 * 正常校验结果；消息固定且脱敏，依赖失败仍由非成功 HTTP 状态表达。
 */
@Value
public class ExpressionValidationResponse {
    boolean valid;
    String message;
}

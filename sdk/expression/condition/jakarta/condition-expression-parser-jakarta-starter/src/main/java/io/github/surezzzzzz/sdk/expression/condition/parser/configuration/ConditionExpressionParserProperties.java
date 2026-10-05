package io.github.surezzzzzz.sdk.expression.condition.parser.configuration;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ConditionExpressionParserConstant;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ParseOptions;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 解析器的唯一全局配置；限值在构造解析器时复制校验。 */
@Data
@ConfigurationProperties(prefix = ConditionExpressionParserConstant.CONFIG_PREFIX)
public class ConditionExpressionParserProperties {
    /** 保留默认启用的接入行为。 */
    private boolean enabled = ConditionExpressionParserConstant.DEFAULT_ENABLED;
    /** 所有限值为正；显式配置不会与其他请求的配置合并。 */
    private ParseOptions limits = new ParseOptions();
}

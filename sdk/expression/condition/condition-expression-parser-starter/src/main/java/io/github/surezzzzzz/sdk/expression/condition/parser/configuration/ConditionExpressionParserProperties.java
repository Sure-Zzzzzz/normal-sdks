package io.github.surezzzzzz.sdk.expression.condition.parser.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 条件表达式解析器配置类
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.expression.condition.parser")
public class ConditionExpressionParserProperties {

    /**
     * 是否启用条件表达式解析器
     */
    private boolean enabled = true;
}

package io.github.surezzzzzz.sdk.expression.condition.parser.configuration;

import io.github.surezzzzzz.sdk.expression.condition.parser.ConditionExpressionParserPackage;
import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 条件表达式解析器自动配置
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ConditionExpressionParserProperties.class)
@ComponentScan(
        basePackageClasses = ConditionExpressionParserPackage.class,
        includeFilters = @ComponentScan.Filter(ConditionExpressionParserComponent.class),
        useDefaultFilters = false
)
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.expression.condition.parser",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ConditionExpressionParserAutoConfiguration {

    private final ConditionExpressionParserProperties properties;

    public ConditionExpressionParserAutoConfiguration(ConditionExpressionParserProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化完成日志
     */
    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("✅ Condition Expression Parser Starter Initialized");
        log.info("Enabled: {}", properties.isEnabled());
        log.info("Custom Comparison Operators: {}", properties.getCustomComparisonOperators().size());
        log.info("Custom Logical Operators: {}", properties.getCustomLogicalOperators().size());
        log.info("Custom Time Ranges: {}", properties.getCustomTimeRanges().size());
        log.info("Custom Match Operators: {}", properties.getCustomMatchOperators().size());
        log.info("========================================");
    }
}

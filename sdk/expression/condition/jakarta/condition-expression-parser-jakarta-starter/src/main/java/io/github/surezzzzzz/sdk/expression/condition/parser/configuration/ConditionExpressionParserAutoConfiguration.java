package io.github.surezzzzzz.sdk.expression.condition.parser.configuration;

import io.github.surezzzzzz.sdk.expression.condition.parser.ConditionExpressionParserPackage;
import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ConditionExpressionParserConstant;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import java.util.List;

/** Boot3 自动装配；可替换的默认组件只经 Bean 方法注册，不参与扫描。 */
@AutoConfiguration
@EnableConfigurationProperties(ConditionExpressionParserProperties.class)
@ComponentScan(basePackageClasses = ConditionExpressionParserPackage.class,
        includeFilters = @ComponentScan.Filter(ConditionExpressionParserComponent.class),
        useDefaultFilters = false)
@ConditionalOnProperty(prefix = ConditionExpressionParserConstant.CONFIG_PREFIX,
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConditionExpressionParserAutoConfiguration {
    /** 允许调用方替换布尔策略。 */
    @Bean
    @ConditionalOnMissingBean(BooleanValueParseStrategy.class)
    public BooleanValueParseStrategy booleanValueParseStrategy() { return new BooleanValueParseStrategy(); }
    /** 允许调用方替换时间策略。 */
    @Bean
    @ConditionalOnMissingBean(TimeRangeValueParseStrategy.class)
    public TimeRangeValueParseStrategy timeRangeValueParseStrategy() { return new TimeRangeValueParseStrategy(); }
    /** 允许调用方替换数字策略。 */
    @Bean
    @ConditionalOnMissingBean(NumberValueParseStrategy.class)
    public NumberValueParseStrategy numberValueParseStrategy() { return new NumberValueParseStrategy(); }
    /** 允许调用方替换字符串兜底。 */
    @Bean
    @ConditionalOnMissingBean(StringValueParseStrategy.class)
    public StringValueParseStrategy stringValueParseStrategy() { return new StringValueParseStrategy(); }
    /** 合并全部策略，排序与复制由 ValueParser 统一负责。 */
    @Bean
    @ConditionalOnMissingBean(ValueParser.class)
    public ValueParser valueParser(List<ValueParseStrategy> strategies) { return new ValueParser(strategies); }
    /** 允许调用方提供解析器或其子类，默认配置只在构造时使用。 */
    @Bean
    @ConditionalOnMissingBean(ConditionExpressionParser.class)
    public ConditionExpressionParser conditionExpressionParser(ValueParser valueParser,
            ConditionExpressionParserProperties properties) {
        return new ConditionExpressionParser(valueParser, properties.getLimits());
    }
}

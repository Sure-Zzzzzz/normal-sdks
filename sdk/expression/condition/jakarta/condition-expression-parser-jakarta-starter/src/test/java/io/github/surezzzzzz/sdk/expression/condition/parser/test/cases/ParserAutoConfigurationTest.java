package io.github.surezzzzzz.sdk.expression.condition.parser.test.cases;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThat;

/** 通过真实 imports 发现自动配置，不使用直接 Import 掩盖注册遗漏。 */
@Slf4j
class ParserAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Host.class);

    @Test
    void defaultAndExplicitEnableUseRealBootImports() {
        log.info("验证默认和显式启用的真实自动装配");
        for (String enabled : new String[]{"", "io.github.surezzzzzz.sdk.expression.condition.parser.enabled=true"}) {
            ApplicationContextRunner configured = enabled.isEmpty() ? runner : runner.withPropertyValues(enabled);
            configured.run(context -> {
                assertThat(context).hasNotFailed().hasSingleBean(ConditionExpressionParser.class).hasSingleBean(ValueParser.class);
                assertThat(context.getBeansOfType(ValueParseStrategy.class)).hasSize(4);
                assertThat(context.getBean(ConditionExpressionParser.class).parse("f=1")).isNotNull();
            });
        }
    }
    @Test
    void disableDoesNotDeleteUserBeans() {
        log.info("验证禁用默认组件而保留调用方 Bean");
        runner.withPropertyValues("io.github.surezzzzzz.sdk.expression.condition.parser.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ConditionExpressionParser.class)
                        .doesNotHaveBean(ValueParser.class).doesNotHaveBean(ValueParseStrategy.class));
        runner.withUserConfiguration(CustomParser.class)
                .withPropertyValues("io.github.surezzzzzz.sdk.expression.condition.parser.enabled=false")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ConditionExpressionParser.class));
    }
    @Test
    void parserSubclassBacksOffWithoutDuplicateRegistration() {
        log.info("验证解析器子类让位");
        runner.withUserConfiguration(CustomParser.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ConditionExpressionParser.class);
            assertThat(context.getBean(ConditionExpressionParser.class)).isInstanceOf(ChildParser.class);
        });
    }
    @Test
    void completeValueParserCanReplaceDefaults() {
        log.info("验证完整值解析器让位");
        runner.withUserConfiguration(CustomValues.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ValueParser.class);
            assertThat(context.getBean(ValueParser.class).parse("18").asString()).isEqualTo("18");
        });
    }
    @Test
    void oneDefaultStrategyCanBeReplaced() {
        log.info("验证单个默认策略替换不重复扫描");
        runner.withUserConfiguration(CustomBoolean.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(BooleanValueParseStrategy.class);
            assertThat(context.getBeansOfType(ValueParseStrategy.class)).hasSize(4);
            assertThat(context.getBean(ValueParser.class).parse("true").asBoolean()).isFalse();
        });
    }
    @Test
    void limitsBindAndInvalidConfigurationFailsAtStartup() {
        log.info("验证容量绑定和启动失败");
        runner.withPropertyValues("io.github.surezzzzzz.sdk.expression.condition.parser.limits.max-length=3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> context.getBean(ConditionExpressionParser.class).parse("longField=1"))
                            .isInstanceOf(io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException.class);
                });
        runner.withPropertyValues("io.github.surezzzzzz.sdk.expression.condition.parser.limits.max-parse-depth=65")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test
    void hostWorksWithoutWebEsOrJackson() {
        log.info("验证无 Web、ES、JSON 的纯宿主");
        runner.withClassLoader(new FilteredClassLoader("org.springframework.web", "jakarta.servlet",
                "com.fasterxml.jackson", "org.elasticsearch"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ConditionExpressionParser.class);
                    assertThat(context.getBean(ConditionExpressionParser.class).parse("f=1")).isNotNull();
                });
    }
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Host { }
    static class ChildParser extends ConditionExpressionParser {
        ChildParser() { super(new ValueParser(Collections.singletonList(new StringValueParseStrategy()))); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class CustomParser { @Bean ChildParser customParser() { return new ChildParser(); } }
    @TestConfiguration(proxyBeanMethods = false)
    static class CustomValues {
        @Bean ValueParser customValues() { return new ValueParser(Collections.singletonList(new StringValueParseStrategy())); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class CustomBoolean {
        @Bean BooleanValueParseStrategy customBoolean() {
            return new BooleanValueParseStrategy() {
                public ValueNode parse(String raw) { return ValueNode.builder().type(ValueType.BOOLEAN).rawValue(raw).parsedValue(false).build(); }
            };
        }
    }
}

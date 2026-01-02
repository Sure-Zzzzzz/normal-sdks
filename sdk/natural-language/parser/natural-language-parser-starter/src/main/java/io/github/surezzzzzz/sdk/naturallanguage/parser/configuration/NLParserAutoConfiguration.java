package io.github.surezzzzzz.sdk.naturallanguage.parser.configuration;

import io.github.surezzzzzz.sdk.naturallanguage.parser.NaturalLanguageParserPackage;
import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.NLTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 自然语言解析器自动配置
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NLParserProperties.class)
@ComponentScan(
        basePackageClasses = NaturalLanguageParserPackage.class,
        includeFilters = @ComponentScan.Filter(NaturalLanguageParserComponent.class),
        useDefaultFilters = false
)
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.naturallanguage.parser",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NLParserAutoConfiguration {

    private final NLParserProperties properties;

    @Autowired(required = false)
    private NLTokenizer tokenizer;

    public NLParserAutoConfiguration(NLParserProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化完成日志
     */
    @PostConstruct
    public void init() {
        // 添加自定义停用词
        if (tokenizer != null && properties.getCustomStopWords() != null && !properties.getCustomStopWords().isEmpty()) {
            tokenizer.addStopWords(properties.getCustomStopWords());
        }

        log.info("========================================");
        log.info("✅ Natural Language Parser Starter Initialized");
        log.info("Enabled: {}", properties.isEnabled());
        log.info("Custom Stop Words: {}", properties.getCustomStopWords() != null ? properties.getCustomStopWords().size() : 0);
        log.info("========================================");
    }
}

package io.github.surezzzzzz.sdk.naturallanguage.parser.configuration;

import io.github.surezzzzzz.sdk.naturallanguage.parser.NaturalLanguageParserPackage;
import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.NLParserConstant;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.DefaultKeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordContributor;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.parser.NLParserPlugin;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.NLParser;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.OperatorSuggester;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.DefaultNLTokenizer;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.NLTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Natural Language Parser Auto Configuration
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
        prefix = NLParserConstant.CONFIG_PREFIX,
        name = "enable",
        havingValue = "true",
        matchIfMissing = true
)
public class NLParserAutoConfiguration {

    private final NLParserProperties properties;

    public NLParserAutoConfiguration(NLParserProperties properties) {
        this.properties = properties;
    }

    /**
     * 关键字注册表：加载内置默认值 → YAML 扩展 → KeywordContributor Bean
     *
     * @param contributors 关键字贡献者列表
     * @return 关键字注册表
     */
    @Bean
    @ConditionalOnMissingBean
    public KeywordRegistry keywordRegistry(List<KeywordContributor> contributors) {
        DefaultKeywordRegistry registry = new DefaultKeywordRegistry();
        // 1. 内置默认值已在 DefaultKeywordRegistry 构造时加载
        // 2. 应用 YAML 配置
        registry.applyConfig(properties.getKeywords());
        // 3. 按 @Order 顺序调用所有 Contributor
        for (KeywordContributor contributor : contributors) {
            contributor.contribute(registry);
        }
        // 4. 冻结并校验
        registry.validateAndFreeze();
        return registry;
    }

    /**
     * NLTokenizer：优先使用用户自定义实现，否则使用 DefaultNLTokenizer
     *
     * @param keywordRegistry 关键字注册表
     * @return NLTokenizer 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public NLTokenizer nlTokenizer(KeywordRegistry keywordRegistry) {
        return new DefaultNLTokenizer(keywordRegistry);
    }

    /**
     * NLParser 门面
     *
     * @param tokenizer       分词器
     * @param keywordRegistry 关键字注册表
     * @param plugins         解析器插件列表
     * @return NLParser 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public NLParser nlParser(NLTokenizer tokenizer, KeywordRegistry keywordRegistry,
                             NLParserProperties parserProperties, List<NLParserPlugin> plugins) {
        return new NLParser(tokenizer, keywordRegistry, parserProperties, plugins);
    }

    /**
     * OperatorSuggester：操作符拼写建议器
     *
     * @param keywordRegistry 关键字注册表
     * @return OperatorSuggester 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public OperatorSuggester operatorSuggester(KeywordRegistry keywordRegistry) {
        return new OperatorSuggester(keywordRegistry);
    }

    /**
     * 初始化完成日志
     */
    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("Natural Language Parser Starter Initialized");
        log.info("Enabled: {}", properties.isEnable());
        log.info("========================================");
    }
}

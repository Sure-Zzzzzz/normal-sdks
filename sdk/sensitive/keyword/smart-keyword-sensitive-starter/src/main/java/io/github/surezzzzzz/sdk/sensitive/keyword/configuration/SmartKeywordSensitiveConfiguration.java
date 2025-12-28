package io.github.surezzzzzz.sdk.sensitive.keyword.configuration;

import io.github.surezzzzzz.sdk.sensitive.keyword.SmartKeywordSensitivePackage;
import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.SmartKeywordSensitiveConstant;
import io.github.surezzzzzz.sdk.sensitive.keyword.nlp.HanLPProvider;
import io.github.surezzzzzz.sdk.sensitive.keyword.nlp.NLPProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Simple Keyword Sensitive Auto Configuration
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(SmartKeywordSensitiveProperties.class)
@ComponentScan(
        basePackageClasses = SmartKeywordSensitivePackage.class,
        includeFilters = @ComponentScan.Filter(SmartKeywordSensitiveComponent.class)
)
@ConditionalOnProperty(prefix = SmartKeywordSensitiveConstant.CONFIG_PREFIX, name = "enable", havingValue = "true")
public class SmartKeywordSensitiveConfiguration {

    /**
     * NLP提供者(可选)
     * 默认启用,只有显式配置 nlp.enabled=false 或 HanLP 依赖缺失时才不创建
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = SmartKeywordSensitiveConstant.CONFIG_PREFIX + ".nlp", name = "enabled", havingValue = "true", matchIfMissing = true)
    public NLPProvider nlpProvider(SmartKeywordSensitiveProperties properties) {
        String provider = properties.getNlp().getProvider();
        log.info("Creating NLPProvider bean: {}", provider);

        if (SmartKeywordSensitiveConstant.NLP_PROVIDER_BUILT_IN.equalsIgnoreCase(provider)) {
            try {
                HanLPProvider hanLPProvider = new HanLPProvider();
                log.info("✅ HanLP NLP Provider initialized successfully");
                return hanLPProvider;
            } catch (Exception e) {
                log.error("Failed to initialize HanLP provider", e);
                if (!properties.getNlp().isFallbackToRule()) {
                    throw e;
                }
                log.warn("NLP provider initialization failed, will use rule-based extraction only");
                return null;
            }
        } else {
            log.warn("Unknown NLP provider: {}, NLP will be disabled", provider);
            return null;
        }
    }

    /**
     * 初始化完成日志
     */
    @Bean
    @ConditionalOnMissingBean(name = "keywordSensitiveInitializer")
    public Object keywordSensitiveInitializer(SmartKeywordSensitiveProperties properties) {
        log.info("========================================");
        log.info("✅ Simple Keyword Sensitive Starter Initialized");
        log.info("Enabled: {}", properties.isEnable());
        log.info("Configured Keywords: {}", properties.getKeywords().size());
        log.info("NLP Enabled: {}", properties.getNlp().isEnabled());
        log.info("NLP Provider: {}", properties.getNlp().getProvider());
        log.info("Default Mask Type: {}", properties.getDefaultStrategy().getMaskType());
        log.info("Auto Recognition: ENABLED (Rule + NLP)");
        log.info("========================================");
        return new Object();
    }
}

package io.github.surezzzzzz.sdk.elasticsearch.search.processor.sensitive;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SensitiveStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 敏感字段策略注册表
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class SensitiveFieldStrategyRegistry {

    private final Map<String, SensitiveFieldStrategy> strategies = new LinkedHashMap<>();

    private final ForbiddenSensitiveFieldStrategy forbiddenStrategy;
    private final MaskSensitiveFieldStrategy maskStrategy;

    @PostConstruct
    public void init() {
        register(SensitiveStrategy.FORBIDDEN.getStrategy(), forbiddenStrategy);
        register(SensitiveStrategy.MASK.getStrategy(), maskStrategy);
        log.info("SensitiveFieldStrategyRegistry initialized with {} strategies", strategies.size());
    }

    /**
     * 根据策略名称解析对应策略
     *
     * @param strategy 策略名称（forbidden / mask）
     * @return 匹配的策略
     */
    public SensitiveFieldStrategy resolve(String strategy) {
        SensitiveFieldStrategy s = strategies.get(strategy.toLowerCase());
        if (s == null) {
            throw new SimpleElasticsearchSearchException(ErrorCode.SENSITIVE_FIELD_STRATEGY_INVALID,
                    String.format(ErrorMessage.SENSITIVE_FIELD_STRATEGY_INVALID, strategy));
        }
        return s;
    }

    /**
     * 注册自定义脱敏策略，不允许覆盖内置
     */
    public void register(String key, SensitiveFieldStrategy strategy) {
        if (strategies.containsKey(key)) {
            throw new ConfigurationException(ErrorCode.SENSITIVE_FIELD_STRATEGY_REQUIRED,
                    String.format("脱敏策略 [%s] 已存在，不允许覆盖内置策略", key));
        }
        strategies.put(key, strategy);
        log.debug("Registered sensitive field strategy: key={}, impl={}", key, strategy.getClass().getSimpleName());
    }
}

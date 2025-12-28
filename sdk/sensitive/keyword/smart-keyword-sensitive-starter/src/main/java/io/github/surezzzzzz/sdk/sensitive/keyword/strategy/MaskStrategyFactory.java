package io.github.surezzzzzz.sdk.sensitive.keyword.strategy;

import io.github.surezzzzzz.sdk.sensitive.keyword.annotation.SmartKeywordSensitiveComponent;
import io.github.surezzzzzz.sdk.sensitive.keyword.constant.MaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Mask Strategy Factory
 * 脱敏策略工厂
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartKeywordSensitiveComponent
public class MaskStrategyFactory {

    private final AsteriskMaskStrategy asteriskStrategy;
    private final PlaceholderMaskStrategy placeholderStrategy;
    private final HashMaskStrategy hashStrategy;

    /**
     * 构造函数 - 使用构造器注入
     *
     * @param asteriskStrategy    星号脱敏策略
     * @param placeholderStrategy 占位符脱敏策略
     * @param hashStrategy        哈希脱敏策略
     */
    @Autowired
    public MaskStrategyFactory(AsteriskMaskStrategy asteriskStrategy,
                               PlaceholderMaskStrategy placeholderStrategy,
                               HashMaskStrategy hashStrategy) {
        this.asteriskStrategy = asteriskStrategy;
        this.placeholderStrategy = placeholderStrategy;
        this.hashStrategy = hashStrategy;
        log.info("MaskStrategyFactory initialized with Asterisk, Placeholder, and Hash strategies");
    }

    /**
     * 根据脱敏类型获取对应的策略
     *
     * @param maskType 脱敏类型
     * @return 对应的脱敏策略
     */
    public MaskStrategy getStrategy(MaskType maskType) {
        if (maskType == null) {
            log.debug("MaskType is null, using default Asterisk strategy");
            return asteriskStrategy;
        }

        switch (maskType) {
            case ASTERISK:
                return asteriskStrategy;
            case PLACEHOLDER:
                return placeholderStrategy;
            case HASH:
                return hashStrategy;
            default:
                log.warn("Unknown mask type: {}, using Asterisk strategy", maskType);
                return asteriskStrategy;
        }
    }
}

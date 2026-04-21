package io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.QueryOperator;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.builder.strategy.operator.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作符策略注册表
 * 内置 17 种操作符策略，启动时自动注册。
 * 用户可通过注入此 Bean 调用 {@link #register} 扩展自定义策略，但不允许覆盖内置 key。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class OperatorStrategyRegistry {

    private final Map<String, OperatorStrategy> strategies = new LinkedHashMap<>();

    private final EqOperatorStrategy eqStrategy;
    private final NeOperatorStrategy neStrategy;
    private final GtOperatorStrategy gtStrategy;
    private final GteOperatorStrategy gteStrategy;
    private final LtOperatorStrategy ltStrategy;
    private final LteOperatorStrategy lteStrategy;
    private final InOperatorStrategy inStrategy;
    private final NotInOperatorStrategy notInStrategy;
    private final BetweenOperatorStrategy betweenStrategy;
    private final LikeOperatorStrategy likeStrategy;
    private final PrefixOperatorStrategy prefixStrategy;
    private final SuffixOperatorStrategy suffixStrategy;
    private final ExistsOperatorStrategy existsStrategy;
    private final NotExistsOperatorStrategy notExistsStrategy;
    private final IsNullOperatorStrategy isNullStrategy;
    private final IsNotNullOperatorStrategy isNotNullStrategy;
    private final RegexOperatorStrategy regexStrategy;

    @PostConstruct
    public void init() {
        register(QueryOperator.EQ.getOperator(), eqStrategy);
        register(QueryOperator.NE.getOperator(), neStrategy);
        register(QueryOperator.GT.getOperator(), gtStrategy);
        register(QueryOperator.GTE.getOperator(), gteStrategy);
        register(QueryOperator.LT.getOperator(), ltStrategy);
        register(QueryOperator.LTE.getOperator(), lteStrategy);
        register(QueryOperator.IN.getOperator(), inStrategy);
        register(QueryOperator.NOT_IN.getOperator(), notInStrategy);
        register(QueryOperator.BETWEEN.getOperator(), betweenStrategy);
        register(QueryOperator.LIKE.getOperator(), likeStrategy);
        register(QueryOperator.PREFIX.getOperator(), prefixStrategy);
        register(QueryOperator.SUFFIX.getOperator(), suffixStrategy);
        register(QueryOperator.EXISTS.getOperator(), existsStrategy);
        register(QueryOperator.NOT_EXISTS.getOperator(), notExistsStrategy);
        register(QueryOperator.IS_NULL.getOperator(), isNullStrategy);
        register(QueryOperator.IS_NOT_NULL.getOperator(), isNotNullStrategy);
        register(QueryOperator.REGEX.getOperator(), regexStrategy);
        log.info("OperatorStrategyRegistry initialized with {} strategies", strategies.size());
    }

    /**
     * 根据操作符解析对应策略
     *
     * @param operator 操作符枚举
     * @return 匹配的策略
     * @throws QueryException 找不到匹配策略时
     */
    public OperatorStrategy resolve(QueryOperator operator) {
        OperatorStrategy strategy = strategies.get(operator.getOperator());
        if (strategy == null) {
            throw new QueryException(ErrorCode.UNSUPPORTED_OPERATOR,
                    String.format(ErrorMessage.UNSUPPORTED_OPERATOR, operator));
        }
        return strategy;
    }

    /**
     * 注册自定义操作符策略，key 已存在时抛异常，防止覆盖内置策略
     *
     * @param key      策略 key，与 {@link QueryOperator#getOperator()} 保持一致
     * @param strategy 策略实现
     * @throws ConfigurationException key 已存在时
     */
    public void register(String key, OperatorStrategy strategy) {
        if (strategies.containsKey(key)) {
            throw new ConfigurationException(ErrorCode.OPERATOR_STRATEGY_DUPLICATE,
                    String.format(ErrorMessage.OPERATOR_STRATEGY_DUPLICATE, key));
        }
        strategies.put(key, strategy);
        log.debug("Registered operator strategy: key={}, impl={}", key, strategy.getClass().getSimpleName());
    }
}

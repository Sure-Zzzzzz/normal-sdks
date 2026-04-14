package io.github.surezzzzzz.sdk.elasticsearch.search.query.pagination;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchAfterMode;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.QueryException;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.PaginationInfo;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 翻页策略注册表
 *
 * <p>内置四种策略在启动时自动注册。用户可通过注入此 Bean 调用 {@link #register} 扩展自定义策略，
 * 但不允许覆盖已注册的 key（包括内置策略）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class PaginationStrategyRegistry {

    private final Map<String, PaginationStrategy> strategies = new LinkedHashMap<>();

    private final OffsetPaginationStrategy offsetStrategy;
    private final TiebreakerPaginationStrategy tiebreakerStrategy;
    private final NonePaginationStrategy noneStrategy;
    private final PitPaginationStrategy pitStrategy;

    public PaginationStrategyRegistry(OffsetPaginationStrategy offsetStrategy,
                                      TiebreakerPaginationStrategy tiebreakerStrategy,
                                      NonePaginationStrategy noneStrategy,
                                      PitPaginationStrategy pitStrategy) {
        this.offsetStrategy = offsetStrategy;
        this.tiebreakerStrategy = tiebreakerStrategy;
        this.noneStrategy = noneStrategy;
        this.pitStrategy = pitStrategy;
    }

    @PostConstruct
    public void init() {
        register(buildOffsetKey(), offsetStrategy);
        register(buildSearchAfterKey(SearchAfterMode.TIEBREAKER), tiebreakerStrategy);
        register(buildSearchAfterKey(SearchAfterMode.NONE), noneStrategy);
        register(buildSearchAfterKey(SearchAfterMode.PIT), pitStrategy);
        log.info("PaginationStrategyRegistry initialized with {} strategies: {}",
                strategies.size(), strategies.keySet());
    }

    /**
     * 注册翻页策略，key 已存在时抛异常，防止覆盖内置策略
     *
     * @param key      策略 key，通过 {@link #buildOffsetKey()} 或 {@link #buildSearchAfterKey(SearchAfterMode)} 生成
     * @param strategy 策略实现
     * @throws ConfigurationException key 已存在时
     */
    public void register(String key, PaginationStrategy strategy) {
        if (strategies.containsKey(key)) {
            throw new ConfigurationException(
                    ErrorCode.PAGINATION_STRATEGY_DUPLICATE,
                    String.format(ErrorMessage.PAGINATION_STRATEGY_DUPLICATE, key));
        }
        strategies.put(key, strategy);
        log.debug("Registered pagination strategy: key={}, impl={}", key, strategy.getClass().getSimpleName());
    }

    /**
     * 根据分页信息解析对应策略
     *
     * @param pagination 分页信息
     * @return 匹配的策略
     * @throws QueryException 找不到匹配策略时
     */
    public PaginationStrategy resolve(PaginationInfo pagination) {
        String key = buildKey(pagination);
        PaginationStrategy strategy = strategies.get(key);
        if (strategy == null) {
            throw new QueryException(ErrorCode.PAGINATION_STRATEGY_NOT_FOUND,
                    String.format(ErrorMessage.PAGINATION_STRATEGY_NOT_FOUND, key));
        }
        return strategy;
    }

    /**
     * 生成 offset 分页的策略 key
     */
    public static String buildOffsetKey() {
        return PaginationStrategyKey.OFFSET;
    }

    /**
     * 生成 search_after 分页的策略 key
     *
     * @param mode search_after 翻页模式
     */
    public static String buildSearchAfterKey(SearchAfterMode mode) {
        return PaginationStrategyKey.SEARCH_AFTER_PREFIX + mode.getCode();
    }

    private String buildKey(PaginationInfo pagination) {
        if (pagination.isOffsetPagination()) {
            return buildOffsetKey();
        }
        return buildSearchAfterKey(pagination.getSearchAfterModeEnum());
    }
}

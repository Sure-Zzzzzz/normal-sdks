package io.github.surezzzzzz.sdk.elasticsearch.search.processor.downgrade;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DateGranularity;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.SimpleElasticsearchSearchException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 降级策略注册表
 * 按 DateGranularity 查找对应的降级策略
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class DowngradeStrategyRegistry {

    private final Map<DateGranularity, DowngradeStrategy> strategies = new LinkedHashMap<>();

    private final DailyDowngradeStrategy dailyStrategy;
    private final MonthlyDowngradeStrategy monthlyStrategy;
    private final YearlyDowngradeStrategy yearlyStrategy;

    @PostConstruct
    public void init() {
        strategies.put(DateGranularity.DAY, dailyStrategy);
        strategies.put(DateGranularity.MONTH, monthlyStrategy);
        strategies.put(DateGranularity.YEAR, yearlyStrategy);
        log.debug("DowngradeStrategyRegistry initialized with {} strategies", strategies.size());
    }

    /**
     * 根据日期粒度查找降级策略
     *
     * @param granularity 日期粒度
     * @return 对应的降级策略
     */
    public DowngradeStrategy resolve(DateGranularity granularity) {
        DowngradeStrategy strategy = strategies.get(granularity);
        if (strategy == null) {
            throw new SimpleElasticsearchSearchException(
                    ErrorCode.INVALID_PARAMETER,
                    "Unsupported date granularity: " + granularity
            );
        }
        return strategy;
    }
}

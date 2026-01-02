package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DateGranularity;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel;
import lombok.Getter;

/**
 * 不支持的降级级别异常
 * 当降级级别对于给定的日期粒度不适用时抛出
 *
 * @author surezzzzzz
 */
@Getter
public class UnsupportedDowngradeLevelException extends SimpleElasticsearchSearchException {

    private final DowngradeLevel level;
    private final DateGranularity granularity;

    public UnsupportedDowngradeLevelException(String errorCode,
                                              String message,
                                              DowngradeLevel level,
                                              DateGranularity granularity) {
        super(errorCode, message);
        this.level = level;
        this.granularity = granularity;
    }

    @Override
    public String getMessage() {
        return super.getMessage() +
                " (level: " + level + ", granularity: " + granularity + ")";
    }
}

package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.DowngradeLevel;
import lombok.Getter;

/**
 * 降级失败异常
 * 当所有降级级别都失败时抛出
 *
 * @author surezzzzzz
 */
@Getter
public class DowngradeFailedException extends SimpleElasticsearchSearchException {

    private final DowngradeLevel finalLevel;

    public DowngradeFailedException(String errorCode, String message, DowngradeLevel finalLevel) {
        super(errorCode, message);
        this.finalLevel = finalLevel;
    }

    public DowngradeFailedException(String errorCode, String message, DowngradeLevel finalLevel, Throwable cause) {
        super(errorCode, message, cause);
        this.finalLevel = finalLevel;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " (final downgrade level: " + finalLevel + ")";
    }
}
